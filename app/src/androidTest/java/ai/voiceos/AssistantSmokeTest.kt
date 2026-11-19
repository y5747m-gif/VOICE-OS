package ai.voiceos

import android.os.BatteryManager
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.voiceos.ai.describe
import ai.voiceos.core.CommandResult
import ai.voiceos.core.Unmatched
import ai.voiceos.settings.localized
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a fresh debug install/emulator. Doesn't require a recognition or TTS provider. */
@RunWith(AndroidJUnit4::class)
class AssistantSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualBatteryReadingAndUnsupportedRequestUseTheSameScreen() {
        val activity = compose.activity
        val language = (activity.application as VoiceOsApplication).settings.state.value.language
        val local = activity.localized(language)
        val capacity = activity.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        compose.onNodeWithTag("home_mic").assertExists()
        compose.onNodeWithTag("nav_assistant").performClick()
        send("Check battery level")
        if (capacity != null && capacity in 0..100) {
            // On a running device the number may tick across a percentage boundary during this test.
            compose.onNodeWithTag("session_reply").assertTextContains(local.getString(R.string.reply_battery).substringBefore("%1\$s").trim(), substring = true)
        } else {
            compose.onNodeWithTag("session_reply").assertTextContains(local.getString(R.string.reply_no_battery))
        }
        send("open WhatsApp to send a message")
        compose.onNodeWithTag("session_reply").assertTextContains(local.describe(CommandResult.NotUnderstood(Unmatched.UNSUPPORTED)))
    }

    private fun send(text: String) {
        compose.onNodeWithTag("assistant_input").performTextInput(text)
        compose.onNodeWithTag("assistant_submit").performClick()
    }
}
