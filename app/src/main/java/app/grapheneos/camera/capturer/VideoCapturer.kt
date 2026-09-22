package app.grapheneos.camera.capturer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderScreenModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction

class VideoCapturer(
    private val mActivity: MainActivity,
) {

    private val viewfinder: ViewfinderScreenModel = mActivity.viewfinder

    val isRecording: Boolean
        get() = viewfinder.uiState.value.isRecordingActive

    val isMuted: Boolean
        get() = viewfinder.uiState.value.isRecordingMuted

    var isPaused: Boolean
        get() = viewfinder.uiState.value.isRecordingPaused
        set(value) {
            if (isRecording) {
                viewfinder.onAction(RecordingAction.RecordingPauseToggled(paused = value))
            }
        }

    fun startRecording() {
        viewfinder.onAction(
            RecordingAction.RecordingRequested(
                hasAudioPermission = mActivity
                    .checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED,
            ),
        )
    }

    fun stopRecording() {
        viewfinder.onAction(RecordingAction.RecordingStopRequested)
    }

    fun muteRecording() {
        setMuted(muted = true)
    }

    fun unmuteRecording() {
        setMuted(muted = false)
    }

    private fun setMuted(muted: Boolean) {
        if (!isRecording) return
        check(viewfinder.uiState.value.capture.includeAudio)

        viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = muted))
    }
}

@Throws(Exception::class)
fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap? {
    MediaMetadataRetriever().use {
        it.setDataSource(context, uri)
        return it.frameAtTime
    }
}
