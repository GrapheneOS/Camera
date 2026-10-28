package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderPermissionDelegateTest {

    private val permissionRepository = mockk<PermissionRepository>()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    private val granted = mutableSetOf<AppPermission>()

    @Test
    fun refresh_recordsWhatIsMissing() {
        runTest {
            granted += AppPermission.CAMERA

            createDelegate().refresh()

            assertEquals(
                setOf(AppPermission.MICROPHONE, AppPermission.LOCATION),
                state().missingPermissions,
            )
        }
    }

    @Test
    fun dialog_isShownForThePermission() {
        createDelegate().showDialog(AppPermission.CAMERA)

        assertEquals(ViewfinderDialog.CameraPermission(offersSettings = true), state().dialog)
    }

    @Test
    fun dialog_inALockscreenSession_offersNoSettings() {
        createDelegate(isSecureSession = true).showDialog(AppPermission.MICROPHONE)

        assertEquals(ViewfinderDialog.MicrophonePermission(offersSettings = false), state().dialog)
    }

    @Test
    fun refresh_closesTheDialogOfAPermissionGrantedMeanwhile() {
        val delegate = createDelegate()

        delegate.showDialog(AppPermission.CAMERA)
        granted += AppPermission.CAMERA
        delegate.refresh()

        assertNull(state().dialog)
    }

    @Test
    fun request_asksTheSystem() {
        runTest {
            val effects = collectEffects(stateHolder)

            createDelegate().request(permission = AppPermission.MICROPHONE, explainsFirst = false)

            assertEquals(
                listOf(
                    Effect.Permission.Request(
                        permission = AppPermission.MICROPHONE,
                        explainsFirst = false,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun openSettings_opensTheAppSettings() {
        runTest {
            val effects = collectEffects(stateHolder)

            createDelegate().openSettings()

            assertEquals(listOf(Effect.Permission.OpenSettings), effects)
        }
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun createDelegate(
        isSecureSession: Boolean = false,
    ): ViewfinderPermissionDelegate {
        every { permissionRepository.isGranted(any()) } answers {
            firstArg<AppPermission>() in granted
        }

        val delegate = ViewfinderPermissionDelegateImpl(
            permissionRepository = permissionRepository,
            entryPoint = cameraEntryPoint(isSecureSession = isSecureSession),
        )
        delegate.bind(stateHolder)

        return delegate
    }
}
