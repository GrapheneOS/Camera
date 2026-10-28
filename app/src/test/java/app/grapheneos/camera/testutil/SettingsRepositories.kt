package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal fun settingsRepositoryOver(
    storedSettings: MutableStateFlow<CameraSettings>,
): SettingsRepository {
    return mockk {
        every { settings } returns storedSettings
        every { modeSettings(slot = any()) } returns ModeSettings()
        every { observeModeSettings(slot = any()) } answers { flowOf(modeSettings(firstArg())) }
        every { update(transform = any()) } answers {
            val transform = firstArg<(CameraSettings) -> CameraSettings>()
            storedSettings.value = transform(storedSettings.value)
            storedSettings.value
        }
    }
}
