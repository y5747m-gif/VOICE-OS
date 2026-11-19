import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { resolve, sep, extname } from 'node:path';
import { createVoiceApi } from './voice.mjs';
import { createGroqApi } from './groq.mjs';
import { createAndroidReleaseApi } from './android-release.mjs';

const types = { '.webmanifest': 'application/manifest+json; charset=utf-8', '.png': 'image/png', '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg', '.woff2': 'font/woff2', '.woff': 'font/woff' };

export function createWebServer({ directory, voice, groq, android }) {
  const root = resolve(directory);
  const index = resolve(root, 'index.html');
  const voiceApi = createVoiceApi(voice);
  const groqApi = createGroqApi(groq);
  const androidApi = createAndroidReleaseApi(android);

  async function serveFile(req, res) {
    if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
    let path;
    try { path = decodeURIComponent(new URL(req.url, 'http://internal').pathname); }
    catch { res.writeHead(400).end(); return; }
    const file = path === '/' ? index : resolve(root, `.${path}`);
    if (!file.startsWith(`${root}${sep}`)) { res.writeHead(404).end(); return; }
    try {
      const info = await stat(file);
      if (!info.isFile()) { res.writeHead(404).end(); return; }
      res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream',
        'Content-Length': info.size, 'X-Content-Type-Options': 'nosniff',
        'Cache-Control': path.startsWith('/assets/') ? 'public,max-age=31536000,immutable' : 'no-cache',
        'Referrer-Policy': 'no-referrer', 'Permissions-Policy': 'microphone=(self)',
        'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src 'self' blob:; object-src 'none'; base-uri 'none'" });
      if (req.method === 'HEAD') { res.end(); return; }
      createReadStream(file).on('error', () => res.destroy()).pipe(res);
    } catch { res.writeHead(404).end(); }
  }

  return createServer({ requestTimeout: 20_000 }, (req, res) => {
    void groqApi(req, res, () => { void voiceApi(req, res, () => { void androidApi(req, res, () => { void serveFile(req, res); }); }); });
  });
}
