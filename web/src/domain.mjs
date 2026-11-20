export const VOICE = Object.freeze({
  id: 'ML7jGRDg4E9hIl5qEm1Z',
  name: 'Suhair Al Abtah',
  asArabic: 'سهير الأبطح',
  url: 'https://elevenlabs.io/voices/ML7jGRDg4E9hIl5qEm1Z',
  model: 'eleven_multilingual_v2',
});

export const ACTIONS = Object.freeze([
  { id: 'youtube', name: { ar: 'يوتيوب', en: 'YouTube' }, phrase: { ar: 'افتح يوتيوب', en: 'Open YouTube' }, url: 'https://www.youtube.com/', aliases: ['اليوتيوب', 'يو توب', 'you tube'] },
  { id: 'whatsapp', name: { ar: 'واتساب ويب', en: 'WhatsApp Web' }, phrase: { ar: 'افتح واتساب ويب', en: 'Open WhatsApp Web' }, url: 'https://web.whatsapp.com/', aliases: ['واتساب', 'واتس اب', 'واتس', 'الواتساب', 'whatsapp', 'whats app'] },
  { id: 'maps', name: { ar: 'خرائط جوجل', en: 'Google Maps' }, phrase: { ar: 'افتح الخرائط', en: 'Open Google Maps' }, url: 'https://www.google.com/maps/', aliases: ['الخرائط', 'خرائط', 'خرائط غوغل', 'maps'] },
  { id: 'spotify', name: { ar: 'سبوتيفاي', en: 'Spotify' }, phrase: { ar: 'افتح سبوتيفاي', en: 'Open Spotify' }, url: 'https://open.spotify.com/', aliases: ['سبوتيفي'] },
  { id: 'instagram', name: { ar: 'إنستجرام', en: 'Instagram' }, phrase: { ar: 'افتح إنستجرام', en: 'Open Instagram' }, url: 'https://www.instagram.com/', aliases: ['إنستغرام', 'انستقرام', 'انستا'] },
  { id: 'facebook', name: { ar: 'فيسبوك', en: 'Facebook' }, phrase: { ar: 'افتح فيسبوك', en: 'Open Facebook' }, url: 'https://www.facebook.com/', aliases: ['فيس بوك', 'الفيسبوك', 'الفيس'] },
]);

const byId = new Map(ACTIONS.map((action) => [action.id, action]));
export function actionById(id) { return byId.get(id); }
export const COMMAND_LIMIT = 180;
export const localeTag = (locale) => locale === 'ar' ? 'ar-EG' : 'en-US';

const normalize = (phrase) => phrase.normalize('NFKC').toLowerCase()
  .replace(/[\u0640\u064b-\u065f\u0670\p{Cf}]/gu, '')
  .replace(/[أإآٱ]/gu, 'ا').replaceAll('ى', 'ي').replaceAll('ة', 'ه')
  .replace(/\s+/gu, ' ').trim().replace(/[.!?؟]+$/u, '').trim();
const commands = new Map();
for (const action of ACTIONS) {
  const terms = new Set([action.name.ar, action.name.en, ...action.aliases]);
  for (const term of terms) for (const verb of ['افتح', 'افتحلي', 'افتح لي', 'تفتحلي', 'تفتح', 'وريني', 'open', 'launch', 'show']) {
    commands.set(normalize(`${verb} ${term}`), action);
  }
  for (const phrase of Object.values(action.phrase)) commands.set(normalize(phrase), action);
}

export function interpretCommand(value) {
  if (typeof value !== 'string' || !value.trim()) return { code: 'empty' };
  if (value.length > COMMAND_LIMIT) return { code: 'tooLong' };
  const phrase = normalize(value)
    .replace(/^(?:(?:please|can you|could you|ممكن|لو سمحت|من فضلك)[,،]? )/u, '')
    .replace(/[,،]? (?:please|لو سمحت|من فضلك)$/u, '');
  const action = commands.get(phrase);
  if (action) return { code: 'prepared', actionId: action.id };
  if (/(?:^|\s)(?:and then|then|وبعدين|وبعدها|ثم|بعد كده)(?:\s|$)|[;؛]/u.test(phrase)) return { code: 'compound' };
  return { code: 'unsupported' };
}

// The shared catalogue defines the ONLY text that the /api/voice endpoint may send.
// A transcript, contact, URL or other arbitrary input is never a synthesis parameter.
export function voiceLine({ locale, code, actionId }) {
  if (locale !== 'ar' && locale !== 'en') return null;
  const ar = locale === 'ar';
  switch (code) {
    case 'sample': return ar ? 'أهلًا، أنا فويس أو إس. قول أمر واحد، وأنا أساعدك تفتح الموقع المطلوب.' : 'Hi, I’m Voice OS. Say one command, and I’ll help you open the right website.';
    case 'opened': {
      const action = actionById(actionId);
      if (!action) return null;
      return ar ? `طلبت من المتصفح يفتح ${action.name.ar} في تبويب جديد.` : `I asked the browser to open ${action.name.en} in a new tab.`;
    }
    case 'opened-tab': {
      const action = actionById(actionId);
      if (!action) return null;
      return ar ? `طلبت من المتصفح يفتح ${action.name.ar} في التبويب الحالي.` : `I asked the browser to open ${action.name.en} in this tab.`;
    }
    case 'handoff': {
      const action = actionById(actionId);
      if (!action) return null;
      return ar ? `طلبت من تطبيق VOICE OS يفتح ${action.name.ar} على طول.` : `I asked the VOICE OS app to open ${action.name.en} directly.`;
    }
    case 'download': return ar ? 'التطبيق مش مثبّت على التليفون. بنحولك على مكان تحميله على طول.' : 'The app isn’t installed on this phone. Taking you to its download page directly.';
    case 'popup': return ar ? 'المتصفح منع التبويب الجديد. اسمح بالتبويبات المنبثقة وجرّب تاني.' : 'The browser blocked the new tab. Allow pop-ups for this site and try again.';
    case 'unsupported': return ar ? 'الأمر ده مش مدعوم في نسخة الويب. جرّب عبارة واحدة من صفحة الأوامر.' : 'This command isn’t supported on the web. Try one phrase from the Actions page.';
    case 'compound': return ar ? 'قول أمر واحد في المرة. ما اتفتحش أي موقع.' : 'Try one command at a time. No website was opened.';
    case 'tooLong': return ar ? 'الأمر طويل. جرّب عبارة أقصر.' : 'That command is too long. Try a shorter phrase.';
    case 'empty': return ar ? 'قول أو اكتب أمر الأول.' : 'Say or type a command first.';
    default: return null;
  }
}
