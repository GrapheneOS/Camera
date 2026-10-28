package app.grapheneos.camera.ui.quicksettings.screen

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.TorchControl
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository = settingsRepositoryOver(storedSettings)

    private val boundCamera = MutableStateFlow<BoundCamera?>(PHOTO_CAMERA)

    private val session = mockk<CameraSession>(relaxed = true) {
        every { boundCamera } returns this@QuickSettingsViewModelTest.boundCamera
        every { capabilities() } returns CAPABILITIES
    }

    private val torch = mockk<TorchControl>(relaxed = true)

    @Test
    fun modeSettings_followTheSlotOfTheBoundCamera() {
        runTest {
            every { settingsRepository.modeSettings(FRONT_VIDEO_SLOT) } returns STORED_FRONT_VIDEO
            val viewModel = viewModel()

            boundCamera.value = FRONT_VIDEO_CAMERA

            assertEquals(VideoQuality.HD, viewModel.uiState.value.videoQuality)
        }
    }

    @Test
    fun flashToggleClicked_withAFlashThatIsOff_storesOnForTheBoundSlot() {
        runTest {
            every { settingsRepository.modeSettings(PHOTO_SLOT) } returns ModeSettings(
                flashMode = FlashMode.OFF,
            )
            every {
                settingsRepository.setFlashMode(slot = any(), value = any())
            } returns ModeSettings()
            val viewModel = viewModel()

            viewModel.onAction(Action.FlashToggleClicked)

            verify { settingsRepository.setFlashMode(slot = PHOTO_SLOT, value = FlashMode.ON) }
        }
    }

    @Test
    fun flashToggleClicked_withoutAFlash_saysSoAndStoresNothing() {
        runTest {
            boundCamera.value = PHOTO_CAMERA.copy(hasFlashUnit = false)
            val viewModel = viewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.FlashToggleClicked)

            assertEquals(
                listOf(Effect.ShowMessage(R.string.flash_unavailable_in_selected_mode)),
                effects,
            )
            verify(exactly = 0) { settingsRepository.setFlashMode(slot = any(), value = any()) }
        }
    }

    @Test
    fun flashToggleClicked_inAVideoOnlyEntryPoint_saysSoEvenWithAFlash() {
        runTest {
            val viewModel = viewModel(
                entryPoint = cameraEntryPoint(requiresVideoModeOnly = true),
            )
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.FlashToggleClicked)

            assertEquals(listOf(Effect.ShowMessage(R.string.flash_switch_unsupported)), effects)
            verify(exactly = 0) { settingsRepository.setFlashMode(slot = any(), value = any()) }
        }
    }

    @Test
    fun aspectRatioToggleClicked_storesTheOtherRatio() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.AspectRatioToggleClicked)

            assertEquals(AspectRatio.RATIO_16_9, storedSettings.value.aspectRatio)
        }
    }

    @Test
    fun selfTimerSelected_storesIt() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SelfTimerSelected(seconds = SELF_TIMER_SECONDS))

            assertEquals(SELF_TIMER_SECONDS, storedSettings.value.selfTimerDurationSeconds)
        }
    }

    @Test
    fun videoQualitySelected_storesItForTheBoundSlot() {
        runTest {
            boundCamera.value = FRONT_VIDEO_CAMERA
            every {
                settingsRepository.setVideoQuality(slot = any(), value = any())
            } returns ModeSettings()
            val viewModel = viewModel()

            viewModel.onAction(Action.VideoQualitySelected(VideoQuality.UHD))

            verify {
                settingsRepository.setVideoQuality(
                    slot = FRONT_VIDEO_SLOT,
                    value = VideoQuality.UHD,
                )
            }
        }
    }

    @Test
    fun modeWrites_beforeACameraIsBound_areDropped() {
        runTest {
            boundCamera.value = null
            val viewModel = viewModel()

            viewModel.onAction(Action.SelfIlluminationToggled(enabled = true))

            verify(exactly = 0) {
                settingsRepository.setSelfIllumination(slot = any(), value = any())
            }
        }
    }

    @Test
    fun cameraSettingsClicked_opensThemWithWhatTheCameraSupports() {
        runTest {
            val viewModel = viewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.CameraSettingsClicked)

            assertEquals(listOf(Effect.OpenCameraSettings(CAPABILITIES)), effects)
        }
    }

    private fun viewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): QuickSettingsViewModel {
        return QuickSettingsViewModel(
            entryPoint = entryPoint,
            settingsRepository = settingsRepository,
            session = session,
            torch = torch,
            uiStateMapper = QuickSettingsUiStateMapperImpl(),
        )
    }

    private fun TestScope.collectEffects(viewModel: QuickSettingsViewModel): List<Effect> {
        val effects = mutableListOf<Effect>()

        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.effects.toList(effects)
        }

        return effects
    }

    private companion object {
        const val SELF_TIMER_SECONDS = 5

        val CAPABILITIES = CameraCapabilities(lensFacing = LensFacing.BACK)

        val PHOTO_CAMERA = BoundCamera(
            mode = CameraMode.CAMERA,
            isVideoMode = false,
            lensFacing = LensFacing.BACK,
            hasFlashUnit = true,
            videoQualities = emptyList(),
        )

        val FRONT_VIDEO_CAMERA = BoundCamera(
            mode = CameraMode.VIDEO,
            isVideoMode = true,
            lensFacing = LensFacing.FRONT,
            hasFlashUnit = false,
            videoQualities = listOf(VideoQuality.FHD, VideoQuality.HD),
        )

        val PHOTO_SLOT = ModeSlot(mode = CameraMode.CAMERA, isFrontFacing = false)
        val FRONT_VIDEO_SLOT = ModeSlot(mode = CameraMode.VIDEO, isFrontFacing = true)

        val STORED_FRONT_VIDEO = ModeSettings(videoQuality = VideoQuality.HD)
    }
}
