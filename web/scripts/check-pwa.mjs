import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { cp, mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { createWebServer } from '../server/app.mjs';
import { apkName, apkPath, ANDROID_APP_ID } from '../src/release.mjs';

// Test the production server and real Chromium workers, without changing the running preview.
const tmp = await mkdtemp(join(tmpdir(), 'voice-os-pwa-test-'));
let browser;
let server;
try {
  const web = join(tmp, 'site');
  const releases = join(tmp, 'releases');
  await cp(new URL('../dist/', import.meta.url), web, { recursive: true });
  await mkdir(releases);
  const signer = 'ab'.repeat(32); // Test fixture, not a certificate for distribution.
  server = createWebServer({ directory: web, android: { directory: releases, signer } }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}/`;
  browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH, headless: true });
  const context = await browser.newContext({ viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true });
  const page = await context.newPage();
  page.setDefaultTimeout(10_000);
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(base);
  await page.waitForFunction(() => Boolean(navigator.serviceWorker.controller));

  const manifest = await context.request.get(`${base}manifest.webmanifest`);
  assert.match(manifest.headers()['content-type'], /^application\/manifest\+json/);
  const pwa = await manifest.json();
  assert.equal(pwa.scope, '/');
  assert.equal(pwa.start_url, '/');
  assert.equal(pwa.display, 'standalone');
  assert.deepEqual(pwa.icons.map((entry) => entry.sizes), ['192x192', '512x512', '512x512']);
  for (const icon of [...pwa.icons, { src: '/icons/apple-touch-icon.png', sizes: '180x180' }]) {
    const png = await context.request.get(new URL(icon.src, base).href);
    assert.equal(png.status(), 200);
    assert.equal(png.headers()['content-type'], 'image/png');
    const body = await png.body();
    assert.equal(`${body.readUInt32BE(16)}x${body.readUInt32BE(20)}`, icon.sizes);
  }
  const cdp = await context.newCDPSession(page);
  assert.deepEqual((await cdp.send('Page.getInstallabilityErrors')).installabilityErrors, []);
  console.log('Chromium considers the production PWA installable; manifest and icon sizes match.');

  const dialog = page.getByRole('dialog');
  await page.getByRole('button', { name: 'ثبّت التطبيق', exact: true }).click();
  await dialog.getByText(/لسه مفيش APK موقّع/).waitFor();
  assert.equal(await dialog.locator('a[download]').count(), 0);
  await dialog.getByText(/iPhone.*Safari/).waitFor();
  await page.keyboard.press('Escape');
  await page.setViewportSize({ width: 320, height: 714 });
  async function layoutFits() {
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 320);
    const title = await page.locator('h1').boundingBox();
    const pill = await page.locator('.install-pill').boundingBox();
    assert.ok(title.y >= pill.y + pill.height, 'The mobile install button must not overlap the title');
  }
  await layoutFits();
  await page.getByRole('button', { name: 'Switch to English' }).click();
  await layoutFits();
  await page.setViewportSize({ width: 375, height: 812 });
  await page.getByRole('button', { name: 'Install app', exact: true }).click();
  await dialog.getByText(/No signed APK is published/).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 375);
  const bounds = await dialog.boundingBox();
  assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 375);
  await page.keyboard.press('Escape');

  // Only a temporary server fixture, to exercise the download UI/transport.
  const bytes = Buffer.alloc(4_096, 67);
  const apk = apkName(42);
  await writeFile(join(releases, apk), bytes);
  await writeFile(join(releases, 'release.json'), JSON.stringify({ schema: 1, applicationId: ANDROID_APP_ID,
    versionCode: 42, versionName: '0.0.0-fixture', minSdk: 26, sizeBytes: bytes.length,
    signingSha256: signer, sha256: createHash('sha256').update(bytes).digest('hex'), downloadPath: apkPath(42) }));
  await page.getByRole('button', { name: 'Install app', exact: true }).click();
  const download = page.waitForEvent('download');
  await dialog.getByRole('link', { name: 'Download APK', exact: true }).click();
  const file = await download;
  assert.equal(file.suggestedFilename(), apk);
  assert.deepEqual(await readFile(await file.path()), bytes);
  await page.keyboard.press('Escape');

  const originalBundle = await readFile(join(web, 'index.html'), 'utf8');
  const originalWorker = await readFile(join(web, 'sw.js'), 'utf8');
  await context.setOffline(true);
  await page.reload();
  await page.getByRole('button', { name: 'Install app', exact: true }).click();
  await dialog.getByText(/Couldn’t read the published release/).waitFor();
  assert.equal(await dialog.locator('a[download]').count(), 0);
  await dialog.getByRole('button', { name: 'Check for updates', exact: true }).click();
  await dialog.getByText(/Couldn’t check for updates/).waitFor();
  await page.keyboard.press('Escape');
  const command = page.getByRole('textbox', { name: 'Write a command' });
  await command.fill('open YouTube');
  await command.press('Enter');
  await page.getByRole('button', { name: 'Open website', exact: true }).waitFor();
  console.log('Offline shell and typed plan still work. API metadata and APK links are not cached.');

  await context.setOffline(false);
  const oldAsset = /src="(\/assets\/index-[^"]+\.js)"/.exec(originalBundle)[1];
  const newAsset = oldAsset.replace('.js', '-integration-test.js');
  // A new release changes both the main HTML and a hashed asset path.
  await rename(join(web, oldAsset), join(web, newAsset));
  await writeFile(join(web, 'index.html'), originalBundle.replace(oldAsset, newAsset).replace('<html ', '<html data-test-shell="next" '));
  execFileSync(process.execPath, [fileURLToPath(new URL('./build-pwa.mjs', import.meta.url)), web], { stdio: 'inherit' });
  assert.notEqual(await readFile(join(web, 'sw.js'), 'utf8'), originalWorker);
  await page.getByRole('button', { name: 'Install app', exact: true }).click();
  await dialog.getByRole('button', { name: 'Check for updates', exact: true }).click();
  const apply = dialog.getByRole('button', { name: 'Update and reopen', exact: true });
  await apply.waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.dataset.testShell), undefined);
  assert.equal(await page.evaluate(async () => Boolean((await navigator.serviceWorker.getRegistration()).waiting)), true);
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Open website', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Update ready', exact: true }).click();
  await apply.click();
  await page.waitForFunction(() => document.documentElement.dataset.testShell === 'next');
  await page.getByRole('button', { name: 'Install app', exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Open website', exact: true }).count(), 0);
  await context.setOffline(true);
  await page.reload();
  assert.equal(await page.evaluate(() => document.documentElement.dataset.testShell), 'next');
  await page.getByRole('button', { name: 'Install app', exact: true }).waitFor();
  assert.deepEqual(errors, []);
  console.log('A second production shell waited without clearing the live session, applied on click, and reopened offline. No page errors.');
} finally {
  await browser?.close();
  if (server) await new Promise((resolve) => server.close(resolve));
  await rm(tmp, { recursive: true, force: true });
}
