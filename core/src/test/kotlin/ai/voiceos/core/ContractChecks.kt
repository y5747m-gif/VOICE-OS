package ai.voiceos.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Dependency-light contract checks; these use an explicit test double, never production UI data. */
fun main() {
    verifyCommandContracts()
    verifyReleaseContracts()
}

internal fun verifyCommandContracts() = runBlocking {
    val grammar = LocalInterpreter()
    val calls = mutableListOf<DeviceAction>()
    val plans = mutableListOf<DeviceAction>()
    val engine = CommandEngine(grammar) { action ->
        calls += action
        when (action) {
            DeviceAction.WHATSAPP -> ActionResult.Failed(ActionFailure.NO_HANDLER)
            DeviceAction.BATTERY -> ActionResult.Battery(63)
            else -> ActionResult.Dispatched
        }
    }
    val supported = listOf(
        "لو سمحت، افتح الواتساب." to DeviceAction.WHATSAPP,
        "اِفتح واتس آب" to DeviceAction.WHATSAPP,
        "ممكن تفتحلي اليوتيوب؟" to DeviceAction.YOUTUBE,
        "  PLEASE, open   YouTube! " to DeviceAction.YOUTUBE,
        "افتح إنستغرام" to DeviceAction.INSTAGRAM,
        "open facebook" to DeviceAction.FACEBOOK,
        "شغّل سبوتيفاي" to DeviceAction.SPOTIFY,
        "افتح الكاميرا" to DeviceAction.CAMERA,
        "open the phone app" to DeviceAction.DIALER,
        "افتح خرائط جوجل" to DeviceAction.MAPS,
        "افتح الإعدادات" to DeviceAction.SETTINGS,
        "can you open Wi‑Fi settings, please?" to DeviceAction.WIFI,
        "افتح إعدادات البلوتوث" to DeviceAction.BLUETOOTH,
        "افتحلي إعدادات الموقع لو سمحت" to DeviceAction.LOCATION,
        "open display settings" to DeviceAction.DISPLAY,
        "what’s my battery level?" to DeviceAction.BATTERY,
        "البطّارية كام؟" to DeviceAction.BATTERY,
    )
    supported.forEach { (text, action) ->
        check(grammar.interpret(text) == Interpretation.Matched(action)) { "Expected $action for $text" }
    }
    check(supported.map { it.second }.toSet() == DeviceAction.entries.toSet())

    val rejected = listOf(
        "" to Unmatched.EMPTY, "   ؟ " to Unmatched.EMPTY,
        "ا".repeat(241) to Unmatched.TOO_LONG,
        "افتح واتساب وابعت لمحمد إني هتأخر شوية" to Unmatched.UNSUPPORTED,
        "افتح واتساب وبعدين افتح اليوتيوب" to Unmatched.MULTIPLE,
        "فكرني بكرة الساعة 8 أكلم أحمد، وبعدها افتحلي يوتيوب وشغل فيديو تعليمي." to Unmatched.MULTIPLE,
        "open YouTube; send a message" to Unmatched.MULTIPLE,
        "open YouTube and then open the camera" to Unmatched.MULTIPLE,
        "open YouTube\nopen the camera" to Unmatched.UNSUPPORTED,
        "open YouTube and play a video" to Unmatched.UNSUPPORTED,
        "open YouTube, open camera please" to Unmatched.UNSUPPORTED,
        "open WhatsApp to message Mom" to Unmatched.UNSUPPORTED,
        "لا تفتح الكاميرا" to Unmatched.UNSUPPORTED,
        "شغل الواي فاي" to Unmatched.UNSUPPORTED,
        "turn off bluetooth" to Unmatched.UNSUPPORTED,
        "clear all history" to Unmatched.UNSUPPORTED,
        "call 12345" to Unmatched.UNSUPPORTED,
        "open com.example.arbitrary" to Unmatched.UNSUPPORTED,
        "open intent://send" to Unmatched.UNSUPPORTED,
        "open wallpaper" to Unmatched.UNSUPPORTED,
        "اتصل بمحمد" to Unmatched.UNSUPPORTED,
    )
    rejected.forEach { (text, reason) ->
        check(engine.submit(text, plans::add) == CommandResult.NotUnderstood(reason)) { "Unexpected parse for $text" }
    }
    check(calls.isEmpty() && plans.isEmpty()) { "Unmatched inputs cannot reach the executor" }

    check(engine.submit("open WhatsApp", plans::add) == CommandResult.Executed(
        DeviceAction.WHATSAPP, ActionResult.Failed(ActionFailure.NO_HANDLER),
    )) { "Failing adapter result must not become success" }
    check(engine.submit("البطارية كام؟", plans::add) == CommandResult.Executed(
        DeviceAction.BATTERY, ActionResult.Battery(63),
    ))
    check(engine.run(DeviceAction.CAMERA, plans::add) == CommandResult.Executed(
        DeviceAction.CAMERA, ActionResult.Dispatched,
    ))
    check(plans == calls && calls == listOf(DeviceAction.WHATSAPP, DeviceAction.BATTERY, DeviceAction.CAMERA))
    val cancelled = launch(start = CoroutineStart.LAZY) {
        engine.run(DeviceAction.MAPS) { cancel() }
    }
    cancelled.join()
    check(cancelled.isCancelled && calls.size == 3) { "Cancelled plans cannot dispatch" }
    check(DeviceAction.entries.map { it.key }.distinct().size == DeviceAction.entries.size)
    check(DeviceAction.entries.all { DeviceAction.fromKey(it.key) == it })
    check(DeviceAction.fromKey("anything-else") == null)
    println("VOICE OS core: ${supported.size} bilingual matches, ${rejected.size} refusals, dispatch, result and cancellation contracts passed.")
}
