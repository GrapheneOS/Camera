package app.grapheneos.camera.domain.capture.usecase

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.capture.coordinator.CameraSoundPlayer
import javax.inject.Inject

interface PlayRecordingStopSound {
    suspend operator fun invoke()
}

internal class PlayRecordingStopSoundImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cameraSoundPlayer: CameraSoundPlayer,
) : PlayRecordingStopSound {

    override suspend fun invoke() {
        if (!settingsRepository.settings.value.enableCameraSounds) return

        cameraSoundPlayer.playRecordingStop()
    }
}
