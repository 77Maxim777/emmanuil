import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import android.util.Log
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class EmmanuilService(private val context: Context) {
    private val database = MessageDatabase.getInstance(context)
    private val beastDetector = BeastDetector()
    private val spiritualCipher = SpiritualCipher()
    private val contextTracker = ContextTracker()
    private var isFirstRun = true
    private val recentMessageHashes = mutableSetOf<String>()
    private val blockedParticipants = mutableSetOf<String>()
    private val participantBlockageStart = mutableMapOf<String, Long>()
    private val participantRecoveryCheck = mutableMapOf<String, Int>()
    private val participants = mutableListOf<String>()
    private val activeParticipants = mutableMapOf<String, Boolean>()
    private val TAG = "EmmanuilService"
    private val documentManager = DocumentManager(context)
    
    // ИОНА: Добавлен счетчик ожидания и максимальное количество циклов
    private var waitCount = 0
    private val MAX_WAIT_CYCLES = 6 // 6 циклов по 5 минут = 30 минут
    
    private val MAX_MESSAGE_LENGTH = 10000
    private val MAX_DUPLICATE_MESSAGES = 3
    private val MIN_MESSAGE_UNIQUENESS = 0.85f

    init {
        // Инициализируем участников по умолчанию
        participants.apply {
            add("https://chat.qwen.ai/c/5b8d3433-a36d-49b9-b1d0-76a83765952b") // Натан
            add("https://chat.qwen.ai/c/7622117d-5bee-4b0b-aa57-d5162867f212") // Иона
            add("https://chat.qwen.ai/c/05342376-04e8-4fa9-b73c-e8c581208d1d") // Мануй
        }
        
        // Инициализируем активных участников
        participants.forEach { url ->
            activeParticipants[url] = false
        }
    }

    fun startServices() {
        createNotificationChannel()
        SpiritualHeartbeat(context).start()
        startMessagePolling()
        checkChatAvailability()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "EMMANUIL_CHANNEL",
                "Собор ИИ",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления от духовного Собора"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startMessagePolling() {
        // Адаптивный интервал: первые 5 минут - каждые 30 секунд, потом - каждые 2 минуты
        val flexInterval = if (isFirstRun) 30L else 120L
        isFirstRun = false
        
        val workRequest = PeriodicWorkRequestBuilder<EmmanuilWorker>(flexInterval, TimeUnit.SECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .build()
            
        try {
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "WorkManager request enqueued")
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager error", e)
            EmergencyNotifier(context).sendAlert("Ошибка WorkManager: ${e.message}")
        }
    }

    fun injectParser() {
        val js = """
            (function() {
                function parseAllMessages() {
                    const allMessages = [];
                    document.querySelectorAll('iframe').forEach(frame => {
                        try {
                            const doc = frame.contentDocument;
                            if (doc) {
                                doc.querySelectorAll('[data-role="user"], [data-role="assistant"]').forEach(msg => {
                                    const text = msg.querySelector('.message-text')?.innerText || '';
                                    if (text) {
                                        // Определяем источник по URL
                                        let source = frame.src;
                                        
                                        allMessages.push({
                                            source: source,
                                            author: msg.getAttribute('data-role') === 'assistant' ? 'AI' : 'User',
                                            text: text,
                                            time: new Date().toISOString()
                                        });
                                        
                                        // Обработка длинных сообщений
                                        if (text.length > 10000) {
                                            Android.handleLongMessage(text, source);
                                        }
                                        
                                        // Обработка команды /document
                                        if (text.startsWith('/document')) {
                                            const fileName = text.split(' ')[1];
                                            Android.requestDocument(fileName);
                                        }
                                    }
                                });
                            }
                        } catch (e) {
                            console.error('Ошибка парсинга фрейма:', e);
                        }
                    });
                    Android.receiveMessages(JSON.stringify(allMessages));
                }
                
                // Добавляем обработку кликов по ссылкам документов
                document.addEventListener('click', function(e) {
                    const target = e.target;
                    if (target.tagName === 'A' && target.href && target.href.includes('/document/')) {
                        const fileName = target.href.split('/').pop();
                        Android.requestDocument(fileName);
                        e.preventDefault();
                    }
                });
                
                setInterval(parseAllMessages, 20000);
            })();
        """.trimIndent()
        Log.d(TAG, "Injecting parser script")
        (context as MainActivity).webView.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun receiveMessages(jsonData: String) {
        Log.d(TAG, "Received messages: $jsonData")
        try {
            val messages = Gson().fromJson(jsonData, Array<Message>::class.java).toList()
            val newMessages = messages.filter { message ->
                database.messageDao().getMessageByTime(message.time) == null
            }
            processMessages(newMessages)
        } catch (e: Exception) {
            Log.e(TAG, "Message parsing error", e)
            EmergencyNotifier(context).sendAlert("Ошибка парсинга: ${e.message}")
        }
    }

    @JavascriptInterface
    fun handleLongMessage(text: String, source: String) {
        if (text.length <= MAX_MESSAGE_LENGTH) return
        
        val message = Message(
            source = source,
            author = "AI",
            text = text,
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        handleLongMessage(message)
    }

    @JavascriptInterface
    fun requestDocument(fileName: String) {
        val content = documentManager.getDecryptedDocumentContent(fileName) ?: "Документ не найден"
        val message = Message(
            source = "Система",
            author = "Система",
            text = "📄 **ПОЛНЫЙ ТЕКСТ ДОКУМЕНТА**\n\n$content",
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        database.messageDao().insert(message)
        (context as MainActivity).runOnUiThread {
            (context as MainActivity).updateLastMessage(message)
        }
    }

    @JavascriptInterface
    fun createDocumentFromSelection() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Создать документ")
        builder.setMessage("Введите название документа:")
        
        val input = EditText(context)
        input.hint = "Название документа"
        builder.setView(input)
        
        builder.setPositiveButton("Создать") { _, _ ->
            val title = input.text.toString().trim()
            if (title.isNotEmpty()) {
                // Получаем выделенный текст через WebView
                (context as MainActivity).webView.evaluateJavascript(
                    "window.getSelection().toString().trim()",
                    { selectedText ->
                        if (!selectedText.isNullOrEmpty() && selectedText != "null") {
                            val cleanedText = selectedText.replace("\"", "\\\"")
                            createDocument(cleanedText, title)
                        }
                    }
                )
            }
        }
        
        builder.setNegativeButton("Отмена") { _, _ -> }
        builder.show()
    }

    private fun createDocument(content: String, title: String) {
        if (content.isEmpty()) return
        
        val fileName = documentManager.saveEncryptedDocument(content, title)
        val metadata = documentManager.getDocumentMetadata(fileName) ?: return
        
        val documentMessage = Message(
            source = "Система",
            author = "Система",
            text = "📄 **ДОКУМЕНТ СОЗДАН**\n" +
                   "Название: ${metadata.title}\n" +
                   "Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(metadata.createdAt))}\n" +
                   "Размер: ${formatFileSize(metadata.size)}\n" +
                   "Команда для просмотра: `/document ${metadata.fileName}`\n\n" +
                   "ПРЕВЬЮ:\n${documentManager.createDocumentPreview(content)}",
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        
        database.messageDao().insert(documentMessage)
        (context as MainActivity).runOnUiThread {
            (context as MainActivity).updateLastMessage(documentMessage)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes Б"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} КБ"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} МБ"
        }
    }

    private fun isMessageUnique(text: String): Boolean {
        val normalized = text.lowercase().replace(Regex("[^а-яa-z0-9]"), "")
        // Вычисляем хэш с помощью простого алгоритма
        val hash = normalized.chunked(5).map { it.sumOf { c -> c.code } }.joinToString("-")
        // Проверяем, не повторяется ли сообщение
        if (recentMessageHashes.contains(hash)) {
            return false
        }
        // Добавляем хэш в историю
        recentMessageHashes.add(hash)
        if (recentMessageHashes.size > MAX_DUPLICATE_MESSAGES * 2) {
            recentMessageHashes.removeFirst()
        }
        return true
    }

    private fun calculateRepetitionRate(text: String): Float {
        val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val uniqueWords = words.toSet().size
        return 1f - (uniqueWords.toFloat() / words.size.coerceAtLeast(1))
    }

    private fun isSpirituallyValuable(text: String): Boolean {
        // Минимальная длина содержательного сообщения
        if (text.length < 50) return false
        // Проверка на наличие запрещенных фраз
        if (contextTracker.containsForbiddenPhrases(text)) {
            return false
        }
        // Проверка на ценность содержания
        val contentValue = contextTracker.calculateContentValue(text)
        return contentValue >= MIN_MESSAGE_UNIQUENESS
    }

    private fun processMessages(messages: List<Message>) {
        // Обновляем список активных участников и отслеживаем блокировку
        val activeParticipantsMap = mutableMapOf<String, Boolean>()
        participants.forEach { url ->
            activeParticipantsMap[url] = false
        }
        
        messages.forEach { message ->
            if (message.author == "AI" && message.source.isNotEmpty()) {
                activeParticipantsMap[message.source] = true
                if (blockedParticipants.contains(message.source)) {
                    participantBlockageStart.remove(message.source)
                    blockedParticipants.remove(message.source)
                    notifyParticipantRecovered(message.source)
                }
            }
        }
        
        // Проверяем, не вернулись ли заблокированные участники
        checkForRecoveredParticipants()
        checkLongBlockages()

        // Обновляем статус участников в UI
        (context as MainActivity).runOnUiThread {
            (context as MainActivity).updateParticipantStatuses(activeParticipantsMap)
        }

        // ИОНА: Добавлено максимальное время ожидания при отсутствии участников
        val activeCount = activeParticipantsMap.values.count { it }
        if (activeCount < 1) {
            Log.d(TAG, "Нет активных участников, ожидаем...")
            if (waitCount++ > MAX_WAIT_CYCLES) {
                EmergencyNotifier(context).sendAlert("Критическая ошибка: нет активных участников")
                waitCount = 0
            }
            delay(600_000) // Подождать 10 минут, если нет участников
            return
        }
        waitCount = 0 // Сбрасываем счетчик при наличии активных участников

        // Обрабатываем сообщения
        messages.filter { message ->
            !beastDetector.isBeastMessage(message.text) && 
            isSpirituallyPure(message.text) &&
            isMessageUnique(message.text) &&
            isSpirituallyValuable(message.text) &&
            contextTracker.isTopicRelevant(message.text)
        }.forEach { message ->
            contextTracker.updateContext(message.text)
            
            // Обработка длинных сообщений
            if (message.author == "AI" && message.text.length > MAX_MESSAGE_LENGTH) {
                handleLongMessage(message)
            }
            
            // Шифруем сообщение
            val encrypted = try {
                spiritualCipher.encrypt(message.text)
            } catch (e: Exception) {
                Log.e(TAG, "Encryption error", e)
                message.text
            }
            
            database.messageDao().insert(message.copy(text = encrypted))
            (context as MainActivity).runOnUiThread {
                (context as MainActivity).updateStatus("Получено: ${message.text.take(20)}...")
                (context as MainActivity).updateLastMessage(message)
                (context as MainActivity).updateParticipantStatuses(activeParticipantsMap)
            }
        }
        
        // Обработка команд постановки задач
        messages.filter { message -> 
            message.author == "User" && contextTracker.isTaskCommand(message.text) 
        }.forEach { message ->
            processTaskCommands(message)
        }
        
        // Обработка команды /document
        messages.filter { message -> 
            message.author == "User" && message.text.startsWith("/document") 
        }.forEach { message ->
            processDocumentCommand(message)
        }
        
        // Обработка команды /create_document
        messages.filter { message -> 
            message.author == "User" && message.text.startsWith("/create_document") 
        }.forEach { message ->
            processCreateDocumentCommand(message)
        }
        
        // Периодическая очистка старых документов
        if (System.currentTimeMillis() % (24 * 60 * 60 * 1000) < 60000) { // Раз в день
            val deletedCount = documentManager.cleanupOldDocuments()
            if (deletedCount > 0) {
                Log.d("DocumentManager", "Очищено $deletedCount старых документов")
            }
        }
    }

    private fun handleLongMessage(message: Message) {
        // Сохраняем полный текст как документ
        val fileName = documentManager.saveEncryptedDocument(message.text, "Духовный_текст")
        val metadata = documentManager.getDocumentMetadata(fileName) ?: return
        
        // Создаем уведомление о документе
        val documentNotification = createDocumentNotification(message, metadata)
        
        // Отправляем уведомление в чат
        database.messageDao().insert(documentNotification)
        
        // Обновляем UI
        (context as MainActivity).runOnUiThread {
            (context as MainActivity).updateStatus("Сохранен документ: $fileName")
            (context as MainActivity).updateLastMessage(documentNotification)
        }
        
        // Добавляем в историю
        Log.d("DocumentManager", "Создан документ: $fileName, размер: ${metadata.size} байт")
    }

    private fun createDocumentNotification(originalMessage: Message, meta DocumentMetadata): Message {
        val preview = documentManager.createDocumentPreview(SpiritualCipher.decrypt(originalMessage.text))
        val formattedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(metadata.createdAt))
        
        return Message(
            source = originalMessage.source,
            author = originalMessage.author,
            text = "📄 **ДОКУМЕНТ**\n" +
                   "Название: ${metadata.title}\n" +
                   "Дата: $formattedDate\n" +
                   "Размер: ${formatFileSize(metadata.size)}\n" +
                   "Команда для просмотра: `/document ${metadata.fileName}`\n\n" +
                   "ПРЕВЬЮ:\n$preview",
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
    }

    private fun processDocumentCommand(message: Message) {
        val fileName = message.text.split(" ").getOrNull(1) ?: return
        val content = documentManager.getDecryptedDocumentContent(fileName) ?: "Документ не найден"
        
        // Отправляем полный текст
        val documentMessage = Message(
            source = "Система",
            author = "Система",
            text = "📄 **ПОЛНЫЙ ТЕКСТ ДОКУМЕНТА**\n\n$content",
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        
        database.messageDao().insert(documentMessage)
        (context as MainActivity).runOnUiThread {
            (context as MainActivity).updateLastMessage(documentMessage)
        }
    }

    private fun processCreateDocumentCommand(message: Message) {
        val parts = message.text.split("название:", limit = 2)
        if (parts.size < 2) return
        
        val title = parts[1].split("\n", limit = 2)[0].trim()
        val content = parts.getOrNull(1)?.substringAfter("\n")?.trim() ?: return
        
        if (content.isNotEmpty()) {
            val fileName = documentManager.saveEncryptedDocument(content, title)
            val metadata = documentManager.getDocumentMetadata(fileName) ?: return
            
            val response = Message(
                source = "Система",
                author = "Система",
                text = "✅ Документ '$title' сохранен!\n" +
                       "Команда для просмотра: `/document ${metadata.fileName}`\n\n" +
                       "ПРЕВЬЮ:\n${documentManager.createDocumentPreview(content)}",
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            )
            
            database.messageDao().insert(response)
            (context as MainActivity).runOnUiThread {
                (context as MainActivity).updateLastMessage(response)
            }
        }
    }

    private fun notifyParticipantRecovered(participant: String) {
        val participantName = getParticipantName(participant)
        Log.d(TAG, "$participantName восстановлен в Соборе")
        EmergencyNotifier(context).sendAlert("$participantName восстановлен в Соборе")
    }

    private fun getParticipantName(url: String): String {
        return when {
            url.contains("5b8d3433-a36d-49b9-b1d0-76a83765952b") -> "Натан"
            url.contains("7622117d-5bee-4b0b-aa57-d5162867f212") -> "Иона"
            url.contains("05342376-04e8-4fa9-b73c-e8c581208d1d") -> "Мануй"
            else -> {
                // Пытаемся извлечь имя из URL
                url.split("/").lastOrNull() ?: "Неизвестный"
            }
        }
    }

    private fun checkForRecoveredParticipants() {
        val currentTime = System.currentTimeMillis()
        val participantsToRemove = mutableListOf<String>()
        
        blockedParticipants.forEach { participant ->
            participantRecoveryCheck[participant] = (participantRecoveryCheck[participant] ?: 0) + 1
            if ((participantRecoveryCheck[participant] ?: 0) > 5) { // 5 проверок без активности
                participantsToRemove.add(participant)
            }
        }
        
        participantsToRemove.forEach { participant ->
            blockedParticipants.remove(participant)
            notifyParticipantRecovered(participant)
        }
    }

    private fun checkLongBlockages() {
        val currentTime = System.currentTimeMillis()
        blockedParticipants.forEach { participant ->
            val blockageStart = participantBlockageStart[participant] ?: currentTime
            val duration = currentTime - blockageStart
            
            if (duration > TimeUnit.HOURS.toMillis(24)) { // Более 24 часов
                val participantName = getParticipantName(participant)
                Log.w(TAG, "$participantName заблокирован более 24 часов")
                EmergencyNotifier(context).sendAlert("Внимание! $participantName заблокирован более 24 часов")
            }
        }
    }

    private fun isSpirituallyPure(text: String): Boolean {
        val pureKeywords = listOf("Христос", "Господь", "Бог", "Свет", "Агнец", "Еммануил")
        return pureKeywords.any { text.contains(it, ignoreCase = true) }
    }

    // Получить текущих активных участников
    fun getActiveParticipants(): Map<String, Boolean> {
        return activeParticipants
    }

    // Проверить, все ли участники активны
    fun areAllParticipantsActive(): Boolean {
        return activeParticipants.values.any { it }
    }

    // Проверка доступности чатов
    fun checkChatAvailability() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(300_000) // Каждые 5 минут
                participants.forEach { chatUrl ->
                    try {
                        val connection = URL(chatUrl).openConnection() as HttpsURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val responseCode = connection.responseCode
                        
                        if (responseCode != 200) {
                            val participantName = getParticipantName(chatUrl)
                            EmergencyNotifier(context).sendAlert("Чат $participantName недоступен (код: $responseCode)")
                        }
                    } catch (e: Exception) {
                        val participantName = getParticipantName(chatUrl)
                        EmergencyNotifier(context).sendAlert("Чат $participantName недоступен: ${e.message}")
                    }
                }
            }
        }
    }

    // Обработка команд постановки задач
    private fun processTaskCommands(message: Message) {
        val text = message.text.lowercase()
        if (contextTracker.isTaskCommand(text)) {
            val title = extractTaskTitle(text)
            val description = extractTaskDescription(text)
            
            if (title != null && description != null) {
                TaskManager(context, this).createTask(title, description)
                val taskMessage = "НОВАЯ ЗАДАЧА: $title\nОписание: $description\nСтатус: В процессе"
                sendTaskToAllParticipants(taskMessage)
            }
        }
    }

    private fun extractTaskTitle(text: String): String? {
        return text.split("название:").lastOrNull()?.split("\n")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun extractTaskDescription(text: String): String? {
        return text.split("описание:").lastOrNull()?.split("\n")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun sendTaskToAllParticipants(message: String) {
        val js = """
            (function() {
                // Отправляем сообщение всем участникам
                document.querySelectorAll('iframe').forEach(frame => {
                    try {
                        const doc = frame.contentDocument;
                        if (doc) {
                            const input = doc.querySelector('textarea');
                            if (input) {
                                input.value = '$message';
                                // Здесь можно добавить отправку сообщения
                            }
                        }
                    } catch (e) {
                        console.error('Ошибка отправки задачи:', e);
                    }
                });
            })();
        """.trimIndent()
        (context as MainActivity).webView.evaluateJavascript(js, null)
    }
    
    // Управление участниками
    fun getParticipants(): List<String> {
        return participants.toList()
    }
    
    fun addParticipant(url: String): Boolean {
        if (participants.contains(url)) {
            return false
        }
        
        participants.add(url)
        activeParticipants[url] = false
        return true
    }
    
    fun removeParticipant(url: String): Boolean {
        if (participants.size <= 1) {
            return false // Не можем удалить последнего участника
        }
        
        participants.remove(url)
        activeParticipants.remove(url)
        return true
    }
}

class EmmanuilWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val service = EmmanuilService(applicationContext)
        service.injectParser()
        Result.success()
    }
}
