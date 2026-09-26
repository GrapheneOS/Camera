package app.grapheneos.camera.ui.common

import android.os.Handler
import android.os.Looper
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.data.sound.player.CameraSoundPlayer

open class TunePlayer(
    private val cameraSoundPlayer: CameraSoundPlayer,
    private val soundsEnabled: () -> Boolean,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    open fun playVRStartSound(onPlayed: Runnable) {
        if (!soundsEnabled()) {
            onPlayed.run()
            return
        }

        cameraSoundPlayer.play(CameraSound.RECORDING_START) {
            mainHandler.postDelayed(onPlayed, SOUND_TO_START_DELAY_MS)
        }
    }

    private companion object {
        private const val SOUND_TO_START_DELAY_MS = 10L
    }
}
