package ai.voiceos.site

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import ai.voiceos.core.AndroidRelease
import ai.voiceos.core.SiteOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

internal object SiteReleaseClient {
    private const val LIMIT = 8192

    suspend fun fetch(site: SiteOrigin): AndroidRelease? = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + 12_000L
        val connection = (site.releaseEndpoint.toURL().openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 6_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Release endpoint unavailable")
            if (connection.contentLength > LIMIT || connection.contentType?.substringBefore(';') != "application/json") {
                throw IOException("Invalid release response")
            }
            val data = ByteArrayOutputStream()
            connection.inputStream.use { body ->
                val chunk = ByteArray(1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = body.read(chunk)
                    if (SystemClock.elapsedRealtime() > deadline) throw IOException("Release response timed out")
                    if (count < 0) break
                    if (data.size() + count > LIMIT) throw IOException("Release response too large")
                    data.write(chunk, 0, count)
                }
            }
            parseResponse(data.toString(Charsets.UTF_8.name()))
        } finally { connection.disconnect() }
    }

    private fun parseResponse(body: String): AndroidRelease? = try {
        val document = JSONObject(body)
        if (document.opt("schema") != 1 || !document.has("release")) throw IOException("Unknown release format")
        if (document.isNull("release")) null else {
            val item = document.getJSONObject("release")
            AndroidRelease.parse(
                schema = item.int("schema"), applicationId = item.string("applicationId"),
                versionCode = item.int("versionCode"), versionName = item.string("versionName"),
                minSdk = item.int("minSdk"), sizeBytes = item.int("sizeBytes"), sha256 = item.string("sha256"),
                signingSha256 = item.string("signingSha256"), downloadPath = item.string("downloadPath"),
            ) ?: throw IOException("Invalid release metadata")
        }
    } catch (error: JSONException) { throw IOException("Unreadable release metadata", error) }

    private fun JSONObject.int(key: String): Int = get(key) as? Int ?: throw IOException("Invalid release number")
    private fun JSONObject.string(key: String): String = get(key) as? String ?: throw IOException("Invalid release text")
}

@Suppress("DEPRECATION") // Android 8.x uses the older signature flag.
internal fun Context.appSigners(): Set<String> {
    val manager = packageManager
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
        manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") {
            String.format(Locale.ROOT, "%02x", it.toInt() and 0xff)
        }
    }
}
