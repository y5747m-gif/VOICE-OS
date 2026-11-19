package ai.voiceos.ai

import ai.voiceos.core.CommandResult
import ai.voiceos.core.DeviceAction
import ai.voiceos.data.InputSource
import ai.voiceos.voice.VoiceIssue

internal enum class Phase { READY, LISTENING, THINKING, EXECUTING, COMPLETED, ERROR, STOPPED }

internal enum class Capture { CONNECTING, WAITING, SPEECH }

internal sealed interface Reply {
    data class Command(val report: CommandResult) : Reply
    data class VoiceError(val reason: VoiceIssue) : Reply
    data object Stopped : Reply
}

internal data class Session(
    val phase: Phase = Phase.READY,
    val source: InputSource? = null,
    val transcript: String? = null,
    val action: DeviceAction? = null,
    val reply: Reply? = null,
    val level: Float = 0f,
    val transcriptFinal: Boolean = false,
    val sequence: Long = 0,
    val capture: Capture? = null,
) {
    val busy: Boolean get() = phase == Phase.LISTENING || phase == Phase.THINKING || phase == Phase.EXECUTING
    val visible: Boolean get() = phase != Phase.READY
}

internal data class HistoryEvent(
    val id: String,
    val at: Long,
    val source: InputSource,
    val result: CommandResult,
    val transcript: String? = null,
) {
    val action: DeviceAction? get() = (result as? CommandResult.Executed)?.action
}

internal data class HistoryState(
    val entries: List<HistoryEvent> = emptyList(),
    val problem: Boolean = false,
    val clearing: Boolean = false,
)

internal data class MemoryState(val name: String? = null, val busy: Boolean = true, val problem: Boolean = false)
