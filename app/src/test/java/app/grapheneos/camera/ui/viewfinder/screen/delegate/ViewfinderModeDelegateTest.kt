package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderModeDelegateTest {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    @Test
    fun defaultMode_usesNoExtension() {
        val delegate = createDelegate()

        assertEquals(ExtensionMode.NONE, delegate.defaultMode.extensionMode)
    }

    @Test
    fun select_theModeAlreadyCurrent_reportsNoChange() {
        val delegate = createDelegate()

        val changed = delegate.select(delegate.defaultMode)

        assertFalse(changed)
    }

    @Test
    fun select_anotherMode_makesItCurrent() {
        val delegate = createDelegate()

        val changed = delegate.select(CameraMode.QR_SCAN)

        assertTrue(changed)
        assertEquals(CameraMode.QR_SCAN, stateHolder.state.value.mode)
    }

    private fun createDelegate(): ViewfinderModeDelegate {
        val delegate = ViewfinderModeDelegateImpl()

        delegate.bind(stateHolder)

        return delegate
    }
}
