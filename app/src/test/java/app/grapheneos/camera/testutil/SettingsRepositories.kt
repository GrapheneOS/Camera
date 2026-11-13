package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal fun settingsRepositoryOver(
    storedSettings: MutableStateFlow<CameraSettings>,
    storedModeSettings: MutableMap<ModeSlot, ModeSettings> = mutableMapOf(),
): SettingsRepository {
    return mockk {
        every { settings } returns storedSettings
        every { modeSettings(slot = any()) } answers {
            storedModeSettings[firstArg()] ?: ModeSettings()
        }
        every { observeModeSettings(slot = any()) } answers { flowOf(modeSettings(firstArg())) }
        every { update(transform = any()) } answers {
            val transform = firstArg<(CameraSettings) -> CameraSettings>()
            storedSettings.value = transform(storedSettings.value)
            storedSettings.value
        }
        every { updateMode(slot = any(), transform = any()) } answers {
            val slot = firstArg<ModeSlot>()
            val transform = secondArg<(ModeSettings) -> ModeSettings>()
            val updated = transform(modeSettings(slot))
            storedModeSettings[slot] = updated
            updated
        }
    }
}
