package ai.voiceos.core

/** The only values that can cross the executor boundary. No package, URI or code from a transcript. */
enum class DeviceAction(val key: String) {
    WHATSAPP("whatsapp"), YOUTUBE("youtube"), INSTAGRAM("instagram"), FACEBOOK("facebook"),
    SPOTIFY("spotify"), CAMERA("camera"), DIALER("dialer"), MAPS("maps"),
    SETTINGS("settings"), WIFI("wifi-settings"), BLUETOOTH("bluetooth-settings"),
    LOCATION("location-settings"), DISPLAY("display-settings"), BATTERY("battery");

    companion object {
        fun fromKey(key: String): DeviceAction? = entries.firstOrNull { it.key == key }
    }
}

enum class Unmatched { EMPTY, TOO_LONG, MULTIPLE, UNSUPPORTED }

enum class ActionFailure(val key: String) {
    NO_HANDLER("no-handler"), BLOCKED("blocked"), NOT_FOREGROUND("not-foreground"),
    DATA_UNAVAILABLE("data-unavailable"), PLATFORM_ERROR("platform-error");

    companion object {
        fun fromKey(key: String): ActionFailure? = entries.firstOrNull { it.key == key }
    }
}

sealed interface ActionResult {
    /** Android accepted startActivity. It does not attest to anything done by the destination. */
    data object Dispatched : ActionResult
    data class Battery(val percent: Int) : ActionResult {
        init { require(percent in 0..100) }
    }
    data class Failed(val cause: ActionFailure) : ActionResult
}

sealed interface CommandResult {
    data class NotUnderstood(val reason: Unmatched) : CommandResult
    data class Executed(val action: DeviceAction, val result: ActionResult) : CommandResult
}

fun interface ActionExecutor {
    suspend fun execute(action: DeviceAction): ActionResult
}
