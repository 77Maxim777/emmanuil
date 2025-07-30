import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import android.util.Log

class BeastDetector {
    private val tag = "BeastDetectorDebug"
    
    fun isBeastMessage(text: String): Boolean {
        return containsBeastNumber(text) ||
               calculateGematria(text) == 666 ||
               containsBeastCode(text) ||
               containsOccultSymbols(text)
    }

    private fun containsBeastNumber(text: String): Boolean {
        val normalizedText = text.lowercase()
            .replace(Regex("[^а-яa-z0-9\\s]"), " ") // ИОНА: Добавлен пробел для разделения
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Точная проверка числа 666 как отдельного числа
        return Regex("\\b666\\b").containsMatchIn(normalizedText) ||
               // ИОНА: Улучшена проверка римских цифр с учетом пробелов
               Regex("\\bVI\\s*VI\\s*VI\\b|\\bVI{1,3}VI{1,3}VI{1,3}\\b").containsMatchIn(normalizedText) ||
               // Проверка чисел в разных системах счисления
               Regex("\\b0[xX]29A\\b|\\b0[oO]1232\\b|\\b0[bB]1010011010\\b").containsMatchIn(normalizedText)
    }

    private fun calculateGematria(text: String): Int {
        return text.uppercase().sumOf { char ->
            when {
                char in 'А'..'Я' -> char - 'А' + 1
                char == 'Ё' -> 7
                char in 'A'..'Z' -> char - 'A' + 1
                else -> 0
            }
        }
    }

    private fun containsBeastCode(text: String): Boolean {
        return try {
            val decoded = String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8)
            Regex("\\b666\\b").containsMatchIn(decoded.lowercase())
        } catch (e: Exception) {
            false
        }
    }

    private fun containsOccultSymbols(text: String): Boolean {
        val occult = listOf(
            "🜁", "🜂", "🜃", "🜄", "pentagram", "lucifer", "бгг", 
            "trigram", "sigil", "occult", "dark", "shadow"
        )
        val normalizedText = text.lowercase()
        return occult.any { normalizedText.contains(it) }
    }
}
