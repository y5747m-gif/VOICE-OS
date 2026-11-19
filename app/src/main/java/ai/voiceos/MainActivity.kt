package ai.voiceos

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ai.voiceos.ai.Phase
import ai.voiceos.ai.SessionViewModel
import ai.voiceos.permissions.MicrophonePermission
import ai.voiceos.site.SiteViewModel
import ai.voiceos.ui.VoiceOsRoot

class MainActivity : ComponentActivity() {
    private val model by viewModels<SessionViewModel> { SessionViewModel.Factory(application as VoiceOsApplication) }
    private val site by viewModels<SiteViewModel>()
    private var resumed = false
    private var permissionForVoice: Boolean? = null
    private var listenOnResume = false
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

    override fun onResume() {
        super.onResume()
        resumed = true
        model.enterForeground(this)
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
