// Native app bridge for VOICE OS Web.
//
// The web edition keeps opening official sites, and on Android it also detects the installed
// VOICE OS app and sends commands straight to it. When the app is not installed, the user is
// taken directly to the download location instead of being left on a dead end.

export const NATIVE_APP_ID = 'ai.voiceos';
export const DEEP_LINK_HOST = 'open';

/** One canonical deep link per catalogue action, e.g. voiceos://open?action=youtube */
export function deepLinkFor(actionId) {
  return `voiceos://${DEEP_LINK_HOST}?action=${encodeURIComponent(actionId ?? '')}`;
}

/** Reverse of deepLinkFor, used by tests and defensive parsing on the web side. */
export function parseDeepLink(raw) {
  try {
    const url = new URL(raw);
    if (url.protocol !== 'voiceos:' || url.hostname !== DEEP_LINK_HOST) return null;
    const action = url.searchParams.get('action') ?? '';
    return action || null;
  } catch {
    return null;
  }
}

export function isAndroidDevice() {
  return typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent || '');
}

/**
 * Decides what an automatically launched command should do.
 * Android with the app installed hands off to the app; Android without it goes to the
 * download location; every other device opens the website.
 */
export function planAutoLaunch({ android, appInstalled }) {
  if (!android) return 'openWebsite';
  return appInstalled === 'installed' ? 'handoff' : 'download';
}

let probeState = 'unknown';
let probePromise = null;

/**
 * Probes for the installed native app. Custom schemes can't be fetched, so the page watches
 * for the app taking over the browser (visibility/focus) and falls back to "missing" after a
 * short timeout. The result is cached for the session.
 */
export function detectNativeApp(force = false) {
  if (!isAndroidDevice()) return Promise.resolve('unknown');
  if (!force && probeState !== 'unknown') return Promise.resolve(probeState);
  if (probePromise) return probePromise;
  if (typeof document === 'undefined' || !document.body) return Promise.resolve('missing');

  probePromise = new Promise((resolve) => {
    const iframe = document.createElement('iframe');
    let settled = false;
    const timer = setTimeout(() => finish('missing'), 1500);

    const onVisibility = () => { if (document.visibilityState === 'hidden') finish('installed'); };
    const onFocus = () => finish('installed');

    function finish(state) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('focus', onFocus);
      iframe.remove();
      probeState = state;
      probePromise = null;
      resolve(state);
    }

    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.display = 'none';
    // The native app answers voiceos://open/ping by taking over the browser; an unhandled
    // scheme never loads, so the timeout below settles the probe as "missing".
    iframe.src = `voiceos://${DEEP_LINK_HOST}/ping`;
    iframe.onload = () => { /* custom schemes never complete a load; fall through to timeout */ };
    iframe.onerror = () => finish('missing');
    document.body.appendChild(iframe);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('focus', onFocus);
  });
  return probePromise;
}

/**
 * Opens a destination website. A user-gesture click gets a new tab; a voice-triggered or
 * typed command (no gesture) falls back to navigating the current tab, which browsers allow.
 */
export function openWebsite(url) {
  try {
    const tab = window.open('about:blank', '_blank');
    if (tab && !tab.closed) {
      tab.opener = null;
      tab.location.replace(url);
      return 'opened';
    }
  } catch { /* popups were blocked or the tab closed immediately */ }
  try {
    window.location.assign(url);
  } catch { /* navigation can be blocked in rare embedded contexts */ }
  return 'opened-tab';
}
