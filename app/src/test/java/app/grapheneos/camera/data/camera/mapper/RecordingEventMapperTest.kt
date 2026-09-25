package app.grapheneos.camera.data.camera.mapper

import androidx.camera.video.RecordingStats
import androidx.camera.video.VideoRecordEvent
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordingEventMapperTest {

    private val mapper = RecordingEventMapperImpl()

    @Test
    fun map_aStart_reportsTheRecordingStarted() {
        assertEquals(RecordingEvent.Started, mapper.map(mockk<VideoRecordEvent.Start>()))
    }

    @Test
    fun map_aStatus_carriesTheRecordedDuration() {
        val status = mockk<VideoRecordEvent.Status> {
            every { recordingStats } returns statsOf(recordedDurationNanos = 5_000_000_000L)
        }

        assertEquals(
            RecordingEvent.Progressed(recordedDurationNanos = 5_000_000_000L),
            mapper.map(status),
        )
    }

    @Test
    fun map_aPause_isNotWorthReporting() {
        assertNull(mapper.map(mockk<VideoRecordEvent.Pause>()))
    }

    @Test
    fun map_aCleanFinalize_savesTheRecording() {
        assertEquals(
            RecordingEvent.Finalized(RecordingOutcome.Saved),
            mapper.map(finalizeOf(hasError = false)),
        )
    }

    @Test
    fun map_aFinalizeWithoutValidData_reportsNothingPlayableWritten() {
        assertEquals(
            RecordingEvent.Finalized(RecordingOutcome.NothingPlayableWritten),
            mapper.map(finalizeOf(error = VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA)),
        )
    }

    @Test
    fun map_aFinalizeTheRecorderCouldNotComplete_reportsAFailure() {
        val errors = listOf(
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
            VideoRecordEvent.Finalize.ERROR_UNKNOWN,
        )

        errors.forEach { error ->
            assertEquals(
                RecordingEvent.Finalized(RecordingOutcome.Failed(errorCode = error)),
                mapper.map(finalizeOf(error = error)),
            )
        }
    }

    @Test
    fun map_aFinalizeThatWasInterrupted_keepsWhatWasWritten() {
        val interrupted = finalizeOf(
            error = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
            numBytesRecorded = 1_024L,
        )

        assertEquals(
            RecordingEvent.Finalized(
                RecordingOutcome.Interrupted(
                    errorCode = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                    hasContent = true,
                ),
            ),
            mapper.map(interrupted),
        )
    }

    @Test
    fun map_aFinalizeThatWroteNothing_hasNoContentToKeep() {
        val interrupted = finalizeOf(
            error = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
            numBytesRecorded = 0L,
        )

        assertEquals(
            RecordingEvent.Finalized(
                RecordingOutcome.Interrupted(
                    errorCode = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
                    hasContent = false,
                ),
            ),
            mapper.map(interrupted),
        )
    }

    private fun finalizeOf(
        hasError: Boolean = true,
        error: Int = VideoRecordEvent.Finalize.ERROR_NONE,
        numBytesRecorded: Long = 0L,
    ): VideoRecordEvent.Finalize {
        return mockk<VideoRecordEvent.Finalize> {
            every { hasError() } returns hasError
            every { this@mockk.error } returns error
            every { recordingStats } returns statsOf(numBytesRecorded = numBytesRecorded)
        }
    }

    private fun statsOf(
        recordedDurationNanos: Long = 0L,
        numBytesRecorded: Long = 0L,
    ): RecordingStats {
        return mockk<RecordingStats> {
            every { this@mockk.recordedDurationNanos } returns recordedDurationNanos
            every { this@mockk.numBytesRecorded } returns numBytesRecorded
        }
    }
}
