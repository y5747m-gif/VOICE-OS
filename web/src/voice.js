import { useCallback, useEffect, useRef, useState } from 'react';
import { localeTag, voiceLine } from './domain.mjs';

export function useSpeechInput() {
  const [phase, setPhase] = useState('idle');
  const [partial, setPartial] = useState('');
  const attempt = useRef(null);
  const supported = typeof window !== 'undefined' && window.isSecureContext && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);

  const release = useCallback((active, abort, update = true) => {
    if (attempt.current !== active) return;
    attempt.current = null;
    clearTimeout(active.timer);
    const rec = active.recognition;
    rec.onresult = rec.onerror = rec.onend = null;
    if (abort) try { rec.abort(); } catch { /* the browser may have already stopped */ }
    if (update) { setPhase('idle'); setPartial(''); }
  }, []);

  const cancel = useCallback(() => {
    if (!attempt.current) return false;
    release(attempt.current, true);
    return true;
  }, [release]);

  const start = useCallback((language, onCommand, onError, onHidden) => {
    if (attempt.current) return;
    if (!supported) { onError('unavailable'); return; }
    try {
      const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      const recognition = new Recognition();
      const active = { recognition, timer: undefined, onHidden, onError };
      attempt.current = active;
      recognition.lang = localeTag(language);
      recognition.interimResults = true;
      recognition.continuous = false;
      recognition.maxAlternatives = 1;
      const fail = (error, abort = false) => {
        if (attempt.current !== active) return;
        release(active, abort);
        onError(error);
      };
      recognition.onresult = (event) => {
        if (attempt.current !== active) return;
        const results = Array.from(event.results);
        const final = results.filter((result) => result.isFinal).map((result) => result[0]?.transcript ?? '').join(' ').trim();
        if (final) {
          release(active, true);
          onCommand(final);
        } else setPartial(results.map((result) => result[0]?.transcript ?? '').join(' ').slice(0, 180));
      };
      recognition.onerror = (event) => fail(event.error, true);
      recognition.onend = () => fail('no-speech');
      active.timer = setTimeout(() => fail('timeout', true), 22_000);
      recognition.start();
      setPhase('listening');
    } catch (e) {
      if (attempt.current) release(attempt.current, true);
      onError(e?.name === 'NotAllowedError' ? 'not-allowed' : 'unknown');
    }
  }, [release, supported]);

  const finish = useCallback(() => {
    const active = attempt.current;
    if (!active) return;
    try { active.recognition.stop(); setPhase('transcribing'); }
    catch { release(active, true); active.onError('unknown'); }
  }, [release]);

  useEffect(() => {
    function onVisibility() {
      const active = attempt.current;
      if (document.hidden && active) { release(active, true); active.onHidden(); }
    }
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      if (attempt.current) release(attempt.current, true, false);
    };
  }, [release]);

  return { phase, partial, supported, start, finish, cancel };
}

export function useVoicePlayer() {
  const [state, setState] = useState({ phase: 'idle' });
  const generation = useRef(0);
  const request = useRef(null);
  const media = useRef(null); // keep at most one ready clip, including a clip awaiting user gesture
  const local = useRef(false);

  const stop = useCallback((silent = false) => {
    generation.current++;
    request.current?.abort();
    request.current = null;
    if (media.current) {
      const audio = media.current.audio;
      audio.onended = audio.onerror = null;
      audio.pause();
    }
    if (local.current) window.speechSynthesis?.cancel();
    local.current = false;
    if (!silent) setState({ phase: 'idle' });
  }, []);

  const play = useCallback(async (payload, mode) => {
    const line = voiceLine(payload);
    if (!line || mode === 'muted') return;
    stop();
    const ticket = generation.current;
    const settle = (next) => { if (ticket === generation.current) setState(next); };
    if (mode === 'browser') {
      try {
        const synth = window.speechSynthesis;
        const lang = localeTag(payload.locale).toLowerCase();
        const voices = synth?.getVoices?.().filter((voice) => voice.localService && voice.lang.toLowerCase().split(/[-_]/)[0] === payload.locale) ?? [];
        const voice = voices.find((entry) => entry.lang.toLowerCase().replace('_', '-') === lang) ?? voices[0];
        if (!voice) { settle({ phase: 'error', issue: 'no_local' }); return; }
        const utterance = new SpeechSynthesisUtterance(line);
        utterance.voice = voice;
        utterance.lang = voice.lang;
        local.current = true;
        utterance.onend = () => settle({ phase: 'idle' });
        utterance.onerror = () => settle({ phase: 'error', issue: 'playback' });
        synth.speak(utterance);
        settle({ phase: 'playing' });
      } catch { settle({ phase: 'error', issue: 'playback' }); }
      return;
    }
    const id = JSON.stringify(payload);
    try {
      if (media.current?.id !== id) {
        if (media.current) URL.revokeObjectURL(media.current.url);
        media.current = null;
        const controller = new AbortController();
        request.current = controller;
        settle({ phase: 'loading' });
        const response = await fetch('/api/voice/speak', {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: id,
          signal: controller.signal,
        });
        if (ticket !== generation.current) return;
        if (!response.ok) {
          const error = await response.json().catch(() => ({}));
          settle({ phase: 'error', issue: error.error ?? 'provider_error' });
          return;
        }
        const blob = await response.blob();
        if (ticket !== generation.current) return;
        if (!blob.type.startsWith('audio/') || !blob.size) { settle({ phase: 'error', issue: 'provider_error' }); return; }
        const url = URL.createObjectURL(blob);
        media.current = { id, url, audio: new Audio(url) };
        request.current = null;
      }
      const audio = media.current.audio;
      audio.currentTime = 0;
      audio.onended = () => settle({ phase: 'idle' });
      audio.onerror = () => settle({ phase: 'error', issue: 'playback' });
      await audio.play();
      settle({ phase: 'playing' });
    } catch (error) {
      if (error?.name !== 'AbortError') settle({ phase: 'error', issue: error?.name === 'NotAllowedError' ? 'gesture' : 'provider_error' });
    }
  }, [stop]);

  useEffect(() => {
    // Some engines load their local voice list asynchronously after the first query.
    window.speechSynthesis?.getVoices();
    return () => {
      stop(true);
      if (media.current) URL.revokeObjectURL(media.current.url);
      media.current = null;
    };
  }, [stop]);

  return { state, play, stop: () => stop(), busy: state.phase === 'playing' || state.phase === 'loading' };
}

const MAX_RECORDING_MS = 25_000;
const UPLOAD_TIMEOUT_MS = 18_000;
const MAX_RECORDING_BYTES = 8 * 1024 * 1024;

function stopTracks(stream) {
  for (const track of stream?.getTracks?.() ?? []) try { track.stop(); } catch { /* device already released */ }
}

export function useGroqSpeechInput() {
  const [phase, setPhase] = useState('idle');
  const [partial, setPartial] = useState('');
  const attempt = useRef(null);
  const supported = typeof window !== 'undefined' && typeof navigator !== 'undefined' && window.isSecureContext &&
    Boolean(navigator.mediaDevices?.getUserMedia && window.MediaRecorder);

  const dispose = useCallback((active, abortRequest) => {
    clearTimeout(active.timer);
    if (active.recorder) {
      active.recorder.ondataavailable = active.recorder.onerror = active.recorder.onstop = null;
      if (active.recorder.state !== 'inactive') try { active.recorder.stop(); } catch { /* recording already stopped */ }
    }
    stopTracks(active.stream);
    active.stream = null;
    if (abortRequest) active.controller?.abort();
    active.controller = null;
    active.chunks.length = 0;
  }, []);

  const release = useCallback((active, abort, update = true) => {
    if (attempt.current !== active) return;
    attempt.current = null;
    dispose(active, abort);
    if (update) { setPhase('idle'); setPartial(''); }
  }, [dispose]);

  const cancel = useCallback(() => {
    if (!attempt.current) return false;
    release(attempt.current, true);
    return true;
  }, [release]);

  const start = useCallback((language, onCommand, onError, onHidden) => {
    if (attempt.current) return;
    if (!supported) { onError('unavailable'); return; }
    if (language !== 'ar' && language !== 'en') { onError('unknown'); return; }

    const active = { stream: null, recorder: null, chunks: [], bytes: 0, timer: undefined, controller: null, onCommand, onError, onHidden };
    attempt.current = active;
    setPhase('listening');

    const fail = (issue, abort = false) => {
      if (attempt.current !== active) return;
      release(active, abort);
      active.onError(issue);
    };

    const upload = async () => {
      if (attempt.current !== active) return;
      clearTimeout(active.timer);
      stopTracks(active.stream);
      active.stream = null;
      const mime = active.recorder?.mimeType || active.chunks.find((chunk) => chunk.type)?.type || 'audio/webm';
      const blob = new Blob(active.chunks, { type: mime });
      active.chunks.length = 0;
      if (!blob.size) { fail('no-speech'); return; }
      const controller = new AbortController();
      active.controller = controller;
      active.timer = setTimeout(() => fail('timeout', true), UPLOAD_TIMEOUT_MS);
      try {
        const response = await fetch('/api/groq/transcribe', {
          method: 'POST',
          headers: { 'Content-Type': mime, 'X-Voice-Language': language },
          body: blob,
          signal: controller.signal,
        });
        const data = await response.json().catch(() => ({}));
        if (attempt.current !== active) return;
        if (!response.ok) {
          const known = ['not_configured', 'provider_access', 'provider_error', 'rate_limit', 'no_transcript', 'too_long', 'too_large', 'invalid_audio'];
          fail(known.includes(data.error) ? data.error : 'provider_error');
          return;
        }
        if (typeof data.text !== 'string' || !data.text.trim()) { fail('no-speech'); return; }
        const text = data.text.trim();
        if (attempt.current !== active) return;
        attempt.current = null;
        dispose(active, false);
        setPhase('idle');
        setPartial('');
        active.onCommand(text);
      } catch (error) {
        if (error?.name !== 'AbortError') fail(error?.name === 'TimeoutError' ? 'timeout' : 'network');
      }
    };

    const acquire = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: {
          echoCancellation: true, noiseSuppression: true, autoGainControl: true,
        } });
        if (attempt.current !== active) { stopTracks(stream); return; }
        active.stream = stream;
        const candidates = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/webm', 'audio/ogg'];
        const mimeType = candidates.find((type) => window.MediaRecorder.isTypeSupported?.(type));
        active.recorder = mimeType ? new window.MediaRecorder(stream, { mimeType }) : new window.MediaRecorder(stream);
        active.recorder.ondataavailable = (event) => {
          if (attempt.current !== active || !event.data?.size) return;
          active.bytes += event.data.size;
          if (active.bytes > MAX_RECORDING_BYTES) { fail('too_large', true); return; }
          active.chunks.push(event.data);
        };
        active.recorder.onerror = () => fail('audio-capture', true);
        active.recorder.onstop = () => { void upload(); };
        active.timer = setTimeout(() => fail('timeout', true), MAX_RECORDING_MS);
        active.recorder.start(250);
      } catch (error) {
        if (attempt.current !== active) return;
        const issue = ['NotAllowedError', 'PermissionDeniedError', 'SecurityError'].includes(error?.name) ? 'not-allowed'
          : ['NotFoundError', 'DevicesNotFoundError', 'NotReadableError'].includes(error?.name) ? 'audio-capture' : 'unknown';
        fail(issue, true);
      }
    };

    void acquire();
  }, [dispose, release, supported]);

  const finish = useCallback(() => {
    const active = attempt.current;
    if (!active) return;
    if (!active.recorder) { release(active, true); active.onError('audio-capture'); return; }
    if (active.recorder.state === 'inactive') return;
    clearTimeout(active.timer);
    try {
      setPhase('transcribing');
      active.recorder.stop();
    } catch { release(active, true); active.onError('audio-capture'); }
  }, [release]);

  useEffect(() => {
    function onVisibility() {
      const active = attempt.current;
      if (document.hidden && active) { release(active, true); active.onHidden?.(); }
    }
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      if (attempt.current) release(attempt.current, true, false);
    };
  }, [release]);

  return { phase, partial, supported, start, finish, cancel };
}
