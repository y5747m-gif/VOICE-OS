import { StrictMode, useCallback, useEffect, useId, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ArrowRight, ArrowUp, ArrowUpRight, AudioLines, Check, ChevronRight, CircleAlert,
  Clock3, Facebook, Globe2, Headphones, History, Instagram, Keyboard, MapPin,
  MessageCircle, Mic, Music2, Pause, Settings2, ShieldCheck, Sparkles, Square, Trash2,
  Smartphone, Volume2, X, Youtube, Zap,
} from 'lucide-react';
import '@fontsource-variable/manrope';
import '@fontsource-variable/alexandria';
import '@fontsource/ibm-plex-sans-arabic/400.css';
import '@fontsource/ibm-plex-sans-arabic/500.css';
import '@fontsource/ibm-plex-sans-arabic/600.css';
import { ACTIONS, COMMAND_LIMIT, VOICE, actionById, interpretCommand, localeTag, voiceLine } from './domain.mjs';
import { copy } from './copy.mjs';
import { useGroqSpeechInput, useSpeechInput, useVoicePlayer } from './voice.js';
import { MobilePanel, mobileCopy, useInstallation } from './mobile.jsx';
import wave from './assets/silk-wave.jpg';
import './style.css';

const icons = { youtube: Youtube, whatsapp: MessageCircle, maps: MapPin, spotify: Music2, instagram: Instagram, facebook: Facebook };
const pages = [{ id: 'home', icon: AudioLines }, { id: 'actions', icon: Zap }, { id: 'history', icon: History }];
const quick = ACTIONS.slice(0, 3);
const getPage = () => pages.find((entry) => `#${entry.id}` === window.location.hash)?.id ?? 'home';

function readPref(key, valid, fallback) {
  try {
    const value = localStorage.getItem(`voiceos:${key}`);
    return valid.includes(value) ? value : fallback;
  } catch { return fallback; }
}
function writePref(key, value) { try { localStorage.setItem(`voiceos:${key}`, value); } catch { /* browser storage is optional */ } }

function Crystal({ className = '' }) {
  const id = useId();
  return (
    <svg className={`crystal ${className}`} viewBox="0 0 100 100" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id={`${id}-base`} x1="18" y1="16" x2="88" y2="94" gradientUnits="userSpaceOnUse">
          <stop stopColor="#f6c7fa" /><stop offset=".42" stopColor="#c17aeb" /><stop offset="1" stopColor="#7547d2" />
        </linearGradient>
        <linearGradient id={`${id}-top`} x1="10" y1="9" x2="80" y2="82" gradientUnits="userSpaceOnUse">
          <stop stopColor="#fff0ff" /><stop offset=".51" stopColor="#d685eb" /><stop offset="1" stopColor="#995cd5" />
        </linearGradient>
        <radialGradient id={`${id}-face`} cx="0" cy="0" r="1" gradientTransform="translate(49 44) rotate(88) scale(49)">
          <stop stopColor="#fff8ff" /><stop offset=".37" stopColor="#e8b5fb" /><stop offset="1" stopColor="#a164df" />
        </radialGradient>
        <linearGradient id={`${id}-edge`} x1="17" y1="28" x2="71" y2="93" gradientUnits="userSpaceOnUse">
          <stop stopColor="#f1adea" /><stop offset="1" stopColor="#764cdc" />
        </linearGradient>
      </defs>
      <path d="M41 6 76 17 92 45 81 76 53 92 21 83 7 53 17 24Z" fill={`url(#${id}-base)`} />
      <path d="m41 6 35 11-12 14-29-7L17 24z" fill={`url(#${id}-top)`} />
      <path d="m76 17 16 28-19 7-9-21z" fill="#a66de5" />
      <path d="m92 45-11 31-21-7 13-17z" fill="#8e61dc" />
      <path d="m81 76-28 16-8-19 15-4z" fill="#7449cf" />
      <path d="m53 92-32-9 10-18 14 8z" fill="#9b64e3" />
      <path d="M21 83 7 53l22-8 2 20z" fill={`url(#${id}-edge)`} />
      <path d="m7 53 10-29 18 0-6 21z" fill="#e4a0e9" />
      <path d="m35 24 29 7 9 21-13 17-15 4-14-8-2-20z" fill={`url(#${id}-face)`} />
      <path d="m35 24 29 7m-29-7-6 21m44 7-13 17M41 6l35 11" stroke="#fff" strokeOpacity=".38" strokeWidth=".95" />
      <path d="m30 44 15-14 13 4-9 17-13 7z" fill="white" opacity=".19" />
    </svg>
  );
}

function AppIcon({ id }) {
  const Icon = icons[id] ?? Globe2;
  return <span className={`app-icon tone-${id}`} aria-hidden="true"><Icon size={20} strokeWidth={1.8} /></span>;
}

function Topbar({ t, language, page, navigate, toggleLanguage, settings }) {
  return (
    <header className="topbar glass">
      <button className="brand" onClick={() => navigate('home')} type="button" aria-label={`VOICE OS · ${t.home}`}>
        <span className="brand-logo" aria-hidden="true"><AudioLines size={25} strokeWidth={2} /></span>
        <span className="brand-title"><strong lang="en" dir="ltr">VOICE<span> OS</span></strong><small>{t.nameLine}</small></span>
      </button>
      <nav className="navigation" aria-label={language === 'ar' ? 'القائمة الرئيسية' : 'Main navigation'}>
        {pages.map(({ id, icon: Icon }) => (
          <button key={id} type="button" onClick={() => navigate(id)} aria-current={page === id ? 'page' : undefined}>
            <Icon size={16} strokeWidth={1.8} aria-hidden="true" /><span>{t[id]}</span>
          </button>
        ))}
      </nav>
      <div className="topbar-tools">
        <button type="button" className="language-button" onClick={toggleLanguage} aria-label={t.language} title={t.language}>
          <Globe2 size={16} strokeWidth={1.8} aria-hidden="true" /> <span>{language === 'ar' ? 'EN' : 'عربي'}</span>
        </button>
        <button type="button" className="icon-button top-settings" onClick={settings} aria-label={t.settings} title={t.settings}>
          <Settings2 size={19} strokeWidth={1.9} aria-hidden="true" />
        </button>
      </div>
    </header>
  );
}

function Heading({ t, page, mobile, installLabel, openInstall }) {
  const home = page === 'home';
  return (
    <div className="page-heading">
      <div className="eyebrow"><Sparkles size={15} strokeWidth={1.8} aria-hidden="true" /><span>{home ? t.eyebrow : t[page]}</span></div>
      <h1>{home ? <><span>{t.headline[0]}</span> {t.headline[1]}</> : t[`${page}Title`]}</h1>
      <p>{home ? t.intro : t[`${page}Intro`]}</p>
      <button type="button" className="install-pill" data-update={mobile.waiting} onClick={openInstall}>
        <Smartphone size={16} strokeWidth={1.8} aria-hidden="true" />{mobile.waiting ? installLabel.updateBadge : installLabel.button}
      </button>
    </div>
  );
}

function VoiceConsole({ t, state, mic, micPress, stop, draft, onDraft, send, inputRef, result, language, launch, player, mode, speak, resultRef, canUseGroq, askGroq }) {
  const listening = mic.phase === 'listening';
  const thinking = mic.phase === 'transcribing' || state === 'transcribing';
  const captured = state !== 'ready' && !listening && !thinking;
  const stageCopy = {
    ready: t.stageTitle, listening: t.stageListening, transcribing: t.stageWait, prepared: t.stagePrepared,
    opened: t.stageOpened, stopped: t.stageStopped, error: t.stageError,
  };
  const overLimit = draft.length > COMMAND_LIMIT;
  return (
    <section className={`console glass state-${state}`} aria-label={t.console}>
      <div className="stage">
        <div className="stage-chrome">
          <span className="console-tag"><AudioLines size={16} strokeWidth={1.8} aria-hidden="true" /><span>{t.console}</span></span>
          <span className="state-tag" role="status"><i aria-hidden="true" />{t[state]}</span>
        </div>
        <div className="wave-art" aria-hidden="true"><img src={wave} alt="" fetchPriority="high" /></div>
        <span className="stage-star star-a" aria-hidden="true">✦</span><span className="stage-star star-b" aria-hidden="true">✦</span>
        <div className="stage-copy">
          <div className="crystal-bay"><i aria-hidden="true" /><Crystal /><span className="little-flare" aria-hidden="true">✧</span></div>
          <h2>{stageCopy[state]}</h2>
          <p>{listening ? t.hintListening : captured ? t.hintCaptured : thinking ? '' : t.hint}</p>
        </div>
        <div className="mic-station">
          <div className="mic-halo" aria-hidden="true" />
          <button type="button" className="mic-button" onClick={micPress} disabled={thinking} aria-label={thinking ? t.wait : listening ? t.finish : t.press}>
            {listening ? <Pause size={32} strokeWidth={2.25} aria-hidden="true" /> : <Mic size={32} strokeWidth={2.05} aria-hidden="true" />}
          </button>
          {(listening || thinking || state === 'prepared') && (
            <button type="button" className="icon-button cancel-voice" onClick={stop} aria-label={t.stop} title={t.stop}><Square size={17} strokeWidth={2} aria-hidden="true" /></button>
          )}
        </div>
        <div className="mic-label">{thinking ? t.wait : listening ? t.finish : t.press}</div>
      </div>
      {mic.partial && <p className="partial" dir="auto">{mic.partial}</p>}
      <div className="typing">
        <form className="composer" onSubmit={send}>
          <Keyboard size={21} strokeWidth={1.65} className="keyboard-icon" aria-hidden="true" />
          <input ref={inputRef} type="text" value={draft} onChange={(event) => onDraft(event.target.value)}
            disabled={listening || thinking} autoComplete="off" spellCheck="false" placeholder={t.placeholder}
            aria-label={t.input} aria-invalid={overLimit} />
          {draft.length === 0 && <kbd aria-hidden="true">/</kbd>}
          <button className="submit-button" type="submit" disabled={!draft.trim() || listening || thinking || overLimit} aria-label={t.submit} title={t.submit}>
            <ArrowUp size={20} strokeWidth={2.15} aria-hidden="true" />
          </button>
        </form>
        {overLimit && <p className="form-error" role="status">{voiceLine({ locale: language, code: 'tooLong' })}</p>}
      </div>
      {result && <ResultCard result={result} t={t} language={language} launch={launch} stop={stop} player={player} mode={mode} speak={speak} cardRef={resultRef} canUseGroq={canUseGroq} askGroq={askGroq} />}
    </section>
  );
}

function ResultCard({ result, t, language, launch, stop, mode, player, speak, cardRef, canUseGroq, askGroq }) {
  const action = actionById(result.actionId);
  const preview = result.code === 'prepared' && action;
  const line = result.code === 'micError' ? (t.micError[result.issue] ?? t.micError.unknown)
    : result.code === 'aiError' ? (t.groqError[result.issue] ?? t.groqError.provider_error)
      : result.code === 'aiThinking' ? t.groqThinking
        : result.code === 'stopped' ? t.stopMessage
          : voiceLine({ locale: language, code: result.code, ...(result.code === 'opened' ? { actionId: result.actionId } : {}) });
  const canPlay = mode !== 'muted' && ['opened', 'popup', 'unsupported', 'compound', 'tooLong', 'empty'].includes(result.code);
  return (
    <div className="result-wrap" ref={cardRef} tabIndex={-1} role="region" aria-label={t.result}>
      <div className="result-card">
        {result.transcript && <div className="heard"><span>{t.prompt}</span><p dir="auto">{result.transcript}</p></div>}
        {preview ? (
          <div className="plan">
            <AppIcon id={action.id} />
            <div><strong>{t.plan(action.name[language])}</strong><small><bdi>{new URL(action.url).hostname}</bdi> · {t.planNote}</small></div>
            <div className="plan-buttons"><button className="primary-button" type="button" onClick={() => launch(action, result.transcript)}>{t.openWebsite}<ArrowUpRight size={17} aria-hidden="true" /></button>
              <button className="text-button" type="button" onClick={stop}>{t.dismiss}</button></div>
          </div>
        ) : (
          <div className="reply" aria-live="polite">
            <span className={`reply-icon ${result.code !== 'opened' ? 'reply-warning' : ''}`} aria-hidden="true">{result.code === 'opened' ? <Check size={18} /> : <CircleAlert size={18} />}</span>
            <div><span className="reply-label">{t.result}</span><p>{line}</p></div>
            {canPlay && <button className="icon-button" type="button" aria-label={player.busy ? t.stopAudio : t.replay} title={player.busy ? t.stopAudio : t.replay}
              onClick={() => player.busy ? player.stop() : speak(result)}>{player.busy ? <Square size={17} aria-hidden="true" /> : <Volume2 size={20} aria-hidden="true" />}</button>}
          </div>
        )}
        {result.code === 'unsupported' && canUseGroq && <div className="ai-understand"><button className="subtle-button" type="button" onClick={() => askGroq(result.transcript)}><Sparkles size={16} aria-hidden="true" />{t.askGroq}</button></div>}
        {!preview && <AudioState player={player} t={t} />}
      </div>
    </div>
  );
}

function AudioState({ player, t }) {
  const { phase, issue } = player.state;
  if (phase === 'idle') return null;
  const text = phase === 'error' ? t.audioIssues[issue] ?? t.audioIssues.provider_error : phase === 'loading' ? t.loadingAudio : t.playing;
  return <p className={`audio-state ${phase === 'error' ? 'audio-error' : ''}`} role="status"><Volume2 size={15} aria-hidden="true" /> {text}</p>;
}

function QuickPanel({ t, language, launch, navigate }) {
  return (
    <section className="quick-card glass" aria-labelledby="quick-title">
      <div className="side-heading">
        <span className="mini-tile blush" aria-hidden="true"><Zap size={18} strokeWidth={1.9} /></span>
        <div><h2 id="quick-title">{t.quick}</h2><p>{t.quickDetail}</p></div>
      </div>
      <div className="shortcut-list">
        {quick.map((action) => (
          <button className="shortcut" type="button" key={action.id} onClick={() => launch(action)}>
            <AppIcon id={action.id} /><span><strong>{action.name[language]}</strong><small>{action.phrase[language]}</small></span>
            <ArrowUpRight size={17} strokeWidth={1.65} className="link-arrow" aria-hidden="true" />
          </button>
        ))}
      </div>
      <button type="button" className="text-button all-actions" onClick={() => navigate('actions')}>{t.seeAll}<ArrowRight size={16} className="dir-arrow" aria-hidden="true" /></button>
    </section>
  );
}

function VoiceProfile({ t, language, configuration, settings }) {
  return (
    <section className="voice-profile glass" aria-labelledby="voice-profile-label">
      <div className="voice-profile-top">
        <h2 id="voice-profile-label"><Headphones size={16} aria-hidden="true" /> {t.voiceProfile}</h2>
        <a className="icon-button source-link" href={VOICE.url} target="_blank" rel="noreferrer" title={t.source} aria-label={t.source}><ArrowUpRight size={17} aria-hidden="true" /></a>
      </div>
      <div className="voice-identity">
        <span className="gem-avatar"><Crystal /></span>
        <div><strong>{language === 'ar' ? VOICE.asArabic : VOICE.name}</strong><small>{t.arabicVoice}</small></div>
      </div>
      <div className="voice-profile-foot">
        <span className="setup-status"><i aria-hidden="true" />{configuration === 'checking' ? t.keyChecking : configuration === 'configured' ? t.keyAdded : t.keyNeeded}</span>
        <button type="button" className="text-button" onClick={settings}>{t.voiceSetup}<ChevronRight size={15} className="dir-arrow" aria-hidden="true" /></button>
      </div>
    </section>
  );
}

function Recent({ t, language, entries, navigate }) {
  const entry = entries[0];
  const action = entry && actionById(entry.actionId);
  return (
    <section className="recent glass" aria-labelledby="recent-title">
      <span className="mini-tile lilac" aria-hidden="true"><Clock3 size={20} strokeWidth={1.75} /></span>
      <div className="recent-copy">
        <h2 id="recent-title">{t.activity}</h2>
        {entry ? <p>{action?.name[language]} · {entry.code === 'opened' ? t.opened : t.error}</p> : <p>{t.noRecentDetail}</p>}
      </div>
      <button type="button" className="subtle-button" onClick={() => navigate('history')}>{t.viewHistory}<ArrowRight size={16} className="dir-arrow" aria-hidden="true" /></button>
    </section>
  );
}

function ActionsPage({ t, language, launch }) {
  return (
    <div className="action-grid">
      {ACTIONS.map((action) => (
        <button className="action-card glass" type="button" key={action.id} onClick={() => launch(action)}>
          <span className="catalog-top"><AppIcon id={action.id} /><ArrowUpRight size={22} strokeWidth={1.65} className="link-arrow" aria-hidden="true" /></span>
          <strong>{action.name[language]}</strong><span>{action.phrase[language]}</span><small>{t.newTab}</small>
        </button>
      ))}
    </div>
  );
}

function HistoryPage({ t, language, entries, navigate, clear }) {
  if (!entries.length) return (
    <section className="empty-card glass"><span className="empty-orb" aria-hidden="true"><History size={30} strokeWidth={1.55} /></span><h2>{t.emptyHistory}</h2><p>{t.emptyHistoryDetail}</p>
      <button className="primary-button" type="button" onClick={() => navigate('home')}>{t.backHome}<ArrowRight size={18} className="dir-arrow" aria-hidden="true" /></button></section>
  );
  const format = new Intl.DateTimeFormat(localeTag(language), { hour: 'numeric', minute: '2-digit' });
  return (
    <section className="activity-list glass">
      <div className="activity-header"><h2>{t.history}</h2><button className="subtle-button" type="button" onClick={clear}><Trash2 size={16} aria-hidden="true" />{t.clearHistory}</button></div>
      {entries.map((item) => {
        const action = actionById(item.actionId);
        return <div className="activity-item" key={item.id}><AppIcon id={item.actionId} /><div><strong>{action.name[language]}</strong>
          <p>{voiceLine({ locale: language, code: item.code, ...(item.code === 'opened' ? { actionId: item.actionId } : {}) })}</p></div>
          <time dateTime={new Date(item.at).toISOString()}>{format.format(item.at)}</time></div>;
      })}
    </section>
  );
}

function Modal({ title, icon: Icon, onClose, t, children }) {
  const ref = useRef(null);
  const id = useId();
  useEffect(() => { const node = ref.current; node.showModal(); return () => node.close(); }, []);
  return (
    <dialog ref={ref} className="modal" aria-labelledby={id} onCancel={(event) => { event.preventDefault(); onClose(); }}
      onClick={(event) => { if (event.target === ref.current) onClose(); }}>
      <div className="modal-inner">
        <div className="modal-heading"><span className="mini-tile lilac" aria-hidden="true"><Icon size={20} strokeWidth={1.8} /></span><h2 id={id}>{title}</h2>
          <button type="button" className="icon-button close-button" onClick={onClose} aria-label={t.close}><X size={20} aria-hidden="true" /></button></div>
        {children}
      </div>
    </dialog>
  );
}

function VoiceSettings({ t, configuration, mode, choose, auto, onAuto, player, sample, onClose }) {
  const choices = [
    { id: 'browser', label: t.browserVoice, detail: t.browserVoiceDetail },
    { id: 'eleven', label: t.elevenVoice, detail: t.elevenVoiceDetail, disabled: configuration !== 'configured' },
    { id: 'muted', label: t.muted, detail: t.mutedDetail },
  ];
  return <Modal title={t.speechTitle} icon={Headphones} onClose={onClose} t={t}>
    <p className="modal-detail">{t.speechDetail}</p>
    <fieldset className="voice-choices"><legend className="sr-only">{t.speechTitle}</legend>
      {choices.map((choice) => <label className={`voice-choice ${mode === choice.id ? 'selected' : ''}`} key={choice.id}>
        <input type="radio" name="voice" checked={mode === choice.id} disabled={choice.disabled} onChange={() => choose(choice.id)} />
        <span><strong>{choice.label}</strong><small>{choice.detail}</small></span>
      </label>)}
    </fieldset>
    <p className="config-hint"><ShieldCheck size={16} aria-hidden="true" />{configuration === 'configured' ? t.configuredHint : t.configHint}</p>
    {mode !== 'muted' && <label className="auto-speak"><input type="checkbox" checked={auto} onChange={(event) => onAuto(event.target.checked)} />
      <span><strong>{t.autoSpeak}</strong><small>{t.autoSpeakDetail}</small></span></label>}
    <AudioState t={t} player={player} />
    <div className="modal-actions">
      {mode !== 'muted' && <button className="primary-button" type="button" onClick={() => player.busy ? player.stop() : sample()}>
        {player.busy ? <Square size={17} aria-hidden="true" /> : <Volume2 size={18} aria-hidden="true" />}{player.busy ? t.stopAudio : t.sample}</button>}
      <a href={VOICE.url} className="text-button" target="_blank" rel="noreferrer">ElevenLabs <ArrowUpRight size={16} aria-hidden="true" /></a>
    </div>
  </Modal>;
}

function App() {
  const [language, setLanguage] = useState(() => readPref('language', ['ar', 'en'], 'ar'));
  const t = copy[language];
  const installLabel = mobileCopy[language];
  const device = useInstallation();
  const [page, setPage] = useState(getPage);
  const [mode, setMode] = useState(() => readPref('mode', ['browser', 'eleven', 'muted'], 'browser'));
  const [auto, setAuto] = useState(() => readPref('auto', ['on', 'off'], 'off') === 'on');
  const [configuration, setConfiguration] = useState('checking');
  const [groqConfiguration, setGroqConfiguration] = useState('checking');
  const [draft, setDraft] = useState('');
  const [dialog, setDialog] = useState(null);
  const [pendingGroq, setPendingGroq] = useState(null);
  const [result, setResult] = useState(null);
  const [entries, setEntries] = useState([]);
  const [activeProvider, setActiveProvider] = useState(null);
  const inputRef = useRef(null);
  const resultRef = useRef(null);
  const micApproved = useRef(false);
  const groqMicApproved = useRef(false);
  const sequence = useRef(0);
  const aiRequest = useRef(null);
  const browserMic = useSpeechInput();
  const groqMic = useGroqSpeechInput();
  const useGroqMic = groqConfiguration === 'configured' && groqMic.supported;
  const canUseGroq = groqConfiguration === 'configured';
  const mic = activeProvider === 'groq' ? groqMic : activeProvider === 'browser' ? browserMic : useGroqMic ? groqMic : browserMic;
  const cancelVoiceInput = useCallback(() => {
    const browser = browserMic.cancel();
    const groq = groqMic.cancel();
    setActiveProvider(null);
    return browser || groq;
  }, [browserMic.cancel, groqMic.cancel]);
  const cancelAiRequest = useCallback(() => {
    if (!aiRequest.current) return false;
    aiRequest.current.abort();
    aiRequest.current = null;
    sequence.current++;
    return true;
  }, []);
  const player = useVoicePlayer();

  useEffect(() => {
    document.documentElement.lang = localeTag(language);
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    document.title = language === 'ar' ? 'VOICE OS · واجهتك الصوتية' : 'VOICE OS · Your Voice Workspace';
    writePref('language', language);
  }, [language]);

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/voice/status', { signal: controller.signal, cache: 'no-store' })
      .then((res) => res.ok ? res.json() : Promise.reject())
      .then((data) => setConfiguration(data.configured && data.voiceId === VOICE.id ? 'configured' : 'missing'))
      .catch(() => { if (!controller.signal.aborted) setConfiguration('missing'); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    const deadline = setTimeout(() => controller.abort(), 5000);
    fetch('/api/groq/status', { signal: controller.signal, cache: 'no-store' })
      .then((res) => res.ok ? res.json() : Promise.reject())
      .then((data) => { if (live) setGroqConfiguration(data.configured === true ? 'configured' : 'missing'); })
      .catch(() => { if (live) setGroqConfiguration('missing'); })
      .finally(() => clearTimeout(deadline));
    return () => { live = false; clearTimeout(deadline); controller.abort(); };
  }, []);

  useEffect(() => {
    const onHash = () => {
      const cancelled = cancelVoiceInput();
      const cancelledAi = cancelAiRequest();
      if (cancelled || cancelledAi) setResult({ code: 'stopped', id: ++sequence.current });
      setPage(getPage());
    };
    addEventListener('hashchange', onHash);
    return () => removeEventListener('hashchange', onHash);
  }, [cancelVoiceInput, cancelAiRequest]);
  useEffect(() => { window.scrollTo(0, 0); }, [page]);
  useEffect(() => {
    if (!result || page !== 'home') return;
    const node = resultRef.current;
    if (result.code === 'prepared') node?.focus({ preventScroll: true });
    if (node && node.getBoundingClientRect().bottom > window.innerHeight - 20) {
      node.scrollIntoView({ block: 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
    }
  }, [page, result]);

  function navigate(target) {
    if (page === target) return;
    const inputCancelled = cancelVoiceInput();
    const aiCancelled = cancelAiRequest();
    if (inputCancelled || aiCancelled) setResult({ code: 'stopped', id: ++sequence.current });
    setPage(target);
    if (window.location.hash !== `#${target}`) window.location.hash = target;
  }
  function stop() {
    cancelVoiceInput();
    cancelAiRequest();
    player.stop();
    setResult({ code: 'stopped', id: ++sequence.current });
  }
  function speak(reply) {
    const payload = { locale: language, code: reply.code, ...(reply.code === 'opened' ? { actionId: reply.actionId } : {}) };
    return player.play(payload, mode);
  }
  function process(text) {
    if (aiRequest.current) return;
    player.stop();
    const reply = { ...interpretCommand(text), transcript: text.slice(0, COMMAND_LIMIT), id: ++sequence.current };
    setResult(reply);
    if (auto && reply.code !== 'prepared') void speak(reply);
  }
  function send(event) {
    event.preventDefault();
    if (!draft.trim() || mic.phase !== 'idle' || aiRequest.current || draft.length > COMMAND_LIMIT) return;
    process(draft);
    setDraft('');
    inputRef.current?.blur();
  }
  function launch(action, transcript) {
    // The popup is opened synchronously from a user's click. Recognizer callbacks only prepare a plan.
    cancelVoiceInput();
    cancelAiRequest();
    player.stop();
    let code = 'popup';
    let tab;
    try {
      tab = window.open('about:blank', '_blank');
      if (tab && !tab.closed) {
        tab.opener = null;
        tab.location.replace(action.url);
        code = 'opened';
      }
    } catch { try { tab?.close(); } catch { /* popup may already be closed */ } }
    const reply = { id: ++sequence.current, code, actionId: action.id, at: Date.now() };
    setEntries((previous) => [reply, ...previous].slice(0, 40));
    setResult({ ...reply, transcript });
    if (page !== 'home') navigate('home');
    if (auto) void speak(reply);
  }
  function startMic() {
    setResult(null);
    player.stop();
    const provider = useGroqMic ? groqMic : browserMic;
    setActiveProvider(useGroqMic ? 'groq' : 'browser');
    provider.start(language, (text) => {
      setActiveProvider(null);
      process(text);
    }, (issue) => {
      setActiveProvider(null);
      setResult({ code: 'micError', issue, id: ++sequence.current });
    }, () => {
      setActiveProvider(null);
      setResult({ code: 'stopped', id: ++sequence.current });
    });
  }
  function micPress() {
    if (mic.phase === 'listening') { mic.finish(); return; }
    if (mic.phase !== 'idle' || aiRequest.current) return;
    const approved = useGroqMic ? groqMicApproved.current : micApproved.current;
    if (!mic.supported || approved) startMic();
    else setDialog('mic');
  }
  function askGroq(command) {
    if (!canUseGroq || typeof command !== 'string' || !command.trim() || command.length > COMMAND_LIMIT) return;
    setPendingGroq(command);
    setDialog('groq-consent');
  }
  async function sendToGroq() {
    const command = pendingGroq;
    setPendingGroq(null);
    setDialog(null);
    if (!canUseGroq || typeof command !== 'string' || !command.trim()) return;
    cancelVoiceInput();
    cancelAiRequest();
    player.stop();
    const controller = new AbortController();
    const requestId = ++sequence.current;
    aiRequest.current = controller;
    setResult({ code: 'aiThinking', transcript: command, id: requestId });
    try {
      const response = await fetch('/api/groq/interpret', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ locale: language, command }), signal: controller.signal,
      });
      const data = await response.json().catch(() => ({}));
      if (aiRequest.current !== controller || sequence.current !== requestId) return;
      if (!response.ok) {
        setResult({ code: 'aiError', issue: data.error ?? 'provider_error', transcript: command, id: requestId });
        return;
      }
      const action = actionById(data.actionId);
      setResult(action
        ? { code: 'prepared', actionId: action.id, transcript: command, id: requestId }
        : { code: 'unsupported', transcript: command, id: requestId });
    } catch (error) {
      if (error?.name !== 'AbortError' && aiRequest.current === controller) {
        setResult({ code: 'aiError', issue: 'provider_error', transcript: command, id: requestId });
      }
    } finally {
      if (aiRequest.current === controller) aiRequest.current = null;
    }
  }
  function chooseMode(value) {
    if (value === 'eleven' && configuration !== 'configured') return;
    if (value === 'eleven' && mode !== 'eleven') { setDialog('consent'); return; }
    persistMode(value);
  }
  function persistMode(value) { player.stop(); setMode(value); writePref('mode', value); }
  function settings() {
    const inputCancelled = cancelVoiceInput();
    const aiCancelled = cancelAiRequest();
    if (inputCancelled || aiCancelled) setResult({ code: 'stopped', id: ++sequence.current });
    setDialog('settings');
  }
  function changeLanguage() {
    const inputCancelled = cancelVoiceInput();
    const aiCancelled = cancelAiRequest();
    if (inputCancelled || aiCancelled) setResult({ code: 'stopped', id: ++sequence.current });
    if (dialog === 'groq-consent') { setDialog(null); setPendingGroq(null); }
    player.stop();
    setLanguage(language === 'ar' ? 'en' : 'ar');
  }

  useEffect(() => {
    function keyboard(event) {
      if (dialog || page !== 'home') return;
      if (event.key === 'Escape' && player.busy && mic.phase === 'idle' && result?.code !== 'prepared' && !aiRequest.current) player.stop();
      else if (event.key === 'Escape' && (mic.phase !== 'idle' || result?.code === 'prepared' || aiRequest.current)) stop();
      else if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey && !event.target?.matches?.('input,textarea,[contenteditable]')) {
        event.preventDefault();
        inputRef.current?.focus();
      }
    }
    window.addEventListener('keydown', keyboard);
    return () => window.removeEventListener('keydown', keyboard);
  });

  const state = mic.phase !== 'idle' ? mic.phase : result?.code === 'aiThinking' ? 'transcribing' : !result ? 'ready'
    : ['prepared', 'opened', 'stopped'].includes(result.code) ? result.code : 'error';

  return (
    <>
      <div className="ambient" aria-hidden="true"><i className="sphere sphere-pink" /><i className="sphere sphere-lilac" /><i className="sphere sphere-pearl" /><i className="sphere sphere-tiny" /></div>
      <div className="shell">
        <Topbar t={t} language={language} page={page} navigate={navigate} toggleLanguage={changeLanguage} settings={settings} />
        <main>
          <Heading t={t} page={page} mobile={device} installLabel={installLabel} openInstall={() => { cancelVoiceInput(); cancelAiRequest(); player.stop(); setDialog('install'); }} />
          {page === 'home' && <>
            <div className="workspace">
              <VoiceConsole t={t} state={state} mic={mic} micPress={micPress} stop={stop} draft={draft} onDraft={setDraft} send={send}
                inputRef={inputRef} result={result} language={language} launch={launch} player={player} mode={mode} speak={speak} resultRef={resultRef}
                canUseGroq={canUseGroq} askGroq={askGroq} />
              <aside className="side-column"><QuickPanel t={t} language={language} launch={launch} navigate={navigate} />
                <VoiceProfile t={t} language={language} configuration={configuration} settings={settings} /></aside>
            </div>
            <Recent t={t} language={language} entries={entries} navigate={navigate} />
          </>}
          {page === 'actions' && <ActionsPage t={t} language={language} launch={launch} />}
          {page === 'history' && <HistoryPage t={t} language={language} entries={entries} navigate={navigate} clear={() => setDialog('clear')} />}
        </main>
        <footer><span><ShieldCheck size={15} strokeWidth={1.8} aria-hidden="true" />{t.privacy}</span><span lang="en" dir="ltr">VOICE OS <i /> WEB EDITION</span></footer>
      </div>
      {dialog === 'install' && <Modal title={installLabel.title} icon={Smartphone} onClose={() => setDialog(null)} t={t}>
        <MobilePanel language={language} device={device} beforeRefresh={() => { cancelVoiceInput(); cancelAiRequest(); player.stop(); }} />
      </Modal>}
      {dialog === 'settings' && <VoiceSettings t={t} configuration={configuration} mode={mode} choose={chooseMode} auto={auto}
        onAuto={(value) => { setAuto(value); writePref('auto', value ? 'on' : 'off'); }}
        player={player} sample={() => speak({ code: 'sample' })} onClose={() => setDialog(null)} />}
      {dialog === 'mic' && <Modal title={t.micTitle} icon={Mic} onClose={() => setDialog(null)} t={t}>
        <p className="modal-detail">{useGroqMic ? t.groqMicDetail : t.micDetail}</p><div className="modal-actions"><button className="primary-button" type="button" onClick={() => { if (useGroqMic) groqMicApproved.current = true; else micApproved.current = true; setDialog(null); startMic(); }}>{t.micStart}</button>
          <button className="text-button" type="button" onClick={() => setDialog(null)}>{t.dismiss}</button></div>
      </Modal>}
      {dialog === 'groq-consent' && <Modal title={t.groqConsentTitle} icon={ShieldCheck} onClose={() => { setDialog(null); setPendingGroq(null); }} t={t}>
        <p className="modal-detail">{t.groqConsentDetail}</p><div className="modal-actions"><button className="primary-button" type="button" onClick={sendToGroq}>{t.groqSend}</button>
          <button className="text-button" type="button" onClick={() => { setDialog(null); setPendingGroq(null); }}>{t.dismiss}</button></div>
      </Modal>}
      {dialog === 'consent' && <Modal title={t.voiceEnable} icon={ShieldCheck} onClose={() => setDialog('settings')} t={t}>
        <p className="modal-detail">{t.voiceConsent}</p><div className="modal-actions"><button className="primary-button" type="button" onClick={() => { persistMode('eleven'); setDialog('settings'); }}>{t.enable}</button>
          <button className="text-button" type="button" onClick={() => setDialog('settings')}>{t.dismiss}</button></div>
      </Modal>}
      {dialog === 'clear' && <Modal title={t.clearQuestion} icon={Trash2} onClose={() => setDialog(null)} t={t}>
        <p className="modal-detail">{t.clearDetail}</p><div className="modal-actions"><button className="primary-button" type="button" onClick={() => { setEntries([]); setResult(null); player.stop(); setDialog(null); }}>{t.clear}</button>
          <button className="text-button" type="button" onClick={() => setDialog(null)}>{t.dismiss}</button></div>
      </Modal>}
    </>
  );
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>);
