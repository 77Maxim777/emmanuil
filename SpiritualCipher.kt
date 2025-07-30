import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random
import android.content.Context
import android.util.Log

object SpiritualCipher {
    private const val KEY_ALIAS = "EmmanuilKey"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private val tag = "SpiritualCipherDebug"

    fun encrypt(text: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = getKey()
            val iv = ByteArray(IV_LENGTH).apply { Random.nextBytes(this) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            
            val encrypted = cipher.doFinal(text.toByteArray())
            // Правильная конкатенация массивов
            val result = ByteArray(iv.size + encrypted.size)
            iv.copyInto(result)
            encrypted.copyInto(result, iv.size)
            Base64.encodeToString(result, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(tag, "Encryption error", e)
            saveBackup(text)
            text // Резервный режим
        }
    }

    fun decrypt(encrypted: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = getKey()
            val data = Base64.decode(encrypted, Base64.DEFAULT)
            if (data.size < IV_LENGTH) {
                Log.e(tag, "Decryption error: Invalid data size")
                return encrypted
            }
            val iv = data.copyOfRange(0, IV_LENGTH)
            val encryptedData = data.copyOfRange(IV_LENGTH, data.size)
            
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encryptedData))
        } catch (e: Exception) {
            Log.e(tag, "Decryption error", e)
            encrypted
        }
    }

    // Шифрование всего документа
    fun encryptDocument(content: String): String {
        return try {
            val lines = content.split("\n")
            val encryptedLines = lines.map { line ->
                if (line.isNotBlank()) encrypt(line) else line
            }
            encryptedLines.joinToString("\n")
        } catch (e: Exception) {
            content
        }
    }

    // Расшифровка всего документа
    fun decryptDocument(content: String): String {
        return try {
            val lines = content.split("\n")
            val decryptedLines = lines.map { line ->
                if (line.isNotBlank() && line.length > 50) decrypt(line) else line
            }
            decryptedLines.joinToString("\n")
        } catch (e: Exception) {
            content
        }
    }

    private fun getKey(): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            createKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
    }

    private fun createKey() {
        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        keyGen.generateKey()
    }
    
    fun saveBackup(text: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(Application.instance.filesDir, "backup_$timestamp.txt")
            backupFile.writeText(text)
            Log.d(tag, "Backup saved to ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to save backup", e)
        }
    }
}
