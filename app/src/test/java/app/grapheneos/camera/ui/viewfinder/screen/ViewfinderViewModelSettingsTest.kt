package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelSettingsTest : ViewfinderViewModelTestBase() {

    @Test
    fun cameraSettingsClicked_opensThemWithWhatTheCameraSupports() {
        runTest {
            val capabilities = CameraCapabilities(
                lensFeatures = mapOf(LensFacing.BACK to LensFeatures(isZslSupported = true)),
            )
            every { cameraDelegate.capabilities() } returns capabilities
            val viewModel = createViewModel()

            viewModel.onAction(SettingsAction.CameraSettingsClicked)

            verify { settingsDelegate.openCameraSettings(capabilities) }
        }
    }

    @Test
    fun cameraSettingsClicked_inADialog_closesItAndOpensThem() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(DialogAction.Choice.CameraSettingsClicked)

            verifyOrder {
                dialogDelegate.close()
                settingsDelegate.openCameraSettings(any())
            }
        }
    }

    @Test
    fun selfIlluminationToggled_storesItWithoutTellingTheScreen() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(SettingsAction.SelfIlluminationToggled(enabled = true))

            verify { settingsDelegate.setSelfIllumination(true) }
            assertEquals(emptyList<ViewfinderScreenEffect>(), effects)
        }
    }

    @Test
    fun enableLocationClicked_opensTheLocationSettings() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(SettingsAction.EnableLocationClicked)

            assertEquals(listOf(ViewfinderScreenEffect.Location.OpenSettings), effects)
        }
    }

    @Test
    fun aspectRatioToggleClicked_storesTheOtherRatio() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3))
            }

            viewModel.onAction(CameraAction.AspectRatioToggleClicked)

            verify { settingsDelegate.setAspectRatio(AspectRatio.RATIO_16_9) }
        }
    }

    @Test
    fun videoQualitySelected_storesIt() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(SettingsAction.VideoQualitySelected(VideoQuality.FHD))

            verify { settingsDelegate.setVideoQuality(VideoQuality.FHD) }
        }
    }

    @Test
    fun rebindRequests_rebindTheCameraOnlyWhileTheScreenIsResumed() {
        runTest {
            val rebindRequests = MutableSharedFlow<Unit>()
            every { settingsDelegate.rebindRequests() } returns rebindRequests
            every { cameraDelegate.isProviderReady } returns false
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenResumed)
            rebindRequests.emit(Unit)
            viewModel.onAction(LifecycleAction.ScreenPaused)
            rebindRequests.emit(Unit)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = true) }
        }
    }

    @Test
    fun flashToggleClicked_withoutAFlash_saysSoAndStoresNothing() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.FlashToggleClicked)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(R.string.flash_unavailable_in_selected_mode),
                ),
                effects,
            )
            verify(exactly = 0) { settingsDelegate.setFlashMode(any()) }
        }
    }

    @Test
    fun flashToggleClicked_inAVideoOnlyEntryPoint_saysSoEvenWithAFlash() {
        runTest {
            val viewModel = createViewModel(
                entryPoint = cameraEntryPoint(requiresVideoModeOnly = true),
            )
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(session = ViewfinderSessionState(isFlashAvailable = true))
            }

            viewModel.onAction(CameraAction.FlashToggleClicked)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.flash_switch_unsupported)),
                effects,
            )
            verify(exactly = 0) { settingsDelegate.setFlashMode(any()) }
        }
    }

    @Test
    fun flashToggleClicked_withAFlashThatIsOff_turnsItOn() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(
                    session = ViewfinderSessionState(isFlashAvailable = true),
                    flashMode = FlashMode.OFF,
                )
            }

            viewModel.onAction(CameraAction.FlashToggleClicked)

            verifyOrder {
                settingsDelegate.setFlashMode(FlashMode.ON)
                cameraDelegate.applyFlashMode(FlashMode.ON)
            }
        }
    }
}
