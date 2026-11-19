package ai.voiceos.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One foreground, allowlisted action at most. An executor result is never synthesized here. */
class CommandEngine(
    private val interpreter: LocalInterpreter,
    private val executor: ActionExecutor,
) {
    suspend fun submit(text: String, onPlan: (DeviceAction) -> Unit): CommandResult =
        when (val interpretation = interpreter.interpret(text)) {
            is Interpretation.NotMatched -> CommandResult.NotUnderstood(interpretation.reason)
            is Interpretation.Matched -> run(interpretation.action, onPlan)
        }

    suspend fun run(action: DeviceAction, onPlan: (DeviceAction) -> Unit): CommandResult.Executed {
        currentCoroutineContext().ensureActive()
        onPlan(action)
        currentCoroutineContext().ensureActive()
        return CommandResult.Executed(action, executor.execute(action))
    }
}
