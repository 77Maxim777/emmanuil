import android.content.Context
import androidx.room.*

@Database(entities = [Message::class], version = 1)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null

        fun getInstance(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "emmanuil_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY time DESC LIMIT 100")
    fun getLastMessages(): List<Message>

    @Query("SELECT * FROM messages WHERE time = :time LIMIT 1")
    fun getMessageByTime(time: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(message: Message)
}

@Entity
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val source: String = "",
    val author: String,
    val text: String,
    val time: String
)
