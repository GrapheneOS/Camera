package app.grapheneos.camera.data.sound.player

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RawRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.sound.model.CameraSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CameraSoundPlayer {

    fun play(sound: CameraSound)

    /**
     * [onFinished] runs exactly once, off the main thread: when the sound ends, fails, or cannot be
     * played at all.
     */
    fun play(sound: CameraSound, onFinished: () -> Unit)
}

internal class CameraSoundPlayerImpl(
    context: Context,
    private val newMediaPlayer: () -> MediaPlayer,
) : CameraSoundPlayer {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        newMediaPlayer = ::MediaPlayer,
    )

    // Every MediaPlayer call is a synchronous binder transaction into mediaserver, which can take
    // seconds to answer. Made from the main thread, that wait was reported as an ANR, so the players
    // are created, prepared and played here. A player also delivers its events to the thread that
    // created it, and the prepared event calls into mediaserver too.
    private val soundHandler = HandlerThread(SOUND_THREAD_NAME).run {
        start()
        Handler(looper)
    }

    private val preparedSounds = mutableSetOf<CameraSound>()
    private val players = mutableMapOf<CameraSound, MediaPlayer>()

    init {
        soundHandler.post {
            CameraSound.entries.forEach { sound ->
                players[sound] = preparePlayer(
                    context = context,
                    sound = sound,
                )
            }
        }
    }

    override fun play(sound: CameraSound) {
        soundHandler.post {
            if (sound in preparedSounds) {
                val player = players.getValue(sound)

                player.seekTo(0)
                player.start()
            }
        }
    }

    override fun play(sound: CameraSound, onFinished: () -> Unit) {
        soundHandler.post {
            when (sound) {
                in preparedSounds -> playToTheEnd(sound, onFinished)
                else -> onFinished()
            }
        }
    }

    private fun playToTheEnd(sound: CameraSound, onFinished: () -> Unit) {
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
        val player = newMediaPlayer()

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

    private companion object {
        private const val SOUND_THREAD_NAME = "CameraSounds"
    }
}
