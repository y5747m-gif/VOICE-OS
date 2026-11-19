import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:http';
import { createVoiceApi } from '../server/voice.mjs';
import { VOICE, voiceLine } from '../src/domain.mjs';

async function withServer(config, run) {
  const voiceApi = createVoiceApi(config);
  const server = createServer((req, res) => { void voiceApi(req, res, () => res.writeHead(404).end()); }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}`;
  const speak = (body, extra = {}) => fetch(`${base}/api/voice/speak`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', ...extra }, body: JSON.stringify(body) });
  try { await run(base, speak); } finally { await new Promise((resolve) => server.close(resolve)); }
}

const sample = { locale: 'ar', code: 'sample' };

test('missing server key is truthful and makes no provider request', async () => {
  await withServer({ fetchUpstream: async () => assert.fail('provider should not be contacted') }, async (base, speak) => {
    const status = await fetch(`${base}/api/voice/status`);
    assert.deepEqual(await status.json(), { configured: false, voiceId: VOICE.id });
    const result = await speak(sample);
    assert.equal(result.status, 503);
    assert.deepEqual(await result.json(), { error: 'not_configured' });
  });
});

test('synthesis uses the linked voice ID and model with a private key and caches the allowed reply', async () => {
  const audio = Uint8Array.of(73, 68, 51); // Test fixture, not a clip shipped to the UI.
  let calls = 0;
  await withServer({ apiKey: 'test-only-key', fetchUpstream: async (url, options) => {
    calls++;
    assert.equal(url, 'https://api.elevenlabs.io/v1/text-to-speech/ML7jGRDg4E9hIl5qEm1Z?output_format=mp3_44100_128');
    assert.equal(options.method, 'POST');
    assert.equal(options.headers['xi-api-key'], 'test-only-key');
    assert.equal(options.redirect, 'error');
    assert.deepEqual(JSON.parse(options.body), { text: voiceLine(sample), model_id: 'eleven_multilingual_v2' });
    return new Response(audio, { headers: { 'Content-Type': 'audio/mpeg' } });
  } }, async (base, speak) => {
    assert.deepEqual(await (await fetch(`${base}/api/voice/status`)).json(), { configured: true, voiceId: VOICE.id });
    const results = await Promise.all([speak(sample), speak(sample)]);
    for (const result of [...results, await speak(sample)]) {
      assert.equal(result.status, 200);
      assert.equal(result.headers.get('content-type'), 'audio/mpeg');
      assert.deepEqual(new Uint8Array(await result.arrayBuffer()), audio);
    }
    assert.equal(calls, 1);
  });
});

test('synthesis rejects cross-origin requests, private raw text and unsupported values', async () => {
  let calls = 0;
  await withServer({ apiKey: 'test-only-key', fetchUpstream: async () => { calls++; throw Error('Not reached'); } }, async (base, speak) => {
    assert.equal((await speak(sample, { Origin: 'https://unrelated.example' })).status, 403);
    assert.equal((await speak(sample, { 'Sec-Fetch-Site': 'cross-site' })).status, 403);
    for (const invalid of [null, [], { ...sample, text: 'A private command' }, { ...sample, url: 'https://example.org' },
      { ...sample, locale: 'fr' }, { ...sample, code: 'user transcript' }, { locale: 'en', code: 'opened', actionId: '__proto__' }, { ...sample, code: 'x'.repeat(600) }]) {
      const result = await speak(invalid);
      assert.equal(result.status, 400);
      assert.deepEqual(await result.json(), { error: 'invalid_message' });
    }
    assert.equal(calls, 0);
    assert.equal((await fetch(`${base}/api/voice/speak`)).status, 405);
  });
});

test('vendor access and network failures do not expose upstream details', async () => {
  for (const [status, code, out] of [[403, 'voice_access', 502], [429, 'rate_limit', 429], [200, 'provider_error', 502]]) {
    await withServer({ apiKey: 'test-only-key', fetchUpstream: async () => new Response('private upstream data', { status }) }, async (_, speak) => {
      const result = await speak(sample);
      assert.equal(result.status, out);
      assert.deepEqual(await result.json(), { error: code });
    });
  }
});
