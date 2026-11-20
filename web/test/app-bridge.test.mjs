import test from 'node:test';
import assert from 'node:assert/strict';
import { deepLinkFor, isAndroidDevice, parseDeepLink, planAutoLaunch } from '../src/app-bridge.mjs';

test('deep links use the voiceos scheme and round-trip the catalogue action', () => {
  assert.equal(deepLinkFor('youtube'), 'voiceos://open?action=youtube');
  assert.equal(parseDeepLink(deepLinkFor('whatsapp')), 'whatsapp');
  assert.equal(parseDeepLink('voiceos://open?action='), null);
  assert.equal(parseDeepLink('https://voiceos.example/open?action=youtube'), null);
  assert.equal(parseDeepLink('voiceos://other?action=youtube'), null);
  assert.equal(deepLinkFor(''), 'voiceos://open?action=');
});

test('Android user agents are detected without false positives', () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const cases = [
    ['Mozilla/5.0 (Linux; Android 14) Chrome/120', true],
    ['Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)', false],
    ['Mozilla/5.0 (Windows NT 10.0)', false],
  ];
  for (const [userAgent, expected] of cases) {
    Object.defineProperty(globalThis, 'navigator', { value: { userAgent }, configurable: true, writable: true });
    assert.equal(isAndroidDevice(), expected, userAgent);
  }
  if (descriptor) Object.defineProperty(globalThis, 'navigator', descriptor);
});

test('automatic launch routes Android to the app or the download location', () => {
  assert.equal(planAutoLaunch({ android: true, appInstalled: 'installed' }), 'handoff');
  assert.equal(planAutoLaunch({ android: true, appInstalled: 'missing' }), 'download');
  assert.equal(planAutoLaunch({ android: true, appInstalled: 'unknown' }), 'download');
  assert.equal(planAutoLaunch({ android: false, appInstalled: 'installed' }), 'openWebsite');
  assert.equal(planAutoLaunch({ android: false, appInstalled: 'missing' }), 'openWebsite');
});
