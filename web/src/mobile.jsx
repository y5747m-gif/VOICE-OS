import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowUpRight, Download, Info, RefreshCw, ShieldCheck, Smartphone } from 'lucide-react';
import { parseRelease, RELEASE_PATH } from './release.mjs';

export const mobileCopy = {
  ar: {
    button: 'ثبّت التطبيق', title: 'التثبيت والتحديثات', updateBadge: 'تحديث جاهز',
    web: 'تطبيق الموقع', webDetail: 'نفس الواجهة بتفتح من أيقونة مستقلة. أوامر الهاتف المحمية بتفضل في تطبيق Android الأصلي.',
    webInstalled: 'الموقع مفتوح كتطبيق مثبّت.', install: 'ثبّت من المتصفح',
    instructions: 'على Android: افتح قائمة Chrome واختر «تثبيت التطبيق» أو «إضافة إلى الشاشة الرئيسية». على iPhone: افتح الموقع في Safari، اضغط مشاركة ثم «إضافة إلى الشاشة الرئيسية».',
    openTab: 'افتح في تبويب مستقل', updateTitle: 'تحديث تطبيق الموقع', check: 'افحص التحديثات', apply: 'حدّث وافتح من جديد',
    checking: 'بنفحص وجود تحديث للموقع…', downloading: 'المتصفح بينزّل ملفات النسخة الجديدة…',
    waiting: 'النسخة الجديدة جاهزة. فتحها هيبدأ جلسة جديدة؛ سجل الصفحة الحالي مش هيتحفظ.',
    applying: 'بنفتح النسخة الجديدة…', current: 'مفيش نسخة ويب أحدث دلوقتي.',
    idle: 'المتصفح بيفحص نسخة الموقع الجديدة عند فتحه. تقدر كمان تفحص يدويًا.',
    updateFailed: 'مقدرناش نفحص نسخة الموقع. اتأكد من الاتصال بالإنترنت وجرّب تاني.',
    serviceUnavailable: 'تحديث تطبيق الموقع يحتاج فتح نسخة الإنتاج عبر HTTPS في متصفح مدعوم، مش خادم التطوير.',
    android: 'تطبيق Android الأصلي', androidDetail: 'ملف APK منفصل لأوامر الهاتف. تثبيته أو تحديثه يحتاج موافقة Android.',
    loadingApk: 'بنفحص الإصدار المنشور…', noApk: 'لسه مفيش APK موقّع منشور على الموقع. تقدر تثبّت تطبيق الموقع من فوق.',
    apkError: 'مقدرناش نقرأ الإصدار المنشور. اتأكد من اتصال الموقع وجرّب تاني.', retry: 'جرّب تاني',
    download: 'نزّل APK', minAndroid: (level) => `Android API ${level} أو أحدث`,
    appChecking: 'بنبحث عن تطبيق VOICE OS على التليفون…',
    appDetected: 'الموقع اكتشف إن التطبيق مثبّت.', appDetectedDetail: 'الأوامر هتنفذ على طول في التطبيق نفسه من غير ما تروح من هنا.',
    appMissing: 'الموقع مالقاش تطبيق VOICE OS على التليفون.', appMissingDetail: 'الأوامر هتروح على طول لمكان تحميل التطبيق.',
    appUnknown: 'مفيش كشف لتطبيق Android من هنا. الأوامر هتفتح في المتصفح.',
    openApp: 'افتح التطبيق', goDownload: 'روح لمكان التحميل',
  },
  en: {
    button: 'Install app', title: 'Install & updates', updateBadge: 'Update ready',
    web: 'Web app', webDetail: 'The same site opens from its own icon. Protected phone actions still need the native Android app.',
    webInstalled: 'This site is open as an installed app.', install: 'Install from browser',
    instructions: 'On Android: in Chrome’s menu, choose Install app or Add to Home screen. On iPhone: open in Safari, tap Share, then Add to Home Screen.',
    openTab: 'Open in a separate tab', updateTitle: 'Web app updates', check: 'Check for updates', apply: 'Update and reopen',
    checking: 'Checking for a newer web app…', downloading: 'The browser is downloading the new app files…',
    waiting: 'The new version is ready. Reopening starts a fresh session and clears this page’s history.',
    applying: 'Opening the new version…', current: 'No newer web version is available right now.',
    idle: 'Your browser checks for a newer version when you open the app. You can also check manually.',
    updateFailed: 'Couldn’t check for updates. Check your connection and try again.',
    serviceUnavailable: 'Web app updates require the production site over HTTPS in a supported browser, not the development server.',
    android: 'Native Android app', androidDetail: 'A separate APK for phone actions. Android asks you to approve installation or an update.',
    loadingApk: 'Checking the published release…', noApk: 'No signed APK is published here yet. You can install the web app above.',
    apkError: 'Couldn’t read the published release. Check your connection and retry.', retry: 'Retry',
    download: 'Download APK', minAndroid: (level) => `Android API ${level} or newer`,
    appChecking: 'Looking for the VOICE OS app on this phone…',
    appDetected: 'The site found the app installed.', appDetectedDetail: 'Commands run directly in the app itself without leaving here.',
    appMissing: 'The site couldn’t find the VOICE OS app on this phone.', appMissingDetail: 'Commands go straight to the app’s download location.',
    appUnknown: 'No Android app detection from here. Commands open in the browser.',
    openApp: 'Open the app', goDownload: 'Go to the download page',
  },
};

export function useInstallation() {
  const [prompt, setPrompt] = useState(null);
  const standalone = () => matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  const [installed, setInstalled] = useState(standalone);
  const supported = import.meta.env.PROD && window.isSecureContext && 'serviceWorker' in navigator;
  const [status, setStatus] = useState(supported ? 'idle' : 'serviceUnavailable');
  const [waiting, setWaiting] = useState(false);
  const registration = useRef(null);
  const requestedActivation = useRef(false);

  useEffect(() => {
    function capture(event) { event.preventDefault(); setPrompt(event); }
    function markInstalled() { setInstalled(true); setPrompt(null); }
    window.addEventListener('beforeinstallprompt', capture);
    window.addEventListener('appinstalled', markInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', capture);
      window.removeEventListener('appinstalled', markInstalled);
    };
  }, []);

  useEffect(() => {
    if (!supported) return;
    const workers = navigator.serviceWorker;
    let live = true;
    let installedListener = null;
    let hadController = Boolean(workers.controller);
    const subscriptions = [];
    function onController() {
      if (requestedActivation.current) { window.location.reload(); return; }
      setWaiting(false);
      // Another open client may have applied the update. Don't interrupt this session.
      if (hadController) setStatus('waiting');
      hadController = true;
    }
    function onVisible() {
      if (document.visibilityState === 'visible' && navigator.onLine) void registration.current?.update().catch(() => {});
    }
    workers.addEventListener('controllerchange', onController);
    document.addEventListener('visibilitychange', onVisible);
    workers.register('/sw.js', { scope: '/', updateViaCache: 'none' }).then((reg) => {
      if (!live) return;
      registration.current = reg;
      if (reg.waiting) { setWaiting(true); setStatus('waiting'); }
      installedListener = () => {
        const worker = reg.installing;
        if (!worker) return;
        const update = Boolean(reg.active);
        if (update) setStatus('downloading');
        const listener = () => {
          if (!live || !update) return;
          if (worker.state === 'installed') { setWaiting(true); setStatus('waiting'); }
          if (worker.state === 'redundant' && !reg.waiting) setStatus('updateFailed');
        };
        worker.addEventListener('statechange', listener);
        subscriptions.push(() => worker.removeEventListener('statechange', listener));
      };
      reg.addEventListener('updatefound', installedListener);
      if (reg.installing) installedListener();
    }).catch(() => { if (live) setStatus('updateFailed'); });
    return () => {
      live = false;
      if (installedListener) registration.current?.removeEventListener('updatefound', installedListener);
      for (const dispose of subscriptions) dispose();
      workers.removeEventListener('controllerchange', onController);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [supported]);

  const check = useCallback(async () => {
    if (!supported) return;
    if (!navigator.onLine) { setStatus('updateFailed'); return; }
    setStatus('checking');
    try {
      const reg = registration.current ?? await navigator.serviceWorker.getRegistration('/');
      if (!reg) { setStatus('updateFailed'); return; }
      await reg.update();
      if (reg.waiting) { setWaiting(true); setStatus('waiting'); }
      else if (reg.installing) setStatus('downloading');
      else setStatus((state) => state === 'waiting' ? 'waiting' : 'current');
    } catch { setStatus('updateFailed'); }
  }, [supported]);

  async function install() {
    if (!prompt) return;
    try {
      await prompt.prompt();
      await prompt.userChoice; // Actual installation is reported by appinstalled, not by accepting the prompt.
    } catch { /* Manual install instructions remain available. */ }
    finally { setPrompt(null); }
  }

  function apply() {
    const worker = registration.current?.waiting;
    if (worker) {
      requestedActivation.current = true;
      setStatus('applying');
      worker.postMessage({ type: 'VOICE_OS_ACTIVATE_UPDATE' });
    } else if (status === 'waiting') window.location.reload(); // Another window already activated it.
  }

  return { installed, canPrompt: Boolean(prompt), supported, waiting: waiting || status === 'waiting', status, install, check, apply };
}

export function usePublishedApk() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState({ status: 'checking' });
  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    setState({ status: 'checking' });
    const deadline = setTimeout(() => controller.abort(), 10_000);
    fetch(RELEASE_PATH, { cache: 'no-store', redirect: 'error', signal: controller.signal })
      .then((response) => response.ok ? response.json() : Promise.reject())
      .then((document) => {
        if (!live) return;
        if (document.schema !== 1) throw Error('invalid_release');
        if (document.release === null) setState({ status: 'missing' });
        else {
          const release = parseRelease(document.release);
          if (!release) throw Error('invalid_release');
          setState({ status: 'ready', release });
        }
      })
      .catch(() => { if (live) setState({ status: 'error' }); })
      .finally(() => clearTimeout(deadline));
    return () => { live = false; clearTimeout(deadline); controller.abort(); };
  }, [attempt]);
  return { ...state, retry: () => setAttempt((number) => number + 1) };
}

export function MobilePanel({ language, device, beforeRefresh, appState, onOpenApp, onGoDownload, apk }) {
  const t = mobileCopy[language];
  const busy = ['checking', 'downloading', 'applying'].includes(device.status);
  const format = new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en-US', { maximumFractionDigits: 1 });
  const detectionCopy = appState === 'installed' ? { title: t.appDetected, detail: t.appDetectedDetail }
    : appState === 'missing' ? { title: t.appMissing, detail: t.appMissingDetail }
      : appState === 'unknown' ? { title: t.appUnknown, detail: t.appUnknown } : null;
  return <div className="mobile-panel">
    <section className="mobile-section">
      <div className="mobile-section-heading"><Smartphone size={19} aria-hidden="true" /><h3>{t.web}</h3></div>
      <p>{t.webDetail}</p>
      <p>{device.installed ? t.webInstalled : t.instructions}</p>
      <div className="mobile-buttons">
        {!device.installed && device.canPrompt && <button className="primary-button" type="button" onClick={device.install}><Download size={17} aria-hidden="true" />{t.install}</button>}
        {!device.installed && <a href={window.location.href} target="_blank" rel="noreferrer" className="text-button">{t.openTab}<ArrowUpRight size={16} aria-hidden="true" /></a>}
      </div>
    </section>
    {detectionCopy && (
      <section className="mobile-section">
        <div className="mobile-section-heading"><RefreshCw size={18} aria-hidden="true" /><h3>{detectionCopy.title}</h3></div>
        <p>{detectionCopy.detail}</p>
        <div className="mobile-buttons">
          {appState === 'installed' && <button type="button" className="primary-button" onClick={onOpenApp}>{t.openApp}</button>}
          {appState === 'missing' && <button type="button" className="primary-button" onClick={onGoDownload}>{t.goDownload}</button>}
        </div>
      </section>
    )}
    <section className="mobile-section">
      <div className="mobile-section-heading"><RefreshCw size={18} aria-hidden="true" /><h3>{t.updateTitle}</h3></div>
      <p role="status">{t[device.status]}</p>
      <div className="mobile-buttons">
        {device.waiting ? <button type="button" className="primary-button" disabled={busy} onClick={() => { beforeRefresh(); device.apply(); }}>{t.apply}</button> :
          device.supported && <button type="button" className="subtle-button" disabled={busy} onClick={device.check}>{t.check}</button>}
      </div>
    </section>
    <section className="mobile-section">
      <div className="mobile-section-heading"><ShieldCheck size={19} aria-hidden="true" /><h3>{t.android}</h3></div>
      <p>{t.androidDetail}</p>
      {apk.status === 'ready' ? <>
        <p className="apk-details"><strong dir="ltr">v{apk.release.versionName}</strong> · {format.format(apk.release.sizeBytes / (1024 * 1024))} MB · {t.minAndroid(apk.release.minSdk)}</p>
        <div className="mobile-buttons"><a href={apk.release.downloadPath} download className="primary-button"><Download size={17} aria-hidden="true" />{t.download}</a></div>
      </> : <p className="apk-status" role="status"><Info size={15} aria-hidden="true" />{t[apk.status === 'checking' ? 'loadingApk' : apk.status === 'error' ? 'apkError' : 'noApk']}</p>}
      {apk.status === 'error' && <button className="text-button" onClick={apk.retry} type="button">{t.retry}</button>}
    </section>
  </div>;
}
