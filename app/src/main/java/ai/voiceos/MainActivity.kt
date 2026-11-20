package ai.voiceos

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.voiceos.ai.Phase
import ai.voiceos.ai.SessionViewModel
import ai.voiceos.core.DeviceAction
import ai.voiceos.permissions.MicrophonePermission
import ai.voiceos.site.SiteViewModel
import ai.voiceos.ui.VoiceOsRoot

class MainActivity : ComponentActivity() {
    private val model by viewModels<SessionViewModel> { SessionViewModel.Factory(application as VoiceOsApplication) }
    private val site by viewModels<SiteViewModel>()
    private var resumed = false
    private var permissionForVoice: Boolean? = null
    private var listenOnResume = false
    private var pendingDeepLink: DeviceAction? = null
    private val microphoneLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val start = permissionForVoice == true
        permissionForVoice = null
        model.refreshPermission()
        if (start) {
            if (!granted) model.permissionDenied()
            else if (resumed) model.startVoice()
            else listenOnResume = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        consumeDeepLink(intent)
        setContent {
            VoiceOsRoot(
                activity = this,
                model = model,
                site = site,
                onMic = ::onMic,
                onRequestPermission = { requestMicrophone(start = false) },
                onOpenAppSettings = { MicrophonePermission.openAppSettings(this) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    /** The web edition sends voiceos://open?action=<catalogue key>; only catalogue keys run. */
    private fun consumeDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (!data.scheme?.equals("voiceos", ignoreCase = true) || data.host?.equals("open", ignoreCase = true) != true) return
        pendingDeepLink = DeviceAction.fromKey(data.getQueryParameter("action") ?: "")
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        model.enterForeground(this)
        val action = pendingDeepLink
        if (action != null && model.runDeepLink(action)) pendingDeepLink = null
        if (listenOnResume) {
            listenOnResume = false
            model.startVoice()
        }
    }

    override fun onPause() {
        resumed = false
        model.leaveForeground(this)
        super.onPause()
    }

    private fun onMic() {
        if (!resumed || permissionForVoice != null) return
        if (model.session.value.phase == Phase.LISTENING) { model.finishSpeech(); return }
        if (model.session.value.busy) return
        if (MicrophonePermission.granted(this) || model.voiceAvailability.value != null) model.startVoice()
        else requestMicrophone(start = true)
    }

    private fun requestMicrophone(start: Boolean) {
        if (!resumed || permissionForVoice != null) return
        if (MicrophonePermission.granted(this)) {
            model.refreshPermission()
            if (start) model.startVoice()
            return
        }
        permissionForVoice = start
        microphoneLauncher.launch(MicrophonePermission.NAME)
    }
}
