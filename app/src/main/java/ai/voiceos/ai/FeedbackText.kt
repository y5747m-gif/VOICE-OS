package ai.voiceos.ai

import android.content.Context
import ai.voiceos.R
import ai.voiceos.actions.copyText
import ai.voiceos.core.ActionFailure
import ai.voiceos.core.ActionResult
import ai.voiceos.core.CommandResult
import ai.voiceos.core.Unmatched
import ai.voiceos.voice.VoiceIssue
import java.text.NumberFormat

internal fun VoiceIssue.messageId(): Int = when (this) {
    VoiceIssue.PERMISSION -> R.string.voice_permission
    VoiceIssue.NO_ON_DEVICE -> R.string.voice_no_device
    VoiceIssue.NO_PROVIDER -> R.string.voice_no_provider
    VoiceIssue.LANGUAGE -> R.string.voice_language
    VoiceIssue.NO_SPEECH -> R.string.voice_no_speech
    VoiceIssue.TIMEOUT -> R.string.voice_timeout
    VoiceIssue.BUSY -> R.string.voice_busy
    VoiceIssue.AUDIO -> R.string.voice_audio
    VoiceIssue.PROVIDER -> R.string.voice_provider_error
}

internal fun Context.describe(reply: Reply): String = when (reply) {
    Reply.Stopped -> getString(R.string.reply_stopped)
    is Reply.VoiceError -> getString(reply.reason.messageId())
    is Reply.Command -> describe(reply.report)
}

internal fun Context.describe(report: CommandResult): String = when (report) {
    is CommandResult.NotUnderstood -> getString(when (report.reason) {
        Unmatched.EMPTY -> R.string.reply_empty
        Unmatched.TOO_LONG -> R.string.reply_long
        Unmatched.MULTIPLE -> R.string.reply_multiple
        Unmatched.UNSUPPORTED -> R.string.reply_unknown
    })
    is CommandResult.Executed -> {
        val name = getString(report.action.copyText().title)
        when (val output = report.result) {
            ActionResult.Dispatched -> getString(R.string.reply_dispatched, name)
            is ActionResult.Battery -> getString(R.string.reply_battery,
                NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(output.percent / 100.0))
            is ActionResult.Failed -> when (output.cause) {
                ActionFailure.NO_HANDLER -> getString(R.string.reply_no_handler, name)
                ActionFailure.BLOCKED -> getString(R.string.reply_blocked, name)
                ActionFailure.NOT_FOREGROUND -> getString(R.string.reply_not_foreground)
                ActionFailure.DATA_UNAVAILABLE -> getString(R.string.reply_no_battery)
                ActionFailure.PLATFORM_ERROR -> getString(R.string.reply_platform_error, name)
            }
        }
    }
}
