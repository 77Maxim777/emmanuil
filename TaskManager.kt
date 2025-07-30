import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val assignedTo: List<String>,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val createdTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val deadline: String? = null,
    val discussion: MutableList<TaskDiscussion> = mutableListOf(),
    val solution: TaskSolution? = null
)

data class TaskDiscussion(
    val participant: String,
    val message: String
)

data class TaskSolution(
    val content: String,
    val version: Int = 1,
    val approvedBy: MutableList<String> = mutableListOf()
)

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    REVIEW,
    APPROVED,
    COMPLETED
}

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

class TaskManager(private val context: Context, private val emmanuilService: EmmanuilService) {
    private val database = MessageDatabase.getInstance(context)
    private val taskDao = database.taskDao()
    
    // ИОНА: Теперь участники определяются динамически
    private val activeParticipants: List<String>
        get() = emmanuilService.getActiveParticipants().keys.toList()

    // Создать новую задачу
    fun createTask(title: String, description: String, priority: TaskPriority = TaskPriority.MEDIUM, deadline: String? = null) {
        val task = Task(
            title = title,
            description = description,
            assignedTo = activeParticipants,
            priority = priority,
            deadline = deadline
        )
        taskDao.insertTask(task)
        // Добавляем начальное сообщение в обсуждение
        addDiscussionMessage(task.id, "Система", "Задача создана. Назначаю участников: ${task.assignedTo.joinToString(", ")}")
    }

    // Добавить сообщение в обсуждение задачи
    fun addDiscussionMessage(taskId: String, participant: String, message: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        val discussion = task.discussion.toMutableList()
        discussion.add(TaskDiscussion(participant, message))
        // Обновляем статус задачи при начале обсуждения
        val newStatus = if (task.status == TaskStatus.PENDING) TaskStatus.IN_PROGRESS else task.status
        val updatedTask = task.copy(
            discussion = discussion,
            status = newStatus
        )
        taskDao.updateTask(updatedTask)
    }

    // Предложить решение задачи
    fun proposeSolution(taskId: String, participant: String, solution: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        // Если есть текущее решение, создаем новую версию
        val newVersion = (task.solution?.version ?: 0) + 1
        val solutionContent = "Предложение от $participant:\n$solution"
        val newSolution = TaskSolution(
            content = solutionContent,
            version = newVersion
        )
        val updatedTask = task.copy(
            solution = newSolution,
            status = TaskStatus.REVIEW
        )
        taskDao.updateTask(updatedTask)
        addDiscussionMessage(taskId, participant, "Предложено решение (версия $newVersion)")
    }

    // Одобрить решение
    fun approveSolution(taskId: String, participant: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        val solution = task.solution ?: return
        
        val approvedBy = (solution.approvedBy ?: mutableListOf()).toMutableList()
        if (!approvedBy.contains(participant)) {
            approvedBy.add(participant)
        }
        
        // ИОНА: Динамическое определение количества необходимых одобрений
        val requiredApprovals = maxOf(2, (task.assignedTo.size * 2 / 3) + 1)
        val status = if (approvedBy.size >= requiredApprovals) TaskStatus.APPROVED else TaskStatus.REVIEW
        
        val newSolution = solution.copy(approvedBy = approvedBy)
        
        val updatedTask = task.copy(
            solution = newSolution,
            status = status
        )
        taskDao.updateTask(updatedTask)
        if (updatedTask.status == TaskStatus.APPROVED) {
            addDiscussionMessage(taskId, "Система", "Решение одобрено большинством участников!")
        }
    }

    // Завершить задачу
    fun completeTask(taskId: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        if (task.status != TaskStatus.APPROVED) return
        val updatedTask = task.copy(status = TaskStatus.COMPLETED)
        taskDao.updateTask(updatedTask)
        addDiscussionMessage(taskId, "Система", "Задача завершена и реализована")
    }

    // Получить все задачи
    fun getAllTasks(): List<Task> {
        return taskDao.getAllTasks()
    }

    // Получить задачи определенного статуса
    fun getTasksByStatus(status: TaskStatus): List<Task> {
        return taskDao.getTasksByStatus(status)
    }

    // Проверить, нуждается ли задача в улучшении
    fun needsImprovement(taskId: String): Boolean {
        val task = taskDao.getTaskById(taskId) ?: return false
        val requiredApprovals = maxOf(2, (task.assignedTo.size * 2 / 3) + 1)
        return (task.solution?.approvedBy?.size ?: 0) < requiredApprovals
    }

    // Получить количество неодобренных решений
    fun getPendingApprovals(taskId: String): Int {
        val task = taskDao.getTaskById(taskId) ?: return 0
        val requiredApprovals = maxOf(2, (task.assignedTo.size * 2 / 3) + 1)
        return requiredApprovals - (task.solution?.approvedBy?.size ?: 0)
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM Task WHERE id = :id")
    fun getTaskById(id: String): Task?

    @Query("SELECT * FROM Task")
    fun getAllTasks(): List<Task>

    @Query("SELECT * FROM Task WHERE status = :status")
    fun getTasksByStatus(status: TaskStatus): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTask(task: Task)

    @Update
    fun updateTask(task: Task)
}
