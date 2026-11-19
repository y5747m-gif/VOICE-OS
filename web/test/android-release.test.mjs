import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { copyFile, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createWebServer } from '../server/app.mjs';
import { ANDROID_APP_ID, parseRelease, apkName, apkPath, RELEASE_PATH } from '../src/release.mjs';

// Bytes for HTTP/contract tests only, not a signed release or a deliverable APK.
const bytes = Buffer.from(Array.from({ length: 2_048 }, (_, i) => i % 251));
const signer = 'ab'.repeat(32);
const release = {
  schema: 1, applicationId: ANDROID_APP_ID, versionCode: 8, versionName: '0.8.0', minSdk: 26,
  sizeBytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'), signingSha256: signer, downloadPath: apkPath(8),
};

async function withServer(t, run, pins = signer) {
  const directory = await mkdtemp(join(tmpdir(), 'voice-os-http-test-'));
  const server = createWebServer({ directory: join(directory, 'assets-only-web-root'), android: { directory, signer: pins } }).listen(0, '127.0.0.1');
  t.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  });
  await once(server, 'listening');
  const request = (path, init) => fetch(`http://127.0.0.1:${server.address().port}${path}`, init);
  return run(directory, request);
}

async function stage(directory, manifest = release) {
  const apk = join(directory, apkName(8));
  await writeFile(apk, bytes);
  await writeFile(join(directory, 'release.json'), JSON.stringify(manifest));
  return apk;
}

test('rejects wrong packages, redirect/traversal paths and invalid release fields', () => {
  assert.deepEqual(parseRelease(release), release);
  for (const change of [{ schema: 0 }, { applicationId: 'ai.other' }, { versionCode: 0 }, { versionCode: 2_100_000_001 },
    { versionName: '<b>Update</b>' }, { minSdk: 25 }, { sizeBytes: 1 }, { sha256: '0'.repeat(63) }, { signingSha256: null },
    { downloadPath: 'https://other.example/voice-os-8.apk' }, { downloadPath: '//other.example/voice-os-8.apk' },
    { downloadPath: '/downloads/../voice-os-8.apk' }, { downloadPath: apkPath(7) }]) {
    assert.equal(parseRelease({ ...release, ...change }), null, JSON.stringify(change));
  }
});

test('metadata and download routes are honest without a published APK', (t) => withServer(t, async (directory, request) => {
  const response = await request(RELEASE_PATH);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { schema: 1, release: null });
  await writeFile(join(directory, '.env.local'), 'NOT_A_PUBLIC_DOWNLOAD');
  for (const path of [apkPath(8), '/downloads/.env.local', '/downloads/%2e%2e/.env.local']) {
    assert.equal((await request(path)).status, 404);
  }
  assert.equal((await request(RELEASE_PATH, { method: 'POST' })).status, 405);
}));

test('verified file is streamed with metadata, HEAD and single byte ranges', (t) => withServer(t, async (directory, request) => {
  await stage(directory);
  const [metadata, response] = await Promise.all([request(RELEASE_PATH), request(apkPath(8))]);
  assert.deepEqual(await metadata.json(), { schema: 1, release });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('content-disposition'), 'attachment; filename="voice-os-8.apk"');
  assert.equal(response.headers.get('content-type'), 'application/vnd.android.package-archive');
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), bytes);
  const head = await request(apkPath(8), { method: 'HEAD' });
  assert.equal(head.headers.get('content-length'), String(bytes.length));
  assert.equal(await head.text(), '');
  assert.equal((await request(RELEASE_PATH, { method: 'HEAD' })).status, 200);
  for (const [range, start, end] of [['bytes=4-8', 4, 8], ['bytes=-3', 2045, 2047], ['bytes=2047-', 2047, 2047]]) {
    const part = await request(apkPath(8), { headers: { Range: range } });
    assert.equal(part.status, 206);
    assert.equal(part.headers.get('content-range'), `bytes ${start}-${end}/${bytes.length}`);
    assert.deepEqual(Buffer.from(await part.arrayBuffer()), bytes.subarray(start, end + 1));
  }
  for (const range of ['items=1-2', 'bytes=3-0', 'bytes=0-1,3-5', 'bytes=2048-', 'bytes=-0']) {
    const invalid = await request(apkPath(8), { headers: { Range: range } });
    assert.equal(invalid.status, 416);
    assert.equal(invalid.headers.get('content-range'), 'bytes */2048');
  }
  await copyFile(join(directory, apkName(8)), join(directory, apkName(7)));
  assert.equal((await request(apkPath(7))).status, 404, 'files not in the active release are not exposed');
}));

test('rejects metadata with a different certificate even if the APK hash matches', (t) => withServer(t, async (directory, request) => {
  await stage(directory, { ...release, signingSha256: 'ee'.repeat(32) });
  assert.equal((await request(RELEASE_PATH)).status, 503);
  assert.equal((await request(apkPath(8))).status, 503);
}));

test('revalidates modified files and rejects missing or corrupted artifacts', (t) => withServer(t, async (directory, request) => {
  const file = await stage(directory);
  assert.equal((await request(RELEASE_PATH)).status, 200);
  await writeFile(file, Buffer.alloc(bytes.length, 17));
  assert.equal((await request(RELEASE_PATH)).status, 503);
  assert.equal((await request(apkPath(8))).status, 503);
  await rm(file);
  assert.equal((await request(RELEASE_PATH)).status, 503);
  await writeFile(join(directory, 'release.json'), 'X'.repeat(8193));
  assert.equal((await request(RELEASE_PATH)).status, 503);
}));
