package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelSettingsTest : ViewfinderViewModelTestBase() {

    @Test
    fun stabilizationToggled_storesTheSettingBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.StabilizationToggled(enabled = true))

            verifyOrder {
                settingsDelegate.setEnableEis(true)
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun focusLockToggled_storesTheSettingBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.FocusLockToggled(enabled = true))

            verifyOrder {
                settingsDelegate.setWaitForFocusLock(true)
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun aspectRatioToggleClicked_storesTheOtherRatioBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3))
            }

            viewModel.onAction(CameraAction.AspectRatioToggleClicked)

            verifyOrder {
                settingsDelegate.setAspectRatio(AspectRatio.RATIO_16_9)
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun videoQualitySelected_theOneAlreadyInUse_leavesTheCameraAlone() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(videoQuality = VideoQuality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(VideoQuality.UHD))

            verify(exactly = 0) { settingsDelegate.setVideoQuality(any()) }
            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
        }
    }

    @Test
    fun videoQualitySelected_anotherOne_storesItBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(videoQuality = VideoQuality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(VideoQuality.FHD))

            verifyOrder {
                settingsDelegate.setVideoQuality(VideoQuality.FHD)
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun flashToggleClicked_withoutAFlash_saysSoAndStoresNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
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
                applicationScope = backgroundScope,
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
            val viewModel = createViewModel(applicationScope = backgroundScope)
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

    @Test
    fun previewStreamingStarted_storedGeoTaggingWithoutPermission_staysOff() {
        runTest {
            every { locationRepository.shouldAskForPermission() } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(geoTagging = true))
            }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(false) }
            assertTrue(ViewfinderScreenEffect.SetLocationUpdates(enabled = false) in effects)
        }
    }

    @Test
    fun previewStreamingStarted_storedGeoTaggingWithPermission_turnsLocationUpdatesOn() {
        runTest {
            every { locationRepository.shouldAskForPermission() } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(geoTagging = true))
            }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(true) }
            assertTrue(ViewfinderScreenEffect.SetLocationUpdates(enabled = true) in effects)
        }
    }
}
