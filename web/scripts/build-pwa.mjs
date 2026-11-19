import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join, resolve } from 'node:path';

const dist = process.argv[2] ? resolve(process.argv[2]) : fileURLToPath(new URL('../dist/', import.meta.url));
const serviceWorkerSource = await readFile(new URL('./service-worker.js', import.meta.url), 'utf8');
const assets = (await readdir(join(dist, 'assets'))).sort().map((name) => `/assets/${name}`);
const shell = ['/index.html', '/manifest.webmanifest', '/voice-os.svg', '/icons/voice-os-192.png', '/icons/voice-os-512.png',
  '/icons/voice-os-maskable.png', '/icons/apple-touch-icon.png', ...assets];
const digest = createHash('sha256').update(serviceWorkerSource).update(process.env.VOICE_OS_BUILD_TAG ?? '');
for (const path of shell) digest.update(path).update(await readFile(join(dist, path.slice(1))));
const build = digest.digest('hex').slice(0, 16);
await writeFile(join(dist, 'sw.js'), `const BUILD = ${JSON.stringify(build)};\nconst SHELL = ${JSON.stringify(shell)};\n${serviceWorkerSource}`);
console.log(`VOICE OS Web shell ${build} · ${shell.length} files`);
