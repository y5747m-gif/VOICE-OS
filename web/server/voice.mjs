import { VOICE, voiceLine } from '../src/domain.mjs';

const PREFIX = '/api/voice';
const SPEAK_URL = `https://api.elevenlabs.io/v1/text-to-speech/${VOICE.id}?output_format=mp3_44100_128`;
const MAX_BODY = 512;
const MAX_AUDIO = 768 * 1024;
const LIMIT_PER_MINUTE = 18;

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify(body));
}

function sameOrigin(req) {
  if (req.headers['sec-fetch-site'] === 'cross-site') return false;
  if (!req.headers.origin) return true; // local tests and same-origin non-browser clients
  try {
    const origin = new URL(req.headers.origin);
    const host = req.headers.host;
    const forwarded = typeof req.headers['x-forwarded-host'] === 'string'
      ? req.headers['x-forwarded-host'].split(',')[0].trim() : '';
    // The development preview is reverse-proxied. Don't accept arbitrary forwarded domains.
    return origin.host === host || (origin.hostname.endsWith('.e2b.app') && origin.host === forwarded);
  } catch { return false; }
}

async function readPayload(req) {
  if (!/^application\/json(?:;|$)/i.test(req.headers['content-type'] ?? '')) return null;
  if (Number(req.headers['content-length']) > MAX_BODY) return null;
  // Drain even oversized requests so we can return a structured error, without buffering them.
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size <= MAX_BODY) chunks.push(chunk);
  }
  if (size > MAX_BODY) return null;
  try {
    const data = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
    if (Object.keys(data).some((key) => !['locale', 'code', 'actionId'].includes(key))) return null;
    if (typeof data.locale !== 'string' || typeof data.code !== 'string') return null;
    if (data.code === 'opened' ? typeof data.actionId !== 'string' : data.actionId !== undefined) return null;
    return data;
  } catch { return null; }
}

async function getAudio(response) {
  if (!/^audio\//i.test(response.headers.get('content-type') ?? '') || !response.body) throw new Error('not-audio');
  if (Number(response.headers.get('content-length')) > MAX_AUDIO) {
    await response.body.cancel();
    throw new Error('too-large');
  }
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_AUDIO) { await reader.cancel(); throw new Error('too-large'); }
      chunks.push(Buffer.from(value));
    }
  } finally { reader.releaseLock(); }
  if (!size) throw new Error('empty-audio');
  return Buffer.concat(chunks, size);
}

// Shared by Vite development and the production server; no secret goes into the client bundle.
export function createVoiceApi({ apiKey = '', fetchUpstream = fetch, now = Date.now } = {}) {
  const key = apiKey.trim();
  const cache = new Map();
  const pending = new Map();
  let calls = [];

  async function synthesize(line) {
    const response = await fetchUpstream(SPEAK_URL, {
      method: 'POST',
      headers: { 'xi-api-key': key, 'Content-Type': 'application/json', Accept: 'audio/mpeg' },
      body: JSON.stringify({ text: line, model_id: VOICE.model }),
      redirect: 'error',
      signal: AbortSignal.timeout(12_000),
    });
    if (!response.ok) {
      await response.body?.cancel();
      if ([401, 403, 404].includes(response.status)) return { error: 'voice_access', status: 502 };
      if (response.status === 429) return { error: 'rate_limit', status: 429 };
      return { error: 'provider_error', status: 502 };
    }
    return { audio: await getAudio(response) };
  }

  return async function voiceApi(req, res, next) {
    const path = (req.url ?? '').split('?', 1)[0];
    if (path !== PREFIX && !path.startsWith(`${PREFIX}/`)) { next(); return; }
    if (req.method === 'GET' && path === `${PREFIX}/status`) {
      json(res, 200, { configured: Boolean(key), voiceId: VOICE.id });
      return;
    }
    if (path !== `${PREFIX}/speak`) { json(res, 404, { error: 'not_found' }); return; }
    if (req.method !== 'POST') { json(res, 405, { error: 'method' }); return; }
    if (!sameOrigin(req)) { json(res, 403, { error: 'origin' }); return; }
    try {
      const data = await readPayload(req);
      const line = data && voiceLine(data);
      if (!line) { json(res, 400, { error: 'invalid_message' }); return; }
      if (!key) { json(res, 503, { error: 'not_configured' }); return; }

      const cached = cache.get(line);
      let result = cached && cached.expires > now() ? { audio: cached.audio } : null;
      if (!result) {
        if (!pending.has(line)) {
          calls = calls.filter((time) => time > now() - 60_000);
          if (calls.length >= LIMIT_PER_MINUTE) { json(res, 429, { error: 'rate_limit' }); return; }
          calls.push(now());
          pending.set(line, synthesize(line)
            .then((answer) => {
              if (answer.audio) cache.set(line, { audio: answer.audio, expires: now() + 60 * 60 * 1000 });
              return answer;
            })
            .finally(() => pending.delete(line)));
        }
        result = await pending.get(line);
      }
      if (result.error) { json(res, result.status, { error: result.error }); return; }
      res.writeHead(200, {
        'Content-Type': 'audio/mpeg', 'Content-Length': result.audio.length,
        'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      });
      res.end(result.audio);
    } catch {
      if (!res.headersSent && !res.destroyed) json(res, 502, { error: 'provider_error' });
    }
  };
}
