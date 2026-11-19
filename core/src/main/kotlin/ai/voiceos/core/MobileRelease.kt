package ai.voiceos.core

import java.net.URI
import java.util.Locale

/** A site origin explicitly chosen by the app owner; never populated from an Intent or remote reply. */
@ConsistentCopyVisibility
data class SiteOrigin private constructor(val home: URI) {
    val releaseEndpoint: URI get() = home.resolve(RELEASE_ENDPOINT)
    fun download(release: AndroidRelease): URI = home.resolve(release.downloadPath)

    companion object {
        const val RELEASE_ENDPOINT = "/api/android/release"

        fun parse(raw: String): SiteOrigin? {
            val value = raw.trim()
            if (value.length > 300 || !value.startsWith("https://", ignoreCase = true)) return null
            return try {
                val uri = URI(value)
                val host = uri.host ?: return null
                if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
                    uri.rawPath !in listOf("", "/") || (uri.port != -1 && uri.port !in 1..65535)
                ) return null
                val port = if (uri.port == 443) -1 else uri.port
                SiteOrigin(URI("https", null, host.lowercase(Locale.ROOT), port, "/", null, null))
            } catch (_: java.net.URISyntaxException) { null }
        }
    }
}

enum class ReleaseDecision { CURRENT, DOWNLOAD, INCOMPATIBLE_ANDROID, DIFFERENT_SIGNATURE }

/** Metadata only. Android's package installer checks the actual package signature again. */
@ConsistentCopyVisibility
data class AndroidRelease private constructor(
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val sizeBytes: Int,
    val sha256: String,
    val signingSha256: String,
) {
    val downloadPath: String get() = path(versionCode)

    fun decision(installedCode: Int, sdk: Int, installedSigners: Set<String>): ReleaseDecision = when {
        versionCode <= installedCode -> ReleaseDecision.CURRENT
        minSdk > sdk -> ReleaseDecision.INCOMPATIBLE_ANDROID
        installedSigners != setOf(signingSha256) -> ReleaseDecision.DIFFERENT_SIGNATURE
        else -> ReleaseDecision.DOWNLOAD
    }

    companion object {
        const val APP_ID = "ai.voiceos"
        private val versionPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.+-]{0,31}")
        private val hashPattern = Regex("[0-9a-f]{64}")
        private fun path(code: Int) = "/downloads/voice-os-$code.apk"

        fun parse(
            schema: Int, applicationId: String, versionCode: Int, versionName: String,
            minSdk: Int, sizeBytes: Int, sha256: String, signingSha256: String, downloadPath: String,
        ): AndroidRelease? {
            if (schema != 1 || applicationId != APP_ID || versionCode !in 1..2_100_000_000 ||
                !versionPattern.matches(versionName) || minSdk !in 26..99 || sizeBytes !in 1024..(160 * 1024 * 1024) ||
                !hashPattern.matches(sha256) || !hashPattern.matches(signingSha256) || downloadPath != path(versionCode)
            ) return null
            return AndroidRelease(versionCode, versionName, minSdk, sizeBytes, sha256, signingSha256)
        }
    }
}
