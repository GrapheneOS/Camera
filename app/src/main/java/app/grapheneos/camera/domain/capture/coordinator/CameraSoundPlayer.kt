package app.grapheneos.camera.domain.capture.coordinator

import android.content.Context
import android.media.MediaPlayer
import app.grapheneos.camera.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CameraSoundPlayer {
    fun playRecordingStop()
}

internal class CameraSoundPlayerImpl @Inject constructor(
    @ApplicationContext context: Context,
) : CameraSoundPlayer {

    private val recordingStopPlayer = MediaPlayer()

    private var isRecordingStopPrepared = false

    init {
        context.resources.openRawResourceFd(R.raw.video_stop).use { sound ->
            recordingStopPlayer.setDataSource(sound)
        }

        recordingStopPlayer.setOnPreparedListener {
            isRecordingStopPrepared = true
        }

        recordingStopPlayer.setOnErrorListener { _, _, _ ->
            isRecordingStopPrepared = false
            true
        }

        recordingStopPlayer.prepareAsync()
    }

    override fun playRecordingStop() {
        if (!isRecordingStopPrepared) return

        recordingStopPlayer.seekTo(0)
        recordingStopPlayer.start()
    }
}
