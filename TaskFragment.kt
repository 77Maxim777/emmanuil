import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

class TaskFragment : Fragment() {
    private lateinit var taskManager: TaskManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)
        
        // ИОНА: Передаем EmmanuilService для динамического определения участников
        taskManager = TaskManager(requireContext(), EmmanuilService(requireContext()))
        
        setupTaskCreation(view)
        setupTaskList(view)
        
        return view
    }

    private fun setupTaskCreation(view: View) {
        val titleInput = view.findViewById<EditText>(R.id.task_title)
        val descriptionInput = view.findViewById<EditText>(R.id.task_description)
        val createButton = view.findViewById<Button>(R.id.btn_create_task)

        createButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            
            if (title.isNotEmpty() && description.isNotEmpty()) {
                taskManager.createTask(title, description)
                titleInput.text.clear()
                descriptionInput.text.clear()
                updateTaskList(view)
            }
        }
    }

    private fun setupTaskList(view: View) {
        updateTaskList(view)
        
        // Обновляем список каждые 30 секунд
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(30_000)
                withContext(Dispatchers.Main) {
                    updateTaskList(view)
                }
            }
        }
    }

    private fun updateTaskList(view: View) {
        val taskList = view.findViewById<TextView>(R.id.task_list)
        val tasks = taskManager.getAllTasks()
        
        taskList.text = buildString {
            tasks.forEach { task ->
                append("ЗАДАЧА: ${task.title}\n")
                append("Статус: ${task.status}\n")
                append("Описание: ${task.description.take(50)}...\n\n")
            }
        }
    }
}
