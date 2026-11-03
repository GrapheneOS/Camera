package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LocationAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelSettingsTest : ViewfinderViewModelTestBase() {

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
    fun enableLocationClicked_opensTheLocationSettings() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(LocationAction.EnableLocationClicked)

            assertEquals(listOf(ViewfinderScreenEffect.Location.OpenSettings), effects)
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
}
