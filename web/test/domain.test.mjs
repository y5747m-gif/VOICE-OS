import test from 'node:test';
import assert from 'node:assert/strict';
import { ACTIONS, COMMAND_LIMIT, interpretCommand, voiceLine } from '../src/domain.mjs';

test('each advertised Egyptian Arabic and English phrase maps to a fixed HTTPS destination', () => {
  for (const action of ACTIONS) {
    assert.equal(new URL(action.url).protocol, 'https:');
    for (const phrase of Object.values(action.phrase)) {
      assert.deepEqual(interpretCommand(phrase), { code: 'prepared', actionId: action.id });
    }
  }
  assert.deepEqual(interpretCommand('مُمْكِن تفتحلي اليوتيوب؟'), { code: 'prepared', actionId: 'youtube' });
  assert.deepEqual(interpretCommand(' Open WhatsApp, please! '), { code: 'prepared', actionId: 'whatsapp' });
});

test('compound, sensitive, oversized and arbitrary-URL requests are not prepared', () => {
  for (const command of ['افتح واتساب وابعت لمحمد', 'لا تفتح يوتيوب', 'turn off wifi', 'open javascript:alert(1)', 'open https://example.org']) {
    assert.deepEqual(interpretCommand(command), { code: 'unsupported' }, command);
  }
  assert.deepEqual(interpretCommand('افتح واتساب وبعدين افتح يوتيوب'), { code: 'compound' });
  assert.deepEqual(interpretCommand('a'.repeat(COMMAND_LIMIT + 1)), { code: 'tooLong' });
  assert.deepEqual(interpretCommand(' '), { code: 'empty' });
});

test('voice responses acknowledge a browser request, not completion inside the destination', () => {
  assert.equal(voiceLine({ locale: 'en', code: 'opened', actionId: 'youtube' }), 'I asked the browser to open YouTube in a new tab.');
  assert.equal(voiceLine({ locale: 'en', code: 'opened', actionId: 'not-in-the-catalogue' }), null);
  assert.equal(voiceLine({ locale: 'fr', code: 'sample' }), null);
  assert.equal(voiceLine({ locale: 'ar', code: 'prepared' }), null);
});

test('handoff, download and same-tab replies describe the automatic launch', () => {
  assert.equal(voiceLine({ locale: 'ar', code: 'handoff', actionId: 'youtube' }), 'طلبت من تطبيق VOICE OS يفتح يوتيوب على طول.');
  assert.equal(voiceLine({ locale: 'en', code: 'opened-tab', actionId: 'maps' }), 'I asked the browser to open Google Maps in this tab.');
  assert.equal(voiceLine({ locale: 'en', code: 'handoff', actionId: 'not-in-the-catalogue' }), null);
  assert.match(voiceLine({ locale: 'ar', code: 'download' }), /مكان تحميله/);
  assert.match(voiceLine({ locale: 'en', code: 'download' }), /download page/);
});
