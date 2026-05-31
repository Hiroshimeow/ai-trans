package com.example

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.audio.RecordingService
import com.example.audio.RecordingController
import com.example.audio.RecordingState
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingServiceTest {

    private lateinit var appDatabase: AppDatabase

    @Before
    fun setup() {
        // AppDatabase will use in-memory context normally in robolectric or fake
        appDatabase = AppDatabase.getDatabase(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `test recording service completion persists to DB`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sessionId = UUID.randomUUID().toString()

        // Setup a mock recording session in database first
        val sessionEntity = com.example.data.RecordingSessionEntity(
            id = sessionId,
            workspaceSessionId = null,
            title = "Test Meeting",
            mode = "meeting_record",
            audioPath = null,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            durationMs = 0L,
            status = "recording",
            stopReason = null,
            providerId = "test",
            language = "vi",
            finalTranscript = null,
            summary = null,
            actionItemsJson = null,
            errorCode = null,
            errorMessage = null
        )
        appDatabase.recordingSessionDao().insertRecordingSession(sessionEntity)

        // 1. Start the service
        val startIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra("EXTRA_SESSION_ID", sessionId)
            putExtra("EXTRA_IS_MEETING", true)
        }
        val serviceController = Robolectric.buildService(RecordingService::class.java, startIntent)
            .create()
            .startCommand(0, 1)

        val controller = RecordingController.getInstance(context)
        assertEquals(RecordingState.MEETING, controller.recordingState.value.state)
        assertEquals(sessionId, controller.recordingState.value.sessionId)

        // 2. Stop the service (like from notification)
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        serviceController.startCommand(0, 2)

        // 3. Verify DB update
        // Because scope.launch is used inside the service action, we may need a small delay or to yield
        kotlinx.coroutines.delay(100) 
        
        val updatedSession = appDatabase.recordingSessionDao().getRecordingSessionById(sessionId)
        assertNotNull(updatedSession)
        assertEquals("completed", updatedSession?.status)
        assertEquals("manual", updatedSession?.stopReason)
        assertNotNull(updatedSession?.audioPath)
    }
}
