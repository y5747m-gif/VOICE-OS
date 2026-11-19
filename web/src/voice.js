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
