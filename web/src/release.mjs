export const ANDROID_APP_ID = 'ai.voiceos';
export const APK_MAX_SIZE = 160 * 1024 * 1024;
export const RELEASE_PATH = '/api/android/release';
export const apkName = (code) => `voice-os-${code}.apk`;
export const apkPath = (code) => `/downloads/${apkName(code)}`;

export function parseRelease(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || input.schema !== 1 || input.applicationId !== ANDROID_APP_ID) return null;
  const { versionCode, versionName, minSdk, sizeBytes, sha256, signingSha256, downloadPath } = input;
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > 2_100_000_000) return null;
  if (typeof versionName !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_.+-]{0,31}$/.test(versionName)) return null;
  if (!Number.isInteger(minSdk) || minSdk < 26 || minSdk > 99) return null;
  if (!Number.isInteger(sizeBytes) || sizeBytes < 1024 || sizeBytes > APK_MAX_SIZE) return null;
  if (typeof sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(sha256)) return null;
  if (typeof signingSha256 !== 'string' || !/^[0-9a-f]{64}$/.test(signingSha256)) return null;
  if (downloadPath !== apkPath(versionCode)) return null;
  return { schema: 1, applicationId: ANDROID_APP_ID, versionCode, versionName, minSdk, sizeBytes, sha256, signingSha256, downloadPath };
}
