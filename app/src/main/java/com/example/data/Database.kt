package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val endpointUrl: String? = null,
    val modelName: String? = null,
    val apiProvider: String? = null, // provider ID or "gemini"
    val apiKey: String? = null,
    val maxTokens: Int? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val attachmentIdsJson: String, // JSONArray of String IDs
    val systemPromptUsed: String,
    val createdAt: Long,
    val isError: Boolean = false,
    val isPending: Boolean = false
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val uri: String, // File path or Content URI (base64 context if needed)
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: String, // "screenshot", "import", "recording"
    val status: String, // "available", "selected", "already_in_chat"
    val extractedText: String? = null,
    val thumbnailUri: String? = null
)

@Entity(tableName = "prompt_skills")
data class PromptSkillEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val selected: Boolean,
    val builtIn: Boolean,
    val createdAt: Long
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val audioPath: String,
    val durationSeconds: Double,
    val transcript: String,
    val createdAt: Long,
    val error: String? = null
)

class DatabaseConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<String>()
        val array = JSONArray(value)
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: String)

    @Query("DELETE FROM attachments WHERE sessionId = :sessionId")
    suspend fun deleteAttachmentsBySessionId(sessionId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE sessionId = :sessionId")
    fun getAttachmentsForSession(sessionId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getAttachmentById(id: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET status = :status WHERE id = :id")
    suspend fun updateAttachmentStatus(id: String, status: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: String)
}

@Dao
interface PromptSkillDao {
    @Query("SELECT * FROM prompt_skills ORDER BY createdAt ASC")
    fun getPromptSkillsFlow(): Flow<List<PromptSkillEntity>>

    @Query("SELECT * FROM prompt_skills")
    suspend fun getPromptSkillsSync(): List<PromptSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: PromptSkillEntity)

    @Query("UPDATE prompt_skills SET selected = :selected WHERE id = :id")
    suspend fun updateSkillSelection(id: String, selected: Boolean)

    @Query("DELETE FROM prompt_skills WHERE id = :id")
    suspend fun deleteSkillById(id: String)

    @Query("DELETE FROM prompt_skills WHERE builtIn = 1")
    suspend fun deleteBuiltInSkills()
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getRecordingsFlow(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: String)
}

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        PromptSkillEntity::class,
        RecordingEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun promptSkillDao(): PromptSkillDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_chat_workspace_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
