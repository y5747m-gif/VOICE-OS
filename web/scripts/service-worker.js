/* BUILD and SHELL are set by build-pwa.mjs from the actual built website. */
const PREFIX = 'voice-os-shell-';
const CACHE = PREFIX + BUILD;
const paths = new Set(SHELL);

self.addEventListener('install', (event) => {
  // No skipWaiting here. The old application stays usable until the new shell is fully cached and chosen.
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(SHELL.map((path) => new Request(path, { cache: 'reload', credentials: 'same-origin', redirect: 'error' })));
  })());
});

self.addEventListener('message', (event) => {
  if (event.data?.type === 'VOICE_OS_ACTIVATE_UPDATE') event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const previous = (await caches.keys()).filter((key) => key.startsWith(PREFIX) && key !== CACHE);
    // Retain at most one previous shell's hashed assets for a tab still on the previous page.
    await Promise.all(previous.slice(0, -1).map((key) => caches.delete(key)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  // Don't cache APKs, update metadata, microphone services, TTS or any other network calls.
  if (request.mode === 'navigate' && ['/', '/index.html'].includes(url.pathname)) {
    event.respondWith(caches.open(CACHE).then(async (cache) => (await cache.match('/index.html')) ?? fetch(request)));
  } else if (paths.has(url.pathname) && !url.search) {
    event.respondWith(caches.open(CACHE).then(async (cache) => (await cache.match(url.pathname)) ?? fetch(request)));
  } else if (url.pathname.startsWith('/assets/') && !url.search) {
    event.respondWith(caches.match(url.pathname).then((asset) => asset ?? fetch(request)));
  }
});
