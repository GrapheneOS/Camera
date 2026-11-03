package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderFocusRequest
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderPreviewControlsState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun focus_showsTheRingAtThePointAndBothPanels() {
        runTest {
            createDelegate().showFocus(x = 10f, y = 20f)

            assertEquals(
                ViewfinderPreviewControlsState(
                    focusRequest = ViewfinderFocusRequest(x = 10f, y = 20f, id = 1),
                    isZoomPanelShown = true,
                    isExposurePanelShown = true,
                ),
                state().previewControls,
            )
        }
    }

    @Test
    fun focus_atTheSamePointAgain_isANewRequest() {
        runTest {
            val delegate = createDelegate()

            delegate.showFocus(x = 10f, y = 20f)
            delegate.showFocus(x = 10f, y = 20f)

            assertEquals(2, state().previewControls.focusRequest?.id)
        }
    }

    @Test
    fun focusRing_outlivesThePanels() {
        runTest {
            createDelegate().showFocus(x = 10f, y = 20f)
            advanceTimeBy(3.seconds)

            assertFalse(state().previewControls.isZoomPanelShown)
            assertEquals(1, state().previewControls.focusRequest?.id)
        }
    }

    @Test
    fun zoomPanel_hidesTwoSecondsAfterItWasLastShown() {
        runTest {
            val delegate = createDelegate()

            delegate.showZoomPanel()
            advanceTimeBy(1.seconds)
            delegate.showZoomPanel()
            advanceTimeBy(1.5.seconds)
            assertTrue(state().previewControls.isZoomPanelShown)

            advanceTimeBy(1.seconds)
            assertFalse(state().previewControls.isZoomPanelShown)
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
    fun panels_hiddenEarly_stayHiddenAndKeepTheirOwnTimeouts() {
        runTest {
            val delegate = createDelegate()

            delegate.showFocus(x = 10f, y = 20f)
            delegate.hideZoomPanel()
            assertFalse(state().previewControls.isZoomPanelShown)
            assertTrue(state().previewControls.isExposurePanelShown)

            delegate.hideExposurePanel()
            advanceTimeBy(3.seconds)
            assertFalse(state().previewControls.isZoomPanelShown)
            assertFalse(state().previewControls.isExposurePanelShown)
        }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun TestScope.createDelegate(): ViewfinderPreviewControlsDelegate {
        val delegate = ViewfinderPreviewControlsDelegateImpl(
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )

        return delegate
    }
}
