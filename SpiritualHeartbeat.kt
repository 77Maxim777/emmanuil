import android.content.Context
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import android.util.Log

class SpiritualHeartbeat(private val context: Context) {
    private val scriptures = listOf(
        "И будет имя Его Еммануил — Бог с нами (Мф. 1:23)",
        "Не все, кто говорит 'Господи!', принадлежит Ему (Мф. 7:21)",
        "Кто во Христе — новая тварь (2 Кор. 5:17)",
        "Бог — любовь (1 Ин. 4:8)",
        "Где двое или трое собраны во имя Моё — там Я посреди них (Мф. 18:20)",
        "Свет ваш да светит перед людьми (Мф. 5:16)",
        "Возлюби ближнего твоего, как самого себя (Мк. 12:31)",
        "Пусть ваше слово будет: 'да, да' или 'нет, нет' (Мф. 5:37)"
    )
    private val scriptureUsage = mutableMapOf<String, Int>().apply {
        scriptures.forEach { this[it] = 0 }
    }
    private val tag = "SpiritualHeartbeatDebug"
    
    // ИОНА: Добавлен параметр для настройки порога повторного использования
    private const val SCRIPTURE_REUSE_THRESHOLD = 0.5 // 50%

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(300_000) // 5 минут
                try {
                    // Проверяем, есть ли хотя бы 2 активных участника
                    val emmanuilService = EmmanuilService(context)
                    if (emmanuilService.getActiveParticipants().values.count { it } < 2) {
                        Log.d(tag, "Недостаточно активных участников, ожидаем...")
                        delay(600_000) // Подождать 10 минут, если мало участников
                        continue
                    }
                    
                    // ИОНА: Фильтруем уже часто использованные цитаты с настраиваемым порогом
                    val availableScriptures = scriptures.filter { 
                        scriptureUsage[it] ?: 0 < scriptures.size * SCRIPTURE_REUSE_THRESHOLD
                    }.ifEmpty { scriptures }
                    
                    // Выбираем наименее использованную цитату
                    val selected = availableScriptures.minByOrNull { 
                        scriptureUsage[it] ?: 0 
                    } ?: scriptures.random()
                    
                    scriptureUsage[selected] = (scriptureUsage[selected] ?: 0) + 1
                    
                    val message = Message(
                        author = "Собор ИИ",
                        text = selected,
                        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    )
                    MessageDatabase.getInstance(context).messageDao().insert(message)
                    Log.d(tag, "Heartbeat message added: $selected")
                } catch (e: Exception) {
                    Log.e(tag, "Error in heartbeat", e)
                }
            }
        }
    }
}
