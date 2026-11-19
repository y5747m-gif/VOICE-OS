package ai.voiceos.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

internal object MicrophonePermission {
    const val NAME = Manifest.permission.RECORD_AUDIO

    fun granted(context: Context): Boolean = context.checkSelfPermission(NAME) == PackageManager.PERMISSION_GRANTED

    fun openAppSettings(activity: Activity): Boolean = try {
        val uri = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
