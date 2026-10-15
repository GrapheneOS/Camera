package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.PhotoResolutions
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderSettingsDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository = settingsRepositoryOver(storedSettings)

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun bind_startsFromWhatTheRepositoryHolds() {
        runTest {
            storedSettings.value = CameraSettings(gridType = GridType.FOUR_BY_FOUR)

            createDelegate()

            assertEquals(GridType.FOUR_BY_FOUR, stateHolder.state.value.settings.gridType)
        }
    }

    @Test
    fun bind_calledAgain_keepsTheFirstStateHolder() {
        runTest {
            val delegate = createDelegate()
            val otherStateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

            delegate.bind(
                scope = backgroundScope,
                stateHolder = otherStateHolder,
            )
            storedSettings.value = CameraSettings(gridType = GridType.FOUR_BY_FOUR)

            assertEquals(GridType.FOUR_BY_FOUR, stateHolder.state.value.settings.gridType)
            assertEquals(CameraSettings(), otherStateHolder.state.value.settings)
        }
    }

    @Test
    fun settings_changedElsewhere_reachTheState() {
        runTest {
            createDelegate()

            storedSettings.value = CameraSettings(removeExifAfterCapture = false)

            assertFalse(stateHolder.state.value.settings.removeExifAfterCapture)
        }
    }

    @Test
    fun setSelfTimerDuration_isInTheStateBeforeItReturns() {
        runTest {
            every { settingsRepository.update(transform = any()) } answers {
                firstArg<(CameraSettings) -> CameraSettings>().invoke(storedSettings.value)
            }

            val delegate = createDelegate()

            delegate.setSelfTimerDuration(seconds = SELF_TIMER_SECONDS)

            assertEquals(
                SELF_TIMER_SECONDS,
                stateHolder.state.value.settings.selfTimerDurationSeconds,
            )
        }
    }

    @Test
    fun perModeWrite_beforeAModeIsSlotted_isDropped() {
        runTest {
            val delegate = createDelegate()

            delegate.setSelfIllumination(enabled = true)

            verify(exactly = 0) {
                settingsRepository.setSelfIllumination(slot = any(), value = any())
            }
            assertEquals(ModeSettings(), stateHolder.state.value.modeSettings)
        }
    }

    @Test
    fun selectModeSlot_loadsThatSlotsSettings() {
        runTest {
            every { settingsRepository.modeSettings(SLOT) } returns SLOTTED

            val delegate = createDelegate()
            delegate.selectModeSlot(SLOT)

            assertEquals(SLOTTED, stateHolder.state.value.modeSettings)
        }
    }

    @Test
    fun perModeWrite_afterAModeIsSlotted_keepsWhatTheRepositoryStored() {
        runTest {
            val stored = SLOTTED.copy(videoQuality = VideoQuality.UHD)
            every { settingsRepository.modeSettings(SLOT) } returns SLOTTED
            every {
                settingsRepository.setVideoQuality(slot = SLOT, value = VideoQuality.UHD)
            } returns stored

            val delegate = createDelegate()
            delegate.selectModeSlot(SLOT)
            delegate.setVideoQuality(VideoQuality.UHD)

            assertEquals(stored, stateHolder.state.value.modeSettings)
        }
    }

    @Test
    fun openCameraSettings_opensThemWithWhatTheCameraSupports() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)

            delegate.openCameraSettings(CAPABILITIES)

            assertEquals(
                listOf(ViewfinderScreenEffect.Settings.OpenCameraSettings(CAPABILITIES)),
                effects,
            )
        }
    }

    @Test
    fun openCameraSettings_whileRecording_saysSoInstead() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            delegate.openCameraSettings(CAPABILITIES)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(
                        R.string.camera_settings_unavailable_during_recording,
                    ),
                ),
                effects,
            )
        }
    }

    private fun TestScope.createDelegate(): ViewfinderSettingsDelegate {
        val delegate = ViewfinderSettingsDelegateImpl(
            settingsRepository = settingsRepository,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )

        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )

        return delegate
    }

    private companion object {
        const val SELF_TIMER_SECONDS = 5

        val CAPABILITIES = CameraCapabilities(
            lensFeatures = mapOf(
                LensFacing.BACK to LensFeatures(
                    isZslSupported = true,
                    isVideoStabilizationSupported = true,
                ),
            ),
            photoResolutions = PhotoResolutions(
                standardMegapixels = 12,
                fullMegapixels = 50,
            ),
        )

        val SLOT = ModeSlot(mode = CameraMode.VIDEO, isFrontFacing = false)
        val SLOTTED = ModeSettings(selfIllumination = true, videoQuality = VideoQuality.FHD)
    }
}
