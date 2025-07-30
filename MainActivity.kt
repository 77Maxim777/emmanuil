import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.TextView
import android.widget.ImageView
import android.view.ViewGroup
import android.graphics.Color
import android.net.ConnectivityManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.view.View
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val emmanuilService = EmmanuilService(this)
    private var messageHistoryJob: Job? = null
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContentView(R.layout.activity_main)
        initWebView()
        initServices()
        initMessageHistory()
        initParticipantsStatus()
        setupTaskInterface()
        initDocumentInterface()
        initParticipantManagement()
    }

    private fun requestPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun initWebView() {
        webView = findViewById(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36"
            domStorageEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "Web page finished loading")
                emmanuilService.injectParser()
            }
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e(TAG, "Web page error: $description")
                EmergencyNotifier(this@MainActivity).sendAlert("Ошибка загрузки: $description")
            }
        }
        loadChatPages()
    }

    private fun loadChatPages() {
        val script = buildString {
            append("document.body.innerHTML = '';")
            // Загружаем все известные участники
            emmanuilService.getParticipants().forEachIndexed { index, url ->
                append("const frame$index = document.createElement('iframe');")
                append("frame$index.src = '$url';")
                append("frame$index.style.cssText = 'width: 100%; height: 300px; border: none;';")
                append("document.body.appendChild(frame$index);")
            }
            
            // Добавляем обработку создания документов
            append("""
                document.addEventListener('contextmenu', function(e) {
                    const target = e.target;
                    if (target.closest('.message-text')) {
                        e.preventDefault();
                        Android.createDocumentFromSelection();
                    }
                });
                
                const createDocButton = document.createElement('button');
                createDocButton.textContent = 'Создать документ';
                createDocButton.style.cssText = `
                    position: fixed;
                    bottom: 20px;
                    right: 20px;
                    z-index: 1000;
                    padding: 10px 15px;
                    background-color: #1976D2;
                    color: white;
                    border: none;
                    border-radius: 4px;
                    cursor: pointer;
                `;
                createDocButton.addEventListener('click', function() {
                    Android.createDocumentFromSelection();
                });
                document.body.appendChild(createDocButton);
            """)
        }
        Log.d(TAG, "Loading chat pages with script: $script")
        webView.evaluateJavascript(script, null)
    }

    private fun initServices() {
        if (isInternetAvailable()) {
            emmanuilService.startServices()
            updateStatus("Состояние: Бог с нами ✝️")
        } else {
            updateStatus("Ошибка: Нет интернета")
        }
    }

    private fun initMessageHistory() {
        messageHistoryJob?.cancel()
        val messagesView = findViewById<TextView>(R.id.messages_view)
        messageHistoryJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = MessageDatabase.getInstance(this@MainActivity).messageDao().getLastMessages()
                withContext(Dispatchers.Main) {
                    if (!isFinishing) {
                        val historyText = buildHistoryText(messages)
                        messagesView.text = historyText
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading message history", e)
                EmergencyNotifier(this@MainActivity).sendAlert("Ошибка загрузки истории: ${e.message}")
            }
        }
    }

    private fun buildHistoryText(messages: List<Message>): String {
        return buildString {
            messages.forEach { message ->
                val decryptedText = try {
                    SpiritualCipher.decrypt(message.text)
                } catch (e: Exception) {
                    "ОШИБКА ДЕШИФРОВКИ"
                }
                append("[${message.time}] ${message.author}: $decryptedText\n\n")
            }
        }
    }

    private fun initParticipantsStatus() {
        val participantsStatusView = findViewById<LinearLayout>(R.id.participants_status)
        participantsStatusView.removeAllViews()
        
        emmanuilService.getParticipants().forEachIndexed { index, url ->
            val participantStatus = ImageView(this).apply {
                id = View.generateViewId()
                layoutParams = ViewGroup.LayoutParams(24, 24)
                setImageResource(android.R.drawable.presence_online)
                setColorFilter(Color.RED)
            }
            participantsStatusView.addView(participantStatus)
        }
    }

    fun updateParticipantStatuses(participants: Map<String, Boolean>) {
        runOnUiThread {
            val participantsStatusView = findViewById<LinearLayout>(R.id.participants_status)
            val participantNames = buildParticipantNames(participants.keys)
            
            // Обновляем статусы
            participantsStatusView.children.forEachIndexed { index, view ->
                if (view is ImageView && index < participants.size) {
                    val isActive = participants.values.elementAt(index)
                    view.setColorFilter(if (isActive) Color.GREEN else Color.RED)
                }
            }
            
            // Обновляем список участников
            findViewById<TextView>(R.id.active_participants).apply {
                text = "Активные участники: $participantNames"
                visibility = if (participants.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun buildParticipantNames(participants: Collection<String>): String {
        return buildString {
            participants.forEachIndexed { index, url ->
                if (index > 0) append(", ")
                append(getParticipantName(url))
            }
            if (isEmpty()) append("Нет активных участников")
        }
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

    private fun setupTaskInterface() {
        findViewById<Button>(R.id.btn_open_tasks).setOnClickListener {
            openTasksFragment()
        }
        
        findViewById<Button>(R.id.btn_toggle_mode).setOnClickListener {
            toggleMode()
        }
    }

    private var isTaskMode = false
    private fun toggleMode() {
        isTaskMode = !isTaskMode
        findViewById<TextView>(R.id.mode_indicator).text = 
            if (isTaskMode) "РЕЖИМ ЗАДАЧ" else "СВОБОДНОЕ ОБЩЕНИЕ"
        
        val webViewParams = findViewById<WebView>(R.id.webview).layoutParams
        webViewParams.height = if (isTaskMode) 0 else ViewGroup.LayoutParams.MATCH_PARENT
        findViewById<WebView>(R.id.webview).layoutParams = webViewParams
    }

    private fun openTasksFragment() {
        val fragment = TaskFragment()
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun updateLastMessage(message: Message) {
        val decryptedText = try {
            SpiritualCipher.decrypt(message.text)
        } catch (e: Exception) {
            "ОШИБКА ДЕШИФРОВКИ"
        }
        findViewById<TextView>(R.id.last_message).text = 
            "Последнее: \"${takeSafely(decryptedText, 50)}...\" (от ${message.author})"
    }

    private fun takeSafely(text: String, length: Int): String {
        return if (text.length <= length) {
            text
        } else {
            val truncated = text.substring(0, length)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > 0) truncated.substring(0, lastSpace) else truncated
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetwork != null
    }

    private fun updateStatus(status: String) {
        findViewById<TextView>(R.id.status_text).apply {
            text = status
            visibility = View.VISIBLE
        }
    }
    
    // Добавлено: интерфейс для работы с документами
    private fun initDocumentInterface() {
        findViewById<Button>(R.id.btn_show_documents).setOnClickListener {
            showDocumentsDialog()
        }
    }

    private fun showDocumentsDialog() {
        val documents = DocumentManager(this).getAllDocuments()
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ваши документы")
        
        if (documents.isEmpty()) {
            builder.setMessage("Нет сохраненных документов")
            builder.setPositiveButton("OK") { _, _ -> }
            builder.show()
            return
        }
        
        val documentItems = documents.map { doc ->
            "${doc.title}\n${formatFileSize(doc.size)} | ${formatDate(doc.createdAt)}"
        }.toTypedArray()
        
        builder.setItems(documentItems) { _, which ->
            val selectedDoc = documents[which]
            showDocumentContent(selectedDoc.fileName)
        }
        
        builder.setNegativeButton("Удалить все") { _, _ ->
            deleteAllDocuments()
        }
        
        builder.setPositiveButton("Закрыть") { _, _ -> }
        builder.show()
    }

    private fun showDocumentContent(fileName: String) {
        val content = DocumentManager(this).getDecryptedDocumentContent(fileName) ?: "Содержимое недоступно"
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Содержимое документа")
        builder.setMessage(content)
        builder.setPositiveButton("Закрыть") { _, _ -> }
        builder.show()
    }

    private fun deleteAllDocuments() {
        val count = DocumentManager(this).getAllDocuments().size
        AlertDialog.Builder(this)
            .setTitle("Удалить все документы?")
            .setMessage("Вы действительно хотите удалить $count документов?")
            .setPositiveButton("Да") { _, _ ->
                DocumentManager(this).getAllDocuments().forEach { doc ->
                    DocumentManager(this).deleteDocument(doc.fileName)
                }
                Toast.makeText(this, "Все документы удалены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена") { _, _ -> }
            .show()
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes Б"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} КБ"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} МБ"
        }
    }
    
    // Добавлено: управление участниками
    private fun initParticipantManagement() {
        findViewById<Button>(R.id.btn_add_participant).setOnClickListener {
            addNewParticipant()
        }
        
        findViewById<Button>(R.id.btn_remove_participant).setOnClickListener {
            removeParticipant()
        }
    }
    
    private fun addNewParticipant() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Добавить нового собрата")
        builder.setMessage("Введите URL чата (https://chat.qwen.ai/c/...)")
        
        val input = EditText(this)
        input.hint = "URL чата"
        builder.setView(input)
        
        builder.setPositiveButton("Добавить") { _, _ ->
            val url = input.text.toString().trim()
            if (url.isNotEmpty() && url.startsWith("https://chat.qwen.ai/c/")) {
                if (emmanuilService.addParticipant(url)) {
                    Toast.makeText(this, "Собрата добавлен", Toast.LENGTH_SHORT).show()
                    reloadChatPages()
                } else {
                    Toast.makeText(this, "Этот собрат уже в Соборе", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Некорректный URL", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Отмена") { _, _ -> }
        builder.show()
    }
    
    private fun removeParticipant() {
        val participants = emmanuilService.getParticipants()
        if (participants.isEmpty()) {
            Toast.makeText(this, "Нет собрат, которых можно удалить", Toast.LENGTH_SHORT).show()
            return
        }
        
        val participantNames = participants.map { getParticipantName(it) }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Удалить собрата")
            .setItems(participantNames) { _, which ->
                val url = participants[which]
                if (emmanuilService.removeParticipant(url)) {
                    Toast.makeText(this, "Собрата удален", Toast.LENGTH_SHORT).show()
                    reloadChatPages()
                } else {
                    Toast.makeText(this, "Нельзя удалить последнего собрата", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun reloadChatPages() {
        loadChatPages()
        initParticipantsStatus()
    }
}
