// Run on a trusted release/deployment host with Android SDK Build-Tools 35+.
// Private signing keys are never read by the web server or this publisher.
import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { constants, createReadStream } from 'node:fs';
import { copyFile, link, mkdir, mkdtemp, open, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { promisify } from 'node:util';
import { ANDROID_APP_ID, APK_MAX_SIZE, apkName, apkPath, parseRelease } from '../src/release.mjs';

const exec = promisify(execFile);

async function sdkTool(bin, ...args) {
  try { return (await exec(bin, args, { timeout: 90_000, maxBuffer: 1024 * 1024 })).stdout; }
  catch (error) { throw Error(`${bin} failed: ${String(error.stderr || error.message).trim().slice(0, 1200)}`); }
}

async function inspect(apk, tools, signer) {
  const info = await stat(apk);
  if (!info.isFile() || info.size < 1024 || info.size > APK_MAX_SIZE) throw Error('APK file is missing or outside the release size limit');
  const signing = await sdkTool(join(tools, 'apksigner'), 'verify', '--verbose', '--print-certs', apk);
  const cert = [...signing.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})\r?$/gmi)].map((entry) => entry[1].toLowerCase());
  if (cert.length !== 1 || cert[0] !== signer || !/^Verified using v[23] scheme .*: true\r?$/m.test(signing)) {
    throw Error('APK must use a modern signature from the single configured release certificate');
  }
  const badging = await sdkTool(join(tools, 'aapt2'), 'dump', 'badging', apk);
  const identity = /^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'[^\r\n]*/m.exec(badging);
  const minSdk = Number(/^minSdkVersion:'(\d+)'/m.exec(badging)?.[1]);
  if (identity?.[1] !== ANDROID_APP_ID || /^application-debuggable\b/m.test(badging) || /\bsplit=/.test(identity[0])) {
    throw Error(`Expected a standalone, non-debug ${ANDROID_APP_ID} release APK`);
  }
  const manifest = await sdkTool(join(tools, 'aapt2'), 'dump', 'xmltree', apk, '--file', 'AndroidManifest.xml');
  const releaseFlags = /^\s*A: \S*\/res\/android:(?:testOnly|debuggable|versionCodeMajor)\([^)]*\)=(.*)$/gm;
  if ([...manifest.matchAll(releaseFlags)].some((match) => !/^(?:false|0|0x0)\s*$/.test(match[1].trim()))) {
    throw Error('Cannot publish a test-only, debuggable or extended-versionCode APK');
  }
  const sha = createHash('sha256');
  for await (const chunk of createReadStream(apk)) sha.update(chunk);
  const versionCode = Number(identity[2]);
  const release = parseRelease({ schema: 1, applicationId: ANDROID_APP_ID, versionCode, versionName: identity[3], minSdk,
    sizeBytes: info.size, sha256: sha.digest('hex'), signingSha256: signer, downloadPath: apkPath(versionCode) });
  if (!release) throw Error('APK metadata does not match the release contract');
  return release;
}

async function publish(input, directory, tools, signer) {
  const source = await stat(input);
  if (!source.isFile() || source.size < 1024 || source.size > APK_MAX_SIZE) throw Error('Expected a signed APK file within the release size limit');
  await mkdir(directory, { recursive: true });
  const lockPath = join(directory, '.publish.lock');
  const lock = await open(lockPath, 'wx');
  let staging;
  let added;
  try {
    const metadataPath = join(directory, 'release.json');
    let previous = null;
    try {
      previous = parseRelease(JSON.parse(await readFile(metadataPath, 'utf8')));
      if (!previous || previous.signingSha256 !== signer) throw Error('Previous release metadata or signing certificate does not match');
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
    staging = await mkdtemp(join(directory, '.staging-'));
    const apk = join(staging, 'signed.apk');
    await copyFile(input, apk, constants.COPYFILE_EXCL);
    const release = await inspect(apk, tools, signer);
    if (previous && release.versionCode <= previous.versionCode) {
      throw Error(`versionCode must be greater than the published ${previous.versionCode}`);
    }
    const metadata = join(staging, 'release.json');
    await writeFile(metadata, `${JSON.stringify(release, null, 2)}\n`, { flag: 'wx' });
    const destination = join(directory, apkName(release.versionCode));
    await link(apk, destination); // Atomic and refuses to overwrite a versioned file.
    added = destination;
    await rename(metadata, metadataPath); // The new release becomes visible only after the APK is in place.
    added = null;
    console.log(`Published ${release.applicationId} ${release.versionName} (${release.versionCode}) in ${directory}`);
  } finally {
    await lock.close();
    if (added) await rm(added, { force: true });
    if (staging) await rm(staging, { recursive: true, force: true });
    await rm(lockPath, { force: true });
  }
}

try {
  const [apk, directory, ...extras] = process.argv.slice(2);
  if (!apk || !directory || extras.length) throw Error('Usage: npm run release:android -- <signed.apk> <private-release-dir>');
  const signer = (process.env.ANDROID_SIGNER_SHA256 ?? '').trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(signer)) throw Error('Set ANDROID_SIGNER_SHA256 to the trusted release certificate SHA-256 (64 hex characters)');
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  const tools = process.env.ANDROID_BUILD_TOOLS_DIR || (sdk && join(sdk, 'build-tools', '35.0.0'));
  if (!tools) throw Error('Set ANDROID_HOME or ANDROID_BUILD_TOOLS_DIR to use Android SDK Build-Tools');
  await publish(resolve(apk), resolve(directory), resolve(tools), signer);
} catch (error) {
  console.error(`Release not published: ${error.message}`);
  process.exitCode = 1;
}
