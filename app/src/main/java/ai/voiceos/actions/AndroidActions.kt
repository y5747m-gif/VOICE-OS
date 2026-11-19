package ai.voiceos.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import ai.voiceos.core.ActionExecutor
import ai.voiceos.core.ActionFailure
import ai.voiceos.core.ActionResult
import ai.voiceos.core.DeviceAction
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Foreground-only adapter. All destinations are compiled in; no extras are built from user text. */
class AndroidActions : ActionExecutor {
    private var host: WeakReference<Activity>? = null

    fun attach(activity: Activity) { host = WeakReference(activity) }

    fun detach(activity: Activity): Boolean {
        if (host?.get() !== activity) return false
        host = null
        return true
    }

    override suspend fun execute(action: DeviceAction): ActionResult = withContext(Dispatchers.Main.immediate) {
        val activity = host?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return@withContext ActionResult.Failed(ActionFailure.NOT_FOREGROUND)
        currentCoroutineContext().ensureActive()
        try {
            if (action == DeviceAction.BATTERY) return@withContext readBattery(activity)
            val intent = when (action) {
                DeviceAction.WHATSAPP -> activity.launchIntent("com.whatsapp")
                DeviceAction.YOUTUBE -> activity.launchIntent("com.google.android.youtube")
                DeviceAction.INSTAGRAM -> activity.launchIntent("com.instagram.android")
                DeviceAction.FACEBOOK -> activity.launchIntent("com.facebook.katana")
                DeviceAction.SPOTIFY -> activity.launchIntent("com.spotify.music")
                DeviceAction.MAPS -> activity.launchIntent("com.google.android.apps.maps")
                DeviceAction.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                DeviceAction.DIALER -> Intent(Intent.ACTION_DIAL) // no phone number, never ACTION_CALL
                DeviceAction.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
                DeviceAction.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                DeviceAction.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                DeviceAction.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                DeviceAction.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                DeviceAction.BATTERY -> error("Battery is handled before launching an activity")
            } ?: return@withContext ActionResult.Failed(ActionFailure.NO_HANDLER)
            activity.startActivity(intent)
            ActionResult.Dispatched
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(ActionFailure.NO_HANDLER)
        } catch (_: SecurityException) {
            ActionResult.Failed(ActionFailure.BLOCKED)
        } catch (_: RuntimeException) {
            ActionResult.Failed(ActionFailure.PLATFORM_ERROR)
        }
    }

    private fun Activity.launchIntent(packageId: String): Intent? = packageManager.getLaunchIntentForPackage(packageId)

    private fun readBattery(context: Context): ActionResult {
        val level = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level != null && level in 0..100) ActionResult.Battery(level)
        else ActionResult.Failed(ActionFailure.DATA_UNAVAILABLE)
    }
}
