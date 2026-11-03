package app.grapheneos.camera.ui.common

import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.data.sound.player.CameraSoundPlayer
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

open class TunePlayer(
    private val cameraSoundPlayer: CameraSoundPlayer,
) {

    open suspend fun playRecordingStartSound(soundEnabled: Boolean) {
        if (!soundEnabled) return

        suspendCancellableCoroutine { continuation ->
            cameraSoundPlayer.play(CameraSound.RECORDING_START) {
                continuation.resume(Unit)
            }
        }
        delay(SOUND_TO_START_DELAY)
    }

    private companion object {
        private val SOUND_TO_START_DELAY = 10.milliseconds
    }
}
