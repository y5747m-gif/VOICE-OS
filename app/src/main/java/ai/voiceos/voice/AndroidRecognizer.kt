package ai.voiceos.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ai.voiceos.permissions.MicrophonePermission
import ai.voiceos.settings.RecognitionMode
import ai.voiceos.settings.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal enum class VoiceIssue { PERMISSION, NO_ON_DEVICE, NO_PROVIDER, NO_SPEECH, TIMEOUT, LANGUAGE, BUSY, AUDIO, PROVIDER }

internal sealed interface Recognition {
    data class Final(val text: String) : Recognition
    data class Failed(val cause: VoiceIssue) : Recognition
}

internal sealed interface SpeechUpdate {
    data object Ready : SpeechUpdate
    data object Beginning : SpeechUpdate
    data class Level(val value: Float) : SpeechUpdate
    data class Partial(val text: String) : SpeechUpdate
    data object EndOfSpeech : SpeechUpdate
}

/** Main-thread, press-to-talk recognizer. One disposable instance per request prevents late callbacks. */
internal class AndroidRecognizer(private val context: Context) {
    private data class Attempt(val client: SpeechRecognizer, val result: CompletableDeferred<Recognition>)
    private var active: Attempt? = null

    fun availability(mode: RecognitionMode): VoiceIssue? = try {
        when (mode) {
            RecognitionMode.ON_DEVICE -> if (Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) null else VoiceIssue.NO_ON_DEVICE
            RecognitionMode.SYSTEM -> if (SpeechRecognizer.isRecognitionAvailable(context)) null else VoiceIssue.NO_PROVIDER
        }
    } catch (_: RuntimeException) {
        VoiceIssue.PROVIDER
    }

    suspend fun listen(language: VoiceLanguage, mode: RecognitionMode, update: (SpeechUpdate) -> Unit): Recognition {
        if (!MicrophonePermission.granted(context)) return Recognition.Failed(VoiceIssue.PERMISSION)
        availability(mode)?.let { return Recognition.Failed(it) }
        if (active != null) return Recognition.Failed(VoiceIssue.BUSY)
        val client = try {
            // Never silently swap the platform's on-device API for a possibly networked provider.
            if (mode == RecognitionMode.ON_DEVICE) {
                if (Build.VERSION.SDK_INT < 31) return Recognition.Failed(VoiceIssue.NO_ON_DEVICE)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: SecurityException) {
            return Recognition.Failed(VoiceIssue.PERMISSION)
        } catch (_: RuntimeException) {
            return Recognition.Failed(VoiceIssue.PROVIDER)
        }
        val attempt = Attempt(client, CompletableDeferred())
        active = attempt
        try {
            fun isCurrent() = active === attempt && !attempt.result.isCompleted
            fun publish(event: SpeechUpdate) {
                if (isCurrent()) update(event)
            }
            client.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = publish(SpeechUpdate.Ready)
                override fun onBeginningOfSpeech() = publish(SpeechUpdate.Beginning)
                override fun onBufferReceived(buffer: ByteArray?) = Unit // microphone bytes aren't kept
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onEndOfSpeech() = publish(SpeechUpdate.EndOfSpeech)
                override fun onRmsChanged(rmsdB: Float) {
                    val level = if (rmsdB.isFinite()) ((rmsdB + 2f) / 14f).coerceIn(0f, 1f) else 0f
                    publish(SpeechUpdate.Level(level))
                }
                override fun onError(error: Int) {
                    if (isCurrent()) attempt.result.complete(Recognition.Failed(issueFor(error)))
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    firstText(partialResults)?.let { publish(SpeechUpdate.Partial(it)) }
                }
                override fun onResults(results: Bundle?) {
                    if (!isCurrent()) return
                    val text = firstText(results)
                    attempt.result.complete(if (text.isNullOrBlank()) Recognition.Failed(VoiceIssue.NO_SPEECH) else Recognition.Final(text))
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.key)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            client.startListening(intent)
            return withTimeoutOrNull(25_000L) { attempt.result.await() } ?: Recognition.Failed(VoiceIssue.TIMEOUT)
        } catch (_: SecurityException) {
            return Recognition.Failed(VoiceIssue.PERMISSION)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            return Recognition.Failed(VoiceIssue.PROVIDER)
        } finally {
            if (active === attempt) release(attempt)
        }
    }

    fun finishSpeech() {
        val attempt = active ?: return
        try {
            attempt.client.stopListening()
        } catch (_: RuntimeException) {
            attempt.result.complete(Recognition.Failed(VoiceIssue.PROVIDER))
        }
    }

    fun cancel() {
        active?.let(::release)
    }

    private fun release(attempt: Attempt) {
        active = null // ignore any callbacks queued by cancel() / destroy()
        attempt.result.cancel()
        try { attempt.client.cancel() } catch (_: RuntimeException) { /* best effort on a dying binder */ }
        try { attempt.client.destroy() } catch (_: RuntimeException) { /* already detached */ }
    }

    private fun firstText(bundle: Bundle?): String? = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun issueFor(error: Int): VoiceIssue = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceIssue.PERMISSION
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceIssue.NO_SPEECH
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceIssue.LANGUAGE
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceIssue.BUSY
        SpeechRecognizer.ERROR_AUDIO -> VoiceIssue.AUDIO
        else -> VoiceIssue.PROVIDER
    }
}
