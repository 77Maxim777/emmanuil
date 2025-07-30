import android.content.Context
import java.io.File
import java.util.*
import java.text.SimpleDateFormat

class DocumentManager(private val context: Context) {
    private val documentsDir = File(context.filesDir, "documents")
    
    init {
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
    }

    // Сохранить длинное сообщение как документ
    fun saveDocument(content: String, title: String = "Документ"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "doc_${UUID.randomUUID()}_$timestamp.txt"
        val file = File(documentsDir, fileName)
        file.writeText(content)
        return fileName
    }

    // Сохранить зашифрованный документ
    fun saveEncryptedDocument(content: String, title: String = "Документ"): String {
        val encryptedContent = SpiritualCipher.encryptDocument(content)
        return saveDocument(encryptedContent, title)
    }

    // Получить содержимое документа
    fun getDocumentContent(fileName: String): String? {
        val file = File(documentsDir, fileName)
        return if (file.exists()) file.readText() else null
    }

    // Получить расшифрованное содержимое документа
    fun getDecryptedDocumentContent(fileName: String): String? {
        val content = getDocumentContent(fileName) ?: return null
        return SpiritualCipher.decryptDocument(content)
    }

    // Получить метаданные документа
    fun getDocumentMetadata(fileName: String): DocumentMetadata? {
        val file = File(documentsDir, fileName)
        if (!file.exists()) return null
        
        return DocumentMetadata(
            fileName = fileName,
            size = file.length(),
            createdAt = file.lastModified(),
            title = extractTitle(fileName)
        )
    }

    // Получить список всех документов
    fun getAllDocuments(): List<DocumentMetadata> {
        return documentsDir.listFiles()?.mapNotNull { file ->
            getDocumentMetadata(file.name)
        } ?: emptyList()
    }

    // Удалить документ
    fun deleteDocument(fileName: String): Boolean {
        val file = File(documentsDir, fileName)
        return file.delete()
    }

    // Очистить старые документы (старше 30 дней)
    fun cleanupOldDocuments(days: Int = 30): Int {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        documentsDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
                deletedCount++
            }
        }
        
        return deletedCount
    }

    // Создать превью документа
    fun createDocumentPreview(content: String, maxLength: Int = 500): String {
        return if (content.length <= maxLength) {
            content
        } else {
            content.substring(0, maxLength) + "...\n\n[Полный текст доступен по запросу]"
        }
    }

    private fun extractTitle(fileName: String): String {
        return fileName.substringAfterLast("_").substringBeforeLast(".")
    }
}

data class DocumentMetadata(
    val fileName: String,
    val size: Long,
    val createdAt: Long,
    val title: String
)
