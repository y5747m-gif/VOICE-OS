package ai.voiceos.site

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.voiceos.BuildConfig
import ai.voiceos.core.AndroidRelease
import ai.voiceos.core.ReleaseDecision
import ai.voiceos.core.SiteOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class SiteStatus { IDLE, CHECKING, NO_RELEASE, CURRENT, DOWNLOAD, INCOMPATIBLE, DIFFERENT_SIGNATURE, ERROR }
internal data class SiteState(val origin: SiteOrigin?, val status: SiteStatus = SiteStatus.IDLE, val release: AndroidRelease? = null)

/** Explicit network checks only. The local voice engine and on-device actions don't depend on this. */
internal class SiteViewModel(private val app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("voice_site", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SiteState(SiteOrigin.parse(preferences.getString("origin", null) ?: BuildConfig.DEFAULT_SITE_URL)))
    val state = mutable.asStateFlow()
    private var check: Job? = null
    private var generation = 0L

    fun setSite(origin: SiteOrigin?) {
        if (state.value.origin == origin) return
        generation++
        check?.cancel()
        preferences.edit().putString("origin", origin?.home?.toASCIIString().orEmpty()).apply()
        mutable.value = SiteState(origin)
    }

    fun checkRelease() {
        val origin = state.value.origin ?: return
        if (state.value.status == SiteStatus.CHECKING) return
        val ticket = ++generation
        mutable.value = SiteState(origin, SiteStatus.CHECKING)
        check = viewModelScope.launch {
            val next = try {
                val release = SiteReleaseClient.fetch(origin)
                if (release == null) SiteState(origin, SiteStatus.NO_RELEASE)
                else {
                    val signers = withContext(Dispatchers.IO) { app.appSigners() }
                    val status = when (release.decision(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT, signers)) {
                        ReleaseDecision.CURRENT -> SiteStatus.CURRENT
                        ReleaseDecision.DOWNLOAD -> SiteStatus.DOWNLOAD
                        ReleaseDecision.INCOMPATIBLE_ANDROID -> SiteStatus.INCOMPATIBLE
                        ReleaseDecision.DIFFERENT_SIGNATURE -> SiteStatus.DIFFERENT_SIGNATURE
                    }
                    SiteState(origin, status, release)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SiteState(origin, SiteStatus.ERROR)
            }
            if (ticket == generation) mutable.value = next
        }
    }

    fun cancelCheck() {
        if (state.value.status != SiteStatus.CHECKING) return
        generation++
        check?.cancel()
        mutable.update { SiteState(it.origin) }
    }
}
