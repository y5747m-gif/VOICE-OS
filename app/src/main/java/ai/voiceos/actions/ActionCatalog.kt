package ai.voiceos.actions

import ai.voiceos.R
import ai.voiceos.core.DeviceAction
import ai.voiceos.core.DeviceAction.*

internal enum class ActionGroup(val title: Int, val detail: Int) {
    APPS(R.string.group_apps, R.string.group_apps_detail),
    PANELS(R.string.group_panels, R.string.group_panels_detail),
    DEVICE(R.string.group_device, R.string.group_device_detail),
}

internal data class ActionText(val title: Int, val phrase: Int, val group: ActionGroup)

internal fun DeviceAction.copyText(): ActionText = when (this) {
    WHATSAPP -> ActionText(R.string.action_whatsapp, R.string.phrase_whatsapp, ActionGroup.APPS)
    YOUTUBE -> ActionText(R.string.action_youtube, R.string.phrase_youtube, ActionGroup.APPS)
    INSTAGRAM -> ActionText(R.string.action_instagram, R.string.phrase_instagram, ActionGroup.APPS)
    FACEBOOK -> ActionText(R.string.action_facebook, R.string.phrase_facebook, ActionGroup.APPS)
    SPOTIFY -> ActionText(R.string.action_spotify, R.string.phrase_spotify, ActionGroup.APPS)
    CAMERA -> ActionText(R.string.action_camera, R.string.phrase_camera, ActionGroup.APPS)
    DIALER -> ActionText(R.string.action_dialer, R.string.phrase_dialer, ActionGroup.APPS)
    MAPS -> ActionText(R.string.action_maps, R.string.phrase_maps, ActionGroup.APPS)
    SETTINGS -> ActionText(R.string.action_settings, R.string.phrase_settings, ActionGroup.PANELS)
    WIFI -> ActionText(R.string.action_wifi, R.string.phrase_wifi, ActionGroup.PANELS)
    BLUETOOTH -> ActionText(R.string.action_bluetooth, R.string.phrase_bluetooth, ActionGroup.PANELS)
    LOCATION -> ActionText(R.string.action_location, R.string.phrase_location, ActionGroup.PANELS)
    DISPLAY -> ActionText(R.string.action_display, R.string.phrase_display, ActionGroup.PANELS)
    BATTERY -> ActionText(R.string.action_battery, R.string.phrase_battery, ActionGroup.DEVICE)
}
