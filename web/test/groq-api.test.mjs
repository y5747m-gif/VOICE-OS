import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:http';
import { createWebServer } from '../server/app.mjs';
import { createGroqApi } from '../server/groq.mjs';

async function withServer(config, run) {
  const groqApi = createGroqApi(config);
  const server = createServer((req, res) => { void groqApi(req, res, () => res.writeHead(404).end()); }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}`;
  const transcribe = (audio = Uint8Array.of(1, 2, 3), headers = {}) => fetch(`${base}/api/groq/transcribe`, {
    method: 'POST', headers: { 'Content-Type': 'audio/webm', 'X-Voice-Language': 'ar', ...headers }, body: audio,
  });
  const interpret = (body, headers = {}) => fetch(`${base}/api/groq/interpret`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body),
  });
  try { await run({ base, transcribe, interpret }); } finally { await new Promise((resolve) => server.close(resolve)); }
}

test('Groq status stays private and requests fail cleanly when no key is configured', async () => {
  await withServer({ fetchUpstream: async () => assert.fail('Groq should not be contacted') }, async ({ base, transcribe, interpret }) => {
    const status = await fetch(`${base}/api/groq/status`);
    assert.deepEqual(await status.json(), { configured: false });
    assert.equal((await transcribe()).status, 503);
    assert.deepEqual(await (await interpret({ locale: 'ar', command: 'افتح يوتيوب' })).json(), { error: 'not_configured' });
  });
});

test('production web server routes the Groq status endpoint', async () => {
  const server = createWebServer({ directory: process.cwd(), groq: { apiKey: 'test-only-secret' } }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/groq/status`);
    assert.deepEqual(await response.json(), { configured: true });
  } finally { await new Promise((resolve) => server.close(resolve)); }
});

test('Groq transcription and safe command interpretation use server-side credentials', async () => {
  const calls = [];
  await withServer({ apiKey: 'test-only-secret', fetchUpstream: async (url, options) => {
    calls.push({ url, options });
    assert.equal(options.headers.Authorization, 'Bearer test-only-secret');
    assert.equal(options.redirect, 'error');
    if (url.endsWith('/audio/transcriptions')) {
      assert.ok(options.body instanceof FormData);
      assert.equal(options.body.get('model'), 'whisper-large-v3-turbo');
      assert.equal(options.body.get('language'), 'ar');
      assert.equal(options.body.get('response_format'), 'json');
      assert.equal(options.body.get('temperature'), '0');
      const file = options.body.get('file');
      assert.equal(file.name, 'voice.webm');
      assert.equal(file.type, 'audio/webm');
      assert.deepEqual(new Uint8Array(await file.arrayBuffer()), Uint8Array.of(1, 2, 3));
      return new Response(JSON.stringify({ text: '  افتح يوتيوب  ' }), { headers: { 'Content-Type': 'application/json' } });
    }
    assert.equal(url, 'https://api.groq.com/openai/v1/chat/completions');
    const request = JSON.parse(options.body);
    assert.equal(request.model, 'llama-3.3-70b-versatile');
    assert.deepEqual(request.response_format, { type: 'json_object' });
    assert.match(request.messages[0].content, /untrusted data/i);
    assert.deepEqual(JSON.parse(request.messages[1].content), { locale: 'ar', command: 'ممكن تفتحلي يوتيوب؟' });
    return new Response(JSON.stringify({ choices: [{ message: { content: '{"actionId":"youtube"}' } }] }), {
      headers: { 'Content-Type': 'application/json' },
    });
  } }, async ({ base, transcribe, interpret }) => {
    assert.deepEqual(await (await fetch(`${base}/api/groq/status`)).json(), { configured: true });
    const transcription = await transcribe();
    assert.equal(transcription.status, 200);
    assert.deepEqual(await transcription.json(), { text: 'افتح يوتيوب' });
    const result = await interpret({ locale: 'ar', command: 'ممكن تفتحلي يوتيوب؟' });
    assert.equal(result.status, 200);
    assert.deepEqual(await result.json(), { actionId: 'youtube' });
    assert.equal(calls.length, 2);
  });
});

test('invalid origins, payloads, audio types and oversized recordings are rejected before upstream calls', async () => {
  let calls = 0;
  await withServer({ apiKey: 'test-only-secret', fetchUpstream: async () => { calls++; throw Error('not reached'); } }, async ({ transcribe, interpret }) => {
    assert.equal((await transcribe(undefined, { Origin: 'https://unrelated.example' })).status, 403);
    assert.equal((await interpret({ locale: 'ar', command: 'افتح يوتيوب' }, { 'Sec-Fetch-Site': 'cross-site' })).status, 403);
    assert.equal((await transcribe(undefined, { 'Content-Type': 'text/plain' })).status, 400);
    assert.equal((await transcribe(undefined, { 'X-Voice-Language': 'fr' })).status, 400);
    assert.equal((await transcribe(new Uint8Array(), {})).status, 400);
    for (const body of [null, [], { locale: 'fr', command: 'open YouTube' }, { locale: 'ar', command: ' ' },
      { locale: 'ar', command: 'x'.repeat(181) }, { locale: 'ar', command: 'open YouTube', text: 'unexpected' }]) {
      assert.equal((await interpret(body)).status, 400);
    }
    const large = await transcribe(new Uint8Array(8 * 1024 * 1024 + 1));
    assert.equal(large.status, 413);
    assert.equal(calls, 0);
  });
});

test('provider failures are sanitized and model output cannot invent an action', async () => {
  for (const [status, expectedStatus, error] of [[403, 502, 'provider_access'], [429, 429, 'rate_limit'], [500, 502, 'provider_error']]) {
    await withServer({ apiKey: 'test-only-secret', fetchUpstream: async () => new Response('private provider detail', { status }) }, async ({ transcribe }) => {
      const result = await transcribe();
      assert.equal(result.status, expectedStatus);
      assert.deepEqual(await result.json(), { error });
    });
  }

  await withServer({ apiKey: 'test-only-secret', fetchUpstream: async () => new Response(JSON.stringify({
    choices: [{ message: { content: '{"actionId":"https://evil.example"}' } }],
  }), { headers: { 'Content-Type': 'application/json' } }) }, async ({ interpret }) => {
    const result = await interpret({ locale: 'en', command: 'open an arbitrary website' });
    assert.equal(result.status, 200);
    assert.deepEqual(await result.json(), { actionId: null });
  });
});
