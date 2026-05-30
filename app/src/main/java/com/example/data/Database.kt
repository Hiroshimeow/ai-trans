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
    val apiKey: String? = null, // Encrypted securely via SecurityHelper
    val maxTokens: Int? = null
) {
    fun getDecryptedApiKey(): String? {
        val key = apiKey ?: return null
        return SecurityHelper.decrypt(key)
    }
}

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
    val source: String, // "image", "import", "recording"
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
    val createdAt: Long,
    val alwaysOn: Boolean = false
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

    @Query("UPDATE prompt_skills SET alwaysOn = :alwaysOn WHERE id = :id")
    suspend fun updateSkillAlwaysOn(id: String, alwaysOn: Boolean)

    @Query("UPDATE prompt_skills SET title = :title, content = :content WHERE id = :id")
    suspend fun updateSkillTitleAndContent(id: String, title: String, content: String)

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

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val workspaceSessionId: String?,
    val title: String,
    val mode: String, // "quick_voice_note", "float_quick_ask", "live_transcribe", "meeting_record"
    val audioPath: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMs: Long,
    val status: String,
    val stopReason: String?,
    val providerId: String?,
    val language: String,
    val finalTranscript: String?,
    val summary: String?,
    val actionItemsJson: String?,
    val errorCode: String?,
    val errorMessage: String?
)

@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val recordingSessionId: String,
    val index: Int,
    val startMs: Long?,
    val endMs: Long?,
    val text: String,
    val isFinal: Boolean,
    val speakerLabel: String?,
    val language: String?,
    val confidence: Float?,
    val source: String,
    val createdAt: Long
)

@Entity(tableName = "transcript_jobs")
data class TranscriptJobEntity(
    @PrimaryKey val id: String,
    val recordingSessionId: String,
    val type: String,
    val status: String,
    val providerId: String,
    val model: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val errorCode: String?,
    val errorMessage: String?,
    val metadataJson: String
)

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions ORDER BY startedAt DESC")
    fun getAllRecordingSessionsFlow(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    suspend fun getRecordingSessionById(id: String): RecordingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordingSession(session: RecordingSessionEntity)

    @Update
    suspend fun updateRecordingSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun deleteRecordingSessionById(id: String)
}

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE recordingSessionId = :recordingSessionId ORDER BY `index` ASC")
    fun getSegmentsForSessionFlow(recordingSessionId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE recordingSessionId = :recordingSessionId ORDER BY `index` ASC")
    suspend fun getSegmentsForSessionSync(recordingSessionId: String): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE recordingSessionId = :recordingSessionId")
    suspend fun deleteSegmentsBySessionId(recordingSessionId: String)
}

@Dao
interface TranscriptJobDao {
    @Query("SELECT * FROM transcript_jobs WHERE recordingSessionId = :recordingSessionId")
    fun getJobsForSession(recordingSessionId: String): Flow<List<TranscriptJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: TranscriptJobEntity)
}

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val tokenAlias: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastConnectedAt: Long?,
    val lastError: String?
)

@Entity(tableName = "mcp_tools")
data class McpToolEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val enabled: Boolean,
    val updatedAt: Long
)

@Dao
interface McpDao {
    @Query("SELECT * FROM mcp_servers ORDER BY createdAt ASC")
    fun getAllServersFlow(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1")
    suspend fun getEnabledServersSync(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServerEntity)
    
    @Update
    suspend fun updateServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)

    @Query("SELECT * FROM mcp_tools WHERE serverId = :serverId")
    suspend fun getToolsForServerSync(serverId: String): List<McpToolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<McpToolEntity>)

    @Query("DELETE FROM mcp_tools WHERE serverId = :serverId")
    suspend fun deleteToolsByServerId(serverId: String)

    @Query("UPDATE mcp_tools SET enabled = :enabled WHERE id = :toolId")
    suspend fun updateToolEnabled(toolId: String, enabled: Boolean)
}

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        PromptSkillEntity::class,
        RecordingEntity::class,
        RecordingSessionEntity::class,
        TranscriptSegmentEntity::class,
        TranscriptJobEntity::class,
        McpServerEntity::class,
        McpToolEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun promptSkillDao(): PromptSkillDao
    abstract fun recordingDao(): RecordingDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun transcriptJobDao(): TranscriptJobDao
    abstract fun mcpDao(): McpDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `prompt_skills` ADD COLUMN `alwaysOn` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mcp_servers` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `baseUrl` TEXT NOT NULL, 
                        `tokenAlias` TEXT NOT NULL, 
                        `enabled` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `lastConnectedAt` INTEGER, 
                        `lastError` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mcp_tools` (
                        `id` TEXT NOT NULL, 
                        `serverId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `inputSchemaJson` TEXT NOT NULL, 
                        `enabled` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recording_sessions` (
                        `id` TEXT NOT NULL, 
                        `workspaceSessionId` TEXT, 
                        `title` TEXT NOT NULL, 
                        `mode` TEXT NOT NULL, 
                        `audioPath` TEXT, 
                        `startedAt` INTEGER NOT NULL, 
                        `endedAt` INTEGER, 
                        `durationMs` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `stopReason` TEXT, 
                        `providerId` TEXT, 
                        `language` TEXT NOT NULL, 
                        `finalTranscript` TEXT, 
                        `summary` TEXT, 
                        `actionItemsJson` TEXT, 
                        `errorCode` TEXT, 
                        `errorMessage` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transcript_segments` (
                        `id` TEXT NOT NULL, 
                        `recordingSessionId` TEXT NOT NULL, 
                        `index` INTEGER NOT NULL, 
                        `startMs` INTEGER, 
                        `endMs` INTEGER, 
                        `text` TEXT NOT NULL, 
                        `isFinal` INTEGER NOT NULL, 
                        `speakerLabel` TEXT, 
                        `language` TEXT, 
                        `confidence` REAL, 
                        `source` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transcript_jobs` (
                        `id` TEXT NOT NULL, 
                        `recordingSessionId` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `providerId` TEXT NOT NULL, 
                        `model` TEXT, 
                        `startedAt` INTEGER NOT NULL, 
                        `endedAt` INTEGER, 
                        `errorCode` TEXT, 
                        `errorMessage` TEXT, 
                        `metadataJson` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_chat_workspace_db"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration() // Adding this just in case as early MVP
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
