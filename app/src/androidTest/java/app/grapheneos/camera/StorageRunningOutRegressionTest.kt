package app.grapheneos.camera

import android.Manifest
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.Os
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import java.io.FileDescriptor
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageRunningOutRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    /** Recording only starts once the preview window is visible and producing frames. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Stands in for MediaProvider with the file it opens for a recording held in memory, and passes
     * everything else to the real one. Storage that runs out refuses to grow the file, which the
     * memory can be sealed to do too.
     */
    private class InMemoryOutputProvider(private val real: ContentResolver) : ContentProvider() {
        private val output: FileDescriptor = Os.memfd_create("recording", MFD_ALLOW_SEALING)
        private val outputs = CopyOnWriteArrayList<Uri>()

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? =
            real.insert(uri, values)?.also { outputs.add(it) }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.dup(output)

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) =
            real.delete(uri, selection, selectionArgs)

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?,
        ) = real.update(uri, values, selection, selectionArgs)

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?,
        ): Cursor? = real.query(uri, projection, selection, selectionArgs, sortOrder)

        override fun getType(uri: Uri) = real.getType(uri)

        /** Leaves room for this many bytes and no more, as storage with only that much left. */
        fun leaveRoomFor(bytes: Long) {
            Os.ftruncate(output, bytes)
            Os.fcntlInt(output, F_ADD_SEALS, F_SEAL_GROW)
        }

        /** Fails every write from now on. */
        fun refuseWrites() {
            Os.fcntlInt(output, F_ADD_SEALS, F_SEAL_WRITE)
        }

        fun outputPlays(): Boolean = try {
            MediaMetadataRetriever().use {
                it.setDataSource(output)
                it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            }
        } catch (e: RuntimeException) {
            false
        }

        /** Deletes the entry of every output created, which a discarded one has already left. */
        fun deleteOutputs() {
            outputs.forEach { runCatching { real.delete(it, null, null) } }
        }
    }

    /**
     * CameraX stops a recording before it fills up its storage only where it can tell what that
     * storage is, which the descriptor the camera records to doesn't tell it, so a recording ran on
     * until a write failed, and the failure then deleted all of it.
     */
    @Test
    fun recording_asTheStorageRunsLow_stopsAndIsSaved() {
        recordingTest { scenario ->
            val capturedBefore = lastCapturedUri(scenario)
            scenario.onActivity {
                val freeBytes = StatFs(Environment.getExternalStorageDirectory().path)
                    .availableBytes
                // Treats the storage as nearly full: what has to stay free is all but a little
                it.videoCapturer.requiredFreeStorageBytes = freeBytes - RECORDABLE_BYTES
                it.videoCapturer.startRecording()
            }
            awaitRecordingStarted(scenario)

            waitUntil(scenario, "the recording stops", RECORDING_TIMEOUT_MS) {
                !it.videoCapturer.isRecording
            }
            awaitOutputSettled(scenario)
            waitUntil(scenario, "the stop is explained") {
                snackbarText(it) == it.getString(R.string.recording_stopped_storage_almost_full)
            }
            assertNotEquals(
                "The recording was not saved",
                capturedBefore,
                lastCapturedUri(scenario)
            )
        }
    }

    /**
     * A muxer that runs out of storage stops taking samples and writes its index into the room it
     * kept for it, leaving what was recorded up to then playable, yet the error it ends the
     * recording with deleted all of it.
     */
    @Test
    fun recording_whenTheStorageFillsUp_keepsWhatWasRecorded() {
        val mediaProvider = InMemoryOutputProvider(targetContext.contentResolver)
        mediaProvider.leaveRoomFor(OUTPUT_ROOM_BYTES)

        recordingTest(mediaProvider) { scenario ->
            val capturedBefore = lastCapturedUri(scenario)
            scenario.onActivity { it.videoCapturer.startRecording() }
            awaitRecordingStarted(scenario)

            waitUntil(scenario, "the full storage ends the recording", RECORDING_TIMEOUT_MS) {
                !it.videoCapturer.isRecording
            }
            awaitOutputSettled(scenario)
            assertTrue("The muxer left nothing that plays", mediaProvider.outputPlays())
            assertNotEquals(
                "What was recorded was not saved",
                capturedBefore,
                lastCapturedUri(scenario)
            )
        }
    }

    /** A muxer whose writes fail can't write its index either, which leaves nothing to keep. */
    @Test
    fun recording_whenItsWritesFail_isNotSaved() {
        val mediaProvider = InMemoryOutputProvider(targetContext.contentResolver)

        recordingTest(mediaProvider) { scenario ->
            val capturedBefore = lastCapturedUri(scenario)
            val pendingBefore = pendingVideoCount()
            scenario.onActivity { it.videoCapturer.startRecording() }
            recordASecond(scenario)

            mediaProvider.refuseWrites()
            waitUntil(scenario, "the failed writes end the recording", RECORDING_TIMEOUT_MS) {
                !it.videoCapturer.isRecording
            }
            awaitOutputSettled(scenario)
            assertFalse("The muxer left something that plays", mediaProvider.outputPlays())
            assertEquals(
                "A recording that doesn't play was saved",
                capturedBefore,
                lastCapturedUri(scenario)
            )
            assertEquals("Its output was left behind", pendingBefore, pendingVideoCount())
        }
    }

    /**
     * Hands [body] a camera that records through [mediaProvider] if given, and cleans up after it
     * either way.
     */
    private fun recordingTest(
        mediaProvider: InMemoryOutputProvider? = null,
        body: (ActivityScenario<VideoOnlyActivity>) -> Unit,
    ) {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            scenario.onActivity {
                it.camConfig.mPlayer = ImmediateTunePlayer(it)
                if (mediaProvider != null) {
                    it.videoCapturer.contentResolver = ContentResolver.wrap(mediaProvider)
                }
            }
            val capturedBefore = lastCapturedUri(scenario)
            try {
                body(scenario)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
                mediaProvider?.deleteOutputs()
            }
        }
    }

    private fun snackbarText(activity: VideoOnlyActivity) =
        activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text?.toString()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        // A few seconds of video at any quality
        const val RECORDABLE_BYTES = 8L * 1024 * 1024

        const val OUTPUT_ROOM_BYTES = 8L * 1024 * 1024

        const val RECORDING_TIMEOUT_MS = 30_000L

        // From the Linux uapi headers, which OsConstants leaves out
        const val MFD_ALLOW_SEALING = 0x0002
        const val F_ADD_SEALS = 1033
        const val F_SEAL_GROW = 0x0004
        const val F_SEAL_WRITE = 0x0008
    }
}
