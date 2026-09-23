package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderPermissionDelegateTest {

    private val permissionRepository = mockk<PermissionRepository>()

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )

    private val granted = mutableSetOf<AppPermission>()

    @Test
    fun refresh_recordsWhatIsMissing() {
        granted += AppPermission.CAMERA

        createDelegate().refresh()

        assertEquals(setOf(AppPermission.MICROPHONE), state().missingPermissions)
    }

    @Test
    fun dialog_isShownUntilDismissed() {
        val delegate = createDelegate()

        delegate.showDialog(AppPermission.CAMERA)
        assertEquals(AppPermission.CAMERA, state().permissionDialog)

        delegate.dismissDialog()
        assertNull(state().permissionDialog)
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun createDelegate(): ViewfinderPermissionDelegate {
        every { permissionRepository.isGranted(any()) } answers {
            firstArg<AppPermission>() in granted
        }

        val delegate = ViewfinderPermissionDelegateImpl(permissionRepository = permissionRepository)
        delegate.bind(stateHolder)

        return delegate
    }
}
