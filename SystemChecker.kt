import android.content.Context
import android.util.Log

object SystemChecker {
    private const val TAG = "EmmanuilSystem"
    
    fun isSystemReady(context: Context): Boolean {
        try {
            // Проверка шифрования
            val testEncrypted = SpiritualCipher.encrypt("test")
            if (testEncrypted == "test") {
                Log.e(TAG, "Шифрование не работает")
                return false
            }
            
            // Проверка обнаружения зверя
            if (!BeastDetector().isBeastMessage("VGhpcyBpcyA2NjY=")) {
                Log.e(TAG, "Обнаружение зверя не работает (Base64)")
                return false
            }
            if (!BeastDetector().isBeastMessage("🜁")) {
                Log.e(TAG, "Обнаружение оккультных символов не работает")
                return false
            }
            
            // Проверка базы данных
            val db = MessageDatabase.getInstance(context)
            val lastMessages = db.messageDao().getLastMessages()
            if (lastMessages.isEmpty()) {
                Log.e(TAG, "База данных пуста")
                return false
            }
            
            // Проверка духовной чистоты
            if (lastMessages.none { it.text.contains("Бог с нами", ignoreCase = true) }) {
                Log.e(TAG, "Нет духовных сообщений")
                return false
            }
            
            // Проверка уникальности цитат
            val scriptureCount = lastMessages
                .map { SpiritualCipher.decrypt(it.text) }
                .filter { it.contains("Бог с нами") || it.contains("Христос") }
                .toSet()
                .size
            if (scriptureCount < 2) {
                Log.e(TAG, "Цитаты повторяются")
                return false
            }
            
            // ИОНА: Добавлена проверка минимального количества участников с уведомлением
            val emmanuilService = EmmanuilService(context)
            val activeParticipants = emmanuilService.getActiveParticipants().values.count { it }
            if (activeParticipants < 2) {
                Log.w(TAG, "Мало активных участников: ${activeParticipants}/3")
                EmergencyNotifier(context).sendAlert(
                    "Внимание! В Соборе мало участников: ${activeParticipants}/3"
                )
            }
            
            Log.d(TAG, "Система готова к работе")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки системы", e)
            return false
        }
    }
}
