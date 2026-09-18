package app.grapheneos.camera.capturer

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository.Companion.DEFAULT_MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.store.videoCollectionUri
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.getTreeDocumentUri
import app.grapheneos.camera.util.removePendingFlagFromUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VideoCapturer(private val mActivity: MainActivity) {

    private val viewfinder: ViewfinderScreenModel = mActivity.viewfinder

    private val session = mActivity.session

    val isRecording: Boolean
        get() = viewfinder.uiState.value.isRecordingActive

    private val videoFileFormat = ".mp4"

    private var recording: Recording? = null

    // Invoking this abandons a start still waiting on its output or queued behind the record-start
    // sound.
    private var cancelDeferredStart: (() -> Unit)? = null

    val isMuted: Boolean
        get() = viewfinder.uiState.value.isRecordingMuted

    var includeAudio: Boolean = false

    var isPaused: Boolean
        get() = viewfinder.uiState.value.isRecordingPaused
        set(value) {
            if (isRecording) {
                if (value) {
                    recording?.pause()
                } else {
                    recording?.resume()
                }
                reportRecordingState(RecordingAction.RecordingPauseToggled(paused = value))
            }
        }

    private val handler = Handler(Looper.getMainLooper())

    // The output is created, opened, saved and discarded through this
    @set:VisibleForTesting
    var contentResolver: ContentResolver = mActivity.contentResolver

    private fun updateTimerTime(timeInNanos: Long) {
        mActivity.timerView.text = formatVideoDuration(timeInNanos / 1_000_000_000)
    }

    private class RecordingContext(
        val uri: Uri,
        val fileDescriptor: ParcelFileDescriptor,
        val shouldAddToGallery: Boolean,
        val isPendingMediaStoreUri: Boolean,
    )

    // Runs on outputExecutor: each step is a call into another process, MediaProvider or the app
    // the video is for, and can take seconds to answer.
    private fun createRecordingContext(
        fileName: String,
        foreignUri: Uri?,
        storageLocation: String,
    ): RecordingContext? {
        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(videoFileFormat) ?: "video/mp4"

        val uri: Uri?
        var shouldAddToGallery = true
        var isPendingMediaStoreUri = false

        if (foreignUri != null) {
            uri = foreignUri
            shouldAddToGallery = false
        } else {
            if (storageLocation == CapturedItemRepository.MEDIA_STORE_LOCATION) {
                val contentValues = ContentValues().apply {
                    put(MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaColumns.MIME_TYPE, mimeType)
                    put(MediaColumns.RELATIVE_PATH, DEFAULT_MEDIA_STORE_CAPTURE_PATH)
                    put(MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(videoCollectionUri, contentValues)
                isPendingMediaStoreUri = true
            } else {
                val treeUri = Uri.parse(storageLocation)
                val treeDocumentUri = getTreeDocumentUri(treeUri)

                uri = DocumentsContract.createDocument(contentResolver, treeDocumentUri, mimeType, fileName)
            }
        }

        if (uri == null) {
            return null
        }

        return contentResolver
            .openFileDescriptor(uri, "w")
            ?.let {
                RecordingContext(uri, it, shouldAddToGallery, isPendingMediaStoreUri)
            }
    }

    private fun prepareRecording(recorder: Recorder, recordingCtx: RecordingContext): PendingRecording {
        var location: Location? = null
        if (viewfinder.uiState.value.capture.geoTagging) {
            location = mActivity.locationRepository.currentLocation()
            if (location == null) {
                mActivity.showMessage(R.string.location_unavailable)
            }
        }
        val outputOptions = FileDescriptorOutputOptions.Builder(recordingCtx.fileDescriptor)
            .setLocation(location)
            .build()
        return recorder.prepareRecording(mActivity, outputOptions)
    }

    fun startRecording() {
        if (session.camera == null) return
        val recorder = session.videoCapture?.output ?: return
        if (isRecording) return

        viewfinder.onAction(RecordingAction.RecordingRequested)

        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = VIDEO_NAME_PREFIX + dateString + videoFileFormat

        includeAudio = false

        val ctx = mActivity

        if (viewfinder.uiState.value.capture.includeAudio) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED) {
                includeAudio = true
            } else {
                ctx.restartRecordingWithMicPermission()
                viewfinder.onAction(RecordingAction.RecordingStopped)
                return
            }
        }

        val foreignUri = if (ctx is VideoCaptureActivity && ctx.isOutputUriAvailable()) {
            ctx.outputUri
        } else {
            null
        }
        val storageLocation = mActivity.capturedItemSession.storageLocation

        // A stop cancels the start until the recording exists, and the sound callback may fire
        // more than once; a second PendingRecording.start() throws.
        var consumed = false
        // A cancel discards the output if it exists by then, and its arrival does otherwise
        var createdCtx: RecordingContext? = null

        cancelDeferredStart = {
            consumed = true
            cancelDeferredStart = null
            createdCtx?.let { abandonOutput(it) }
            afterRecordingStops()
        }

        outputExecutor.execute {
            val recordingCtx = try {
                createRecordingContext(fileName, foreignUri, storageLocation)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            handler.post {
                if (consumed) {
                    recordingCtx?.let { abandonOutput(it) }
                    return@post
                }

                val pendingRecording = try {
                    recordingCtx?.let { prepareRecording(recorder, it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }

                if (recordingCtx == null || pendingRecording == null) {
                    recordingCtx?.let { abandonOutput(it) }
                    if (foreignUri == null) {
                        viewfinder.onAction(CaptureAction.StorageLocationNotFound)
                    }
                    ctx.showMessage(R.string.unable_to_access_output_file)
                    cancelDeferredStart = null
                    afterRecordingStops()
                    return@post
                }

                createdCtx = recordingCtx

                if (includeAudio) {
                    pendingRecording.withAudioEnabled()
                }

                val onStartSoundPlayed = onStartSoundPlayed@{
                    if (consumed) {
                        return@onStartSoundPlayed
                    }

                    consumed = true
                    cancelDeferredStart = null

                    recording = pendingRecording.start(ctx.mainExecutor) { event ->
                        onRecordEvent(event, recordingCtx, dateString)
                    }

                    // The Recording didn't exist yet when the mute/pause setters ran.
                    if (isMuted) {
                        recording?.mute(true)
                    }

                    if (isPaused) {
                        recording?.pause()
                    }

                    try {
                        // FileDescriptorOutputOptions doc says that the file descriptor should be closed by the
                        // caller, and that it's safe to do so as soon as pendingRecording.start() returns
                        recordingCtx.fileDescriptor.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                mActivity.tunePlayer.playVRStartSound(handler, onStartSoundPlayed)
            }
        }
    }

    private fun onRecordEvent(event: VideoRecordEvent, recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity

        if (event is VideoRecordEvent.Start) {
            onRecordingStart()
        }

        if (event is VideoRecordEvent.Status) {
            updateTimerTime(event.recordingStats.recordedDurationNanos)
        }

        if (event is VideoRecordEvent.Finalize) {
            afterRecordingStops()

            mActivity.tunePlayer.playVRStopSound()

            if (event.hasError()) {
                when (event.error) {
                    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> {
                        abandonOutput(recordingCtx)
                        ctx.showMessage(R.string.recording_too_short_to_be_saved)
                        return
                    }
                    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED,
                    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR,
                    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> {
                        abandonOutput(recordingCtx)
                        ctx.showMessage(ctx.getString(R.string.unable_to_save_video_verbose, event.error))
                        return
                    }
                    else -> {
                        ctx.showMessage(ctx.getString(R.string.error_during_recording, event.error))
                        // The errors left unnamed here (the camera going away, storage
                        // running out) finalize whatever was written before they hit, which
                        // is worth keeping — but only if anything was.
                        if (event.recordingStats.numBytesRecorded == 0L) {
                            abandonOutput(recordingCtx)
                            return
                        }
                    }
                }
            }

            saveOutput(recordingCtx, dateString)
        }
    }

    // A video in MediaStore stays pending, hidden from other apps and left to be deleted, until
    // MediaProvider is told to make it visible: only then is it saved.
    private fun saveOutput(recordingCtx: RecordingContext, dateString: String) {
        if (!recordingCtx.isPendingMediaStoreUri) {
            onOutputSaved(recordingCtx, dateString)
            return
        }

        // Off the main thread for the same reason the output is created there
        outputExecutor.execute {
            val saved = try {
                removePendingFlagFromUri(contentResolver, recordingCtx.uri)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            handler.post {
                if (saved) {
                    onOutputSaved(recordingCtx, dateString)
                } else {
                    mActivity.showMessage(R.string.unable_to_save_video)
                }
            }
        }
    }

    private fun onOutputSaved(recordingCtx: RecordingContext, dateString: String) {
        val ctx = mActivity
        val uri = recordingCtx.uri

        if (recordingCtx.shouldAddToGallery) {
            val item = CapturedItem(ITEM_TYPE_VIDEO, dateString, uri)
            mActivity.capturedItemSession.recordCapturedItem(item)

            ctx.updateThumbnail()

            if (ctx is SecureMainActivity) {
                ctx.capturedItems.add(item)
            }
        }

        if (ctx is VideoCaptureActivity) {
            ctx.afterRecording(uri)
        }
    }

    private val dp16 = 16 * mActivity.resources.displayMetrics.density
    private val dp8 = 8 * mActivity.resources.displayMetrics.density

    // Skinned devices wrap the capture button shape in selectors and layer-lists
    private fun findGradientDrawable(drawable: Drawable?): GradientDrawable? {
        return when (drawable) {
            is GradientDrawable -> drawable
            is StateListDrawable -> findGradientDrawable(drawable.current)
            is LayerDrawable -> {
                (0 until drawable.numberOfLayers)
                    .firstNotNullOfOrNull { findGradientDrawable(drawable.getDrawable(it)) }
            }
            else -> null
        }
    }

    // If no shape can be dug out, skip the cosmetic animation rather than crash
    private fun animateCaptureButtonCorners(from: Float, to: Float) {
        val gd = findGradientDrawable(mActivity.captureButton.drawable) ?: return

        val animator = ValueAnimator.ofFloat(from, to)
        animator.setDuration(300)
            .addUpdateListener { animation ->
                gd.cornerRadius = animation.animatedValue as Float
            }
        animator.start()
    }

    private fun onRecordingStart() {
        animateCaptureButtonCorners(dp16, dp8)

        reportRecordingState(RecordingAction.RecordingStarted)

        mActivity.tabLayout.visibility = View.INVISIBLE
        mActivity.timerView.setText(R.string.start_value_timer)
    }

    private fun afterRecordingStops() {
        animateCaptureButtonCorners(dp8, dp16)

        if (mActivity !is VideoCaptureActivity) {
            mActivity.tabLayout.visibility = View.VISIBLE
        }

        reportRecordingState(RecordingAction.RecordingStopped)

        mActivity.forceUpdateOrientationSensor()
    }

    fun muteRecording() {
        if (!isRecording) return
        check(viewfinder.uiState.value.capture.includeAudio)
        viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = true))
        recording?.mute(true)
    }

    fun unmuteRecording() {
        if (!isRecording) return
        check(viewfinder.uiState.value.capture.includeAudio)
        viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = false))
        recording?.mute(false)
    }

    fun stopRecording() {
        cancelDeferredStart?.let {
            it()
            return
        }

        recording?.stop()
        recording?.close()
        recording = null
    }

    private fun reportRecordingState(action: RecordingAction) {
        if (mActivity.isDestroyed) {
            return
        }

        viewfinder.onAction(action)
    }

    // Off the main thread for the same reason the output is created there
    private fun abandonOutput(recordingCtx: RecordingContext) {
        outputExecutor.execute {
            try {
                // A no-op once the recording has started: starting it closed this
                recordingCtx.fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            discardUnusedOutput(recordingCtx)
        }
    }

    private fun discardUnusedOutput(recordingCtx: RecordingContext) {
        if (!recordingCtx.shouldAddToGallery) {
            return
        }

        try {
            if (recordingCtx.isPendingMediaStoreUri) {
                contentResolver.delete(recordingCtx.uri, null, null)
            } else {
                DocumentsContract.deleteDocument(contentResolver, recordingCtx.uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        // Shared across activities like ImageSaver's: a stalled MediaProvider holds one thread, not
        // one per press
        private val outputExecutor = Executors.newSingleThreadExecutor()

        /**
         * Returns once the output work queued so far is done, and has posted back what it posts
         */
        @VisibleForTesting
        fun awaitOutputWork(timeoutS: Long) {
            outputExecutor.submit {}.get(timeoutS, TimeUnit.SECONDS)
        }
    }
}

private const val STALE_PENDING_RECORDING_AGE = 60 * 60 * 1000L

// A recording that dies with its process (swipe-away from Recents, OOM kill, crash) never reaches
// the Finalize callback that clears IS_PENDING, so it leaves a half-written file that is invisible
// to the user until MediaProvider expires it a week later, and unplayable in the meantime since it
// has no moov atom. Deleting is the honest outcome. Pending rows are only visible to the app that
// owns them, so this can never reach another app's in-flight write, and the age cutoff keeps it
// clear of a recording that is still being muxed.
fun deleteStalePendingRecordings(
    context: Context,
    maxAge: Long = STALE_PENDING_RECORDING_AGE,
) {
    val selection = "${MediaColumns.IS_PENDING} = 1" +
        " AND ${MediaColumns.DISPLAY_NAME} LIKE ?" +
        " AND ${MediaColumns.DATE_ADDED} < ?"
    val cutoffSeconds = (System.currentTimeMillis() - maxAge) / 1000L
    val args = arrayOf("$VIDEO_NAME_PREFIX%", cutoffSeconds.toString())

    try {
        // Pending rows are filtered out of every operation unless they are explicitly asked for.
        @Suppress("DEPRECATION")
        val collection = MediaStore.setIncludePending(videoCollectionUri)
        context.contentResolver.delete(collection, selection, args)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
