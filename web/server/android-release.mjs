import { createHash } from 'node:crypto';
import { open } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { apkName, parseRelease, RELEASE_PATH } from '../src/release.mjs';

const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const MAX_MANIFEST = 8192;
function json(req, res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, { ...headers, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  res.end(req.method === 'HEAD' ? undefined : body);
}

export function byteRange(header, size) {
  if (header === undefined) return null;
  const parts = typeof header === 'string' && /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!parts || (!parts[1] && !parts[2])) return false;
  const first = parts[1] ? Number(parts[1]) : null;
  const last = parts[2] ? Number(parts[2]) : null;
  if ((first !== null && !Number.isSafeInteger(first)) || (last !== null && !Number.isSafeInteger(last))) return false;
  if (first === null) return last > 0 ? { start: Math.max(0, size - last), end: size - 1 } : false;
  const end = Math.min(last ?? size - 1, size - 1);
  return first <= end ? { start: first, end } : false;
}

// Configured by the deployer with files from the signed-release workflow, never with user uploads.
export function createAndroidReleaseApi({ directory = '', signer = '' } = {}) {
  const folder = directory.trim() ? resolve(directory) : null;
  const expectedSigner = signer.trim().toLowerCase();
  let verified = null;
  let inProgress = null;

  async function loadMetadata() {
    if (!folder) return null;
    let file;
    try { file = await open(resolve(folder, 'release.json'), 'r'); }
    catch (error) { if (error?.code === 'ENOENT') return null; throw error; }
    try {
      const info = await file.stat();
      if (!info.isFile() || info.size > MAX_MANIFEST) throw Error('invalid_manifest');
      const buffer = Buffer.alloc(MAX_MANIFEST + 1);
      const { bytesRead } = await file.read(buffer, 0, buffer.length, 0);
      if (bytesRead > MAX_MANIFEST || bytesRead !== info.size) throw Error('invalid_manifest');
      const release = parseRelease(JSON.parse(buffer.toString('utf8', 0, bytesRead)));
      if (!release || release.signingSha256 !== expectedSigner) throw Error('invalid_manifest');
      return release;
    } finally { await file.close(); }
  }

  async function verify(file, key, release) {
    if (verified === key) return;
    if (inProgress?.key === key) { await inProgress.task; return; }
    const task = (async () => {
      const hash = createHash('sha256');
      for await (const part of file.createReadStream({ autoClose: false, start: 0 })) hash.update(part);
      if (hash.digest('hex') !== release.sha256) throw Error('apk_hash_mismatch');
      verified = key;
    })();
    inProgress = { key, task };
    try { await task; } finally { if (inProgress?.task === task) inProgress = null; }
  }

  return async function androidReleaseApi(req, res, next) {
    const path = (req.url ?? '').split('?', 1)[0];
    const metadata = path === RELEASE_PATH;
    const download = path.startsWith('/downloads/');
    if (!metadata && !download) { next(); return; }
    if (!['GET', 'HEAD'].includes(req.method)) { json(req, res, 405, { error: 'method' }); return; }
    let file;
    try {
      const release = await loadMetadata();
      if (!release) { json(req, res, metadata ? 200 : 404, metadata ? { schema: 1, release: null } : { error: 'not_found' }); return; }
      if (download && path !== release.downloadPath) { json(req, res, 404, { error: 'not_found' }); return; }
      file = await open(resolve(folder, apkName(release.versionCode)), 'r');
      const info = await file.stat();
      if (!info.isFile() || info.size !== release.sizeBytes) throw Error('invalid_apk');
      const key = `${info.dev}/${info.ino}/${info.mtimeMs}/${info.ctimeMs}/${info.size}/${release.sha256}`;
      await verify(file, key, release);
      if (metadata) { json(req, res, 200, { schema: 1, release }); return; }

      const partial = byteRange(req.headers.range, info.size);
      if (partial === false) { res.writeHead(416, { ...headers, 'Content-Range': `bytes */${info.size}` }).end(); return; }
      const start = partial?.start ?? 0;
      const end = partial?.end ?? info.size - 1;
      res.writeHead(partial ? 206 : 200, {
        ...headers, 'Content-Type': 'application/vnd.android.package-archive', 'Content-Length': end - start + 1,
        'Content-Disposition': `attachment; filename="${apkName(release.versionCode)}"`, 'Accept-Ranges': 'bytes',
        ...(partial ? { 'Content-Range': `bytes ${start}-${end}/${info.size}` } : {}),
      });
      if (req.method === 'HEAD') { res.end(); return; }
      await pipeline(file.createReadStream({ autoClose: false, start, end }), res);
    } catch {
      if (!res.headersSent && !res.destroyed) json(req, res, 503, { error: 'invalid_release' });
      else if (!res.destroyed) res.destroy();
    } finally { if (file) await file.close().catch(() => {}); }
  };
}
