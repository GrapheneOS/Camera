package app.grapheneos.camera.domain.sound.usecase

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.data.sound.player.CameraSoundPlayer
import javax.inject.Inject

interface PlayCameraSound {
    suspend operator fun invoke(sound: CameraSound)
}

internal class PlayCameraSoundImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cameraSoundPlayer: CameraSoundPlayer,
) : PlayCameraSound {

    override suspend fun invoke(sound: CameraSound) {
        if (!settingsRepository.settings.value.enableCameraSounds) return

        cameraSoundPlayer.play(sound)
    }
}
