package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.immediateScope
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderPreviewControlsState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderPreviewControlsDelegateTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun exposurePanel_showsImmediately() {
        runTest {
            createDelegate().showExposurePanel()

            assertEquals(
                ViewfinderPreviewControlsState(
                    isExposurePanelShown = true,
                ),
                state().previewControls,
            )
        }
    }

    @Test
    fun exposurePanel_hidesTwoSecondsAfterItWasLastShown() {
        runTest {
            val delegate = createDelegate()

            delegate.showExposurePanel()
            advanceTimeBy(1.5.seconds)
            delegate.showExposurePanel()
            advanceTimeBy(1.5.seconds)
            assertTrue(state().previewControls.isExposurePanelShown)

            advanceTimeBy(1.seconds)
            assertFalse(state().previewControls.isExposurePanelShown)
        }
    }

    @Test
    fun exposurePanel_hiddenEarly_staysHidden() {
        runTest {
            val delegate = createDelegate()

            delegate.showExposurePanel()
            delegate.hideExposurePanel()
            advanceTimeBy(3.seconds)
            assertFalse(state().previewControls.isExposurePanelShown)
        }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun TestScope.createDelegate(): ViewfinderPreviewControlsDelegate {
        val delegate = ViewfinderPreviewControlsDelegateImpl()

        delegate.bind(
            scope = immediateScope(),
            stateHolder = stateHolder,
        )

        return delegate
    }
}
