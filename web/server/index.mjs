import { stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createWebServer } from './app.mjs';

const directory = fileURLToPath(new URL('../dist', import.meta.url));
try {
  await Promise.all(['index.html', 'sw.js'].map((name) => stat(new URL(`../dist/${name}`, import.meta.url))));
} catch { console.error('Run npm run build before npm start.'); process.exit(1); }
const port = Number(process.env.PORT || 4173);
createWebServer({ directory, voice: { apiKey: process.env.ELEVENLABS_API_KEY }, android: {
  directory: process.env.ANDROID_RELEASE_DIR, signer: process.env.ANDROID_SIGNER_SHA256,
} }).listen(port, '0.0.0.0', () => console.log(`VOICE OS Web listening on port ${port}`));
