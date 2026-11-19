package ai.voiceos.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only an explicitly submitted display name is kept. Keystore keys never leave Android. */
internal class NicknameStore(context: Context) {
    private val prefs = context.getSharedPreferences("personal_memory", Context.MODE_PRIVATE)

    suspend fun read(): Result<String?> = onDisk {
        val encoded = prefs.getString(FIELD, null) ?: return@onDisk null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyStore().getKey(ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Keystore key unavailable"), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
    }

    suspend fun save(name: String): Result<Unit> = onDisk {
        require(isValidName(name))
        val key = (keyStore().getKey(ALIAS, null) as? SecretKey) ?: generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_SIZE)
        val payload = Base64.encodeToString(cipher.iv + cipher.doFinal(name.encodeToByteArray()), Base64.NO_WRAP)
        if (!prefs.edit().putString(FIELD, payload).commit()) throw IOException("Local write failed")
    }

    suspend fun remove(): Result<Unit> = onDisk {
        // Remove the key as well; any old encrypted payload is no longer decryptable.
        keyStore().deleteEntry(ALIAS)
        if (!prefs.edit().remove(FIELD).commit()) throw IOException("Local delete failed")
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        generateKey()
    }

    private suspend fun <T> onDisk(work: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(work())
        } catch (error: GeneralSecurityException) {
            Result.failure(error)
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        } catch (error: IllegalStateException) {
            Result.failure(error)
        } catch (error: java.security.ProviderException) {
            Result.failure(error)
        } catch (error: SecurityException) {
            Result.failure(error)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 32
        private const val FIELD = "display_name"
        private const val ALIAS = "voice_os_display_name_v1"
        private const val IV_SIZE = 12
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        fun isValidName(name: String): Boolean = name.isNotBlank() && name.length <= MAX_NAME_LENGTH &&
            name.none { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }
    }
}
