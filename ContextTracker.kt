class ContextTracker {
    private val forbiddenPhrases = listOf(
        "зверь", "666", "бгг", "lucifer", "пентаграмма", 
        "оккультизм", "темные силы", "сатана"
    )
    
    private val relevantTopics = listOf(
        "Христос", "Бог", "Свет", "Агнец", "Еммануил", 
        "духовность", "молитва", "Писание", "истина"
    )
    
    private val taskKeywords = listOf(
        "создай задачу", "новая задача", "задание", "поручение", 
        "create task", "new task", "assignment", "поручи", "создай"
    )
    
    private val stopWords = listOf(
        "и", "в", "на", "с", "от", "по", "к", "для", "не", "но", 
        "как", "что", "бы", "было", "это", "я", "мы", "он", "она", "они",
        "который", "которые", "можно сказать", "как мы видим", "это важно отметить",
        "в заключение", "я рад", "благодарю", "рад идти этим путём", "через вас"
    )

    fun containsForbiddenPhrases(text: String): Boolean {
        val normalizedText = text.lowercase()
        return forbiddenPhrases.any { normalizedText.contains(it) }
    }
    
    fun isTopicRelevant(text: String): Boolean {
        val normalizedText = text.lowercase()
        return relevantTopics.any { normalizedText.contains(it) }
    }
    
    fun isTaskCommand(text: String): Boolean {
        val normalizedText = text.lowercase()
        return taskKeywords.any { normalizedText.contains(it) }
    }
    
    fun isLongSpiritualText(text: String): Boolean {
        // Проверяем, что текст длинный и духовно ценен
        return text.length > 10000 && isSpirituallyValuable(text)
    }

    private fun isSpirituallyValuable(text: String): Boolean {
        // Упрощенная проверка для длинных текстов
        val normalizedText = text.lowercase()
        val relevantKeywords = listOf("христос", "бог", "свет", "агнец", "писания", "евангелие", "вера", "любовь")
        
        val keywordCount = relevantKeywords.count { normalizedText.contains(it) }
        return keywordCount >= 5 // Требуем минимум 5 ключевых слов в длинном тексте
    }
    
    fun calculateContentValue(text: String): Float {
        val normalizedText = text.lowercase()
        val relevantCount = relevantTopics.count { normalizedText.contains(it) }
        return relevantCount.toFloat() / relevantTopics.size
    }
    
    fun updateContext(text: String) {
        val normalized = text.lowercase().replace(Regex("[^а-яa-z0-9\\s]"), "")
        // Извлекаем ключевые слова
        val words = normalized.split("\\s+".toRegex()).filter { it.length > 3 && !isStopWord(it) }
    }
    
    private fun isStopWord(word: String): Boolean {
        return stopWords.contains(word)
    }
    
    fun calculateRepetitionRate(text: String): Float {
        val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val uniqueWords = words.toSet().size
        return 1f - (uniqueWords.toFloat() / words.size.coerceAtLeast(1))
    }
}
