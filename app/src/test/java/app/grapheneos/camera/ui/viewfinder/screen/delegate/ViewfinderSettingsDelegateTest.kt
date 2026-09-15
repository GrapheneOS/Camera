package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.video.Quality
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderSettingsDelegateTest {

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository = mockk<SettingsRepository>()

    @Before
    fun setUp() {
        every { settingsRepository.settings } returns storedSettings
        every { settingsRepository.update(transform = any()) } answers {
            val transform = firstArg<(CameraSettings) -> CameraSettings>()
            storedSettings.value = transform(storedSettings.value)
            storedSettings.value
        }
    }

    @Test
    fun settings_areReadThroughTheRepository() {
        val delegate = createDelegate()

        storedSettings.value = CameraSettings(removeExifAfterCapture = false)

        assertFalse(delegate.settings.removeExifAfterCapture)
    }

    @Test
    fun cycleGridType_fromTheLastGrid_wrapsAroundToNone() {
        storedSettings.value = CameraSettings(gridType = GridType.GOLDEN_RATIO)

        val delegate = createDelegate()
        delegate.cycleGridType()

        assertEquals(GridType.NONE, storedSettings.value.gridType)
    }

    @Test
    fun toggleScanAllCodes_flipsTheStoredValue() {
        storedSettings.value = CameraSettings(scanAllCodes = false)

        val delegate = createDelegate()
        delegate.toggleScanAllCodes()

        assertTrue(storedSettings.value.scanAllCodes)
    }

    @Test
    fun perModeWrite_beforeAModeIsSlotted_isDropped() {
        val delegate = createDelegate()

        delegate.setGeoTagging(enabled = true)

        verify(exactly = 0) { settingsRepository.setGeoTagging(slot = any(), value = any()) }
        assertTrue(delegate.requireLocation)
    }

    @Test
    fun selectModeSlot_loadsThatSlotsSettings() {
        every { settingsRepository.modeSettings(SLOT) } returns SLOTTED

        val delegate = createDelegate()
        delegate.selectModeSlot(SLOT)

        assertEquals(SLOTTED, delegate.modeSettings)
    }

    @Test
    fun perModeWrite_afterAModeIsSlotted_keepsWhatTheRepositoryStored() {
        val stored = SLOTTED.copy(videoQuality = Quality.UHD)
        every { settingsRepository.modeSettings(SLOT) } returns SLOTTED
        every {
            settingsRepository.setVideoQuality(slot = SLOT, value = Quality.UHD)
        } returns stored

        val delegate = createDelegate()
        delegate.selectModeSlot(SLOT)
        delegate.setVideoQuality(Quality.UHD)

        assertEquals(stored, delegate.modeSettings)
    }

    private fun createDelegate(): ViewfinderSettingsDelegate {
        return ViewfinderSettingsDelegateImpl(settingsRepository = settingsRepository)
    }

    private companion object {
        val SLOT = ModeSlot(mode = CameraMode.VIDEO, isFrontFacing = false)
        val SLOTTED = ModeSettings(geoTagging = true, videoQuality = Quality.FHD)
    }
}
