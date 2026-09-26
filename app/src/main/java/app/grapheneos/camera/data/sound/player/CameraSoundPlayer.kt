package app.grapheneos.camera.data.sound.player

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.sound.model.CameraSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CameraSoundPlayer {
    fun play(sound: CameraSound)

    /** [onFinished] runs exactly once: when the sound ends, fails, or cannot be played at all. */
    fun play(sound: CameraSound, onFinished: () -> Unit)
}

internal class CameraSoundPlayerImpl @Inject constructor(
    @ApplicationContext context: Context,
) : CameraSoundPlayer {

    private val preparedSounds = mutableSetOf<CameraSound>()

    private val players = CameraSound.entries.associateWith { sound ->
        preparePlayer(
            context = context,
            sound = sound,
        )
    }

    override fun play(sound: CameraSound) {
        if (sound !in preparedSounds) return

        val player = players.getValue(sound)

        player.seekTo(0)
        player.start()
    }

    override fun play(sound: CameraSound, onFinished: () -> Unit) {
        if (sound !in preparedSounds) {
            onFinished()
            return
        }

        val player = players.getValue(sound)
        var isDelivered = false

        // An unhandled playback error is also delivered to the completion listener, and a
        // failed sound must still deliver onFinished once: the recording start is waiting on it.
        val deliverOnce = {
            if (!isDelivered) {
                isDelivered = true
                player.setOnCompletionListener(null)
                onFinished()
            }
        }

        player.setOnCompletionListener {
            deliverOnce()
        }

        player.setOnErrorListener { _, _, _ ->
            preparedSounds -= sound
            deliverOnce()
            true
        }

        player.seekTo(0)
        player.start()
    }

    private fun preparePlayer(
        context: Context,
        sound: CameraSound,
    ): MediaPlayer {
        val player = MediaPlayer()

        context.resources.openRawResourceFd(resourceOf(sound)).use { file ->
            player.setDataSource(file)
        }

        player.setOnPreparedListener {
            preparedSounds += sound
        }

        player.setOnErrorListener { _, _, _ ->
            preparedSounds -= sound
            true
        }

        player.prepareAsync()

        return player
    }

    @RawRes
    private fun resourceOf(sound: CameraSound): Int {
        return when (sound) {
            CameraSound.SHUTTER -> R.raw.image_shot
            CameraSound.FOCUS_START -> R.raw.focus_start
            CameraSound.TIMER_TICK -> R.raw.timer_increment
            CameraSound.TIMER_FINAL_SECOND -> R.raw.timer_final_second
            CameraSound.RECORDING_START -> R.raw.video_start
            CameraSound.RECORDING_STOP -> R.raw.video_stop
        }
    }
}
