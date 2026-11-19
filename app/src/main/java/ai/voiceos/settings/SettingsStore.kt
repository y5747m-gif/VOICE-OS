package ai.voiceos.settings

import android.content.Context
import android.content.res.Configuration
import ai.voiceos.core.DeviceAction
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class VoiceLanguage(val key: String, val locale: Locale) {
    ARABIC("ar-EG", Locale.forLanguageTag("ar-EG")), ENGLISH("en-US", Locale.US);
}
internal enum class Appearance(val key: String) { DARK("dark"), LIGHT("light"), SYSTEM("system") }
internal enum class RecognitionMode { ON_DEVICE, SYSTEM }

internal data class UserSettings(
    val language: VoiceLanguage = VoiceLanguage.ARABIC,
    val appearance: Appearance = Appearance.DARK,
    val recognition: RecognitionMode = RecognitionMode.ON_DEVICE,
    val speakReplies: Boolean = true,
    val reduceMotion: Boolean = false,
    val keepHistory: Boolean = false,
    val pins: List<DeviceAction> = DEFAULT_PINS,
) {
    companion object {
        const val MAX_PINS = 3
        val DEFAULT_PINS = listOf(DeviceAction.YOUTUBE, DeviceAction.CAMERA, DeviceAction.WIFI)
    }
}

internal class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()

    fun update(change: (UserSettings) -> UserSettings) {
        val next = change(mutable.value)
        if (next == mutable.value) return
        preferences.edit()
            .putString("language", next.language.key)
            .putString("appearance", next.appearance.key)
            .putBoolean("allow_system_speech", next.recognition == RecognitionMode.SYSTEM)
            .putBoolean("speak_replies", next.speakReplies)
            .putBoolean("reduce_motion", next.reduceMotion)
            .putBoolean("keep_history", next.keepHistory)
            .putString("pins", next.pins.joinToString(",") { it.key })
            .apply()
        mutable.value = next
    }

    private fun load() = UserSettings(
        language = VoiceLanguage.entries.find { it.key == preferences.getString("language", null) } ?: VoiceLanguage.ARABIC,
        appearance = Appearance.entries.find { it.key == preferences.getString("appearance", null) } ?: Appearance.DARK,
        recognition = if (preferences.getBoolean("allow_system_speech", false)) RecognitionMode.SYSTEM else RecognitionMode.ON_DEVICE,
        speakReplies = preferences.getBoolean("speak_replies", true),
        reduceMotion = preferences.getBoolean("reduce_motion", false),
        keepHistory = preferences.getBoolean("keep_history", false),
        pins = preferences.getString("pins", null)?.split(',')?.mapNotNull(DeviceAction::fromKey)
            ?.distinct()?.take(UserSettings.MAX_PINS) ?: UserSettings.DEFAULT_PINS,
    )
}

internal fun Context.localized(language: VoiceLanguage, base: Configuration = resources.configuration): Context =
    createConfigurationContext(Configuration(base).apply { setLocale(language.locale) })
