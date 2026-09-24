package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
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

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = CameraMode.CAMERA, requiresVideoModeOnly = false),
    )

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
    fun dialog_isShownUntilDismissed() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.CAMERA)
            assertEquals(AppPermission.CAMERA, state().permissionDialog)
            assertEquals(
                listOf(
                    Effect.Permission.ShowDialog(
                        permission = AppPermission.CAMERA,
                        offersSettings = true,
                    ),
                ),
                effects,
            )

            delegate.dismissDialog()
            assertNull(state().permissionDialog)
        }
    }

    @Test
    fun dialog_alreadyUp_isNotStackedAgain() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.CAMERA)
            delegate.showDialog(AppPermission.CAMERA)

            assertEquals(1, effects.size)
        }
    }

    @Test
    fun dialog_inALockscreenSession_offersNoSettings() {
        runTest {
            val effects = collectEffects(stateHolder)

            createDelegate(isSecureSession = true).showDialog(AppPermission.LOCATION)

            assertEquals(
                listOf(
                    Effect.Permission.ShowDialog(
                        permission = AppPermission.LOCATION,
                        offersSettings = false,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun refresh_closesTheDialogOfAPermissionGrantedMeanwhile() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.CAMERA)
            effects.clear()

            granted += AppPermission.CAMERA
            delegate.refresh()

            assertNull(state().permissionDialog)
            assertEquals(listOf(Effect.Permission.DismissDialog), effects)
        }
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
    fun openSettings_closesTheDialogOnTheWay() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.LOCATION)
            effects.clear()

            delegate.openSettings()

            assertNull(state().permissionDialog)
            assertEquals(listOf(Effect.Permission.OpenSettings), effects)
        }
    }

    @Test
    fun cameraDialogDismissed_closesTheScreen() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.CAMERA)
            effects.clear()

            delegate.onDialogDismissed(AppPermission.CAMERA)

            assertNull(state().permissionDialog)
            assertEquals(listOf(Effect.CloseScreen), effects)
        }
    }

    @Test
    fun microphoneDialogDismissed_keepsTheScreen() {
        runTest {
            val effects = collectEffects(stateHolder)
            val delegate = createDelegate()

            delegate.showDialog(AppPermission.MICROPHONE)
            effects.clear()

            delegate.onDialogDismissed(AppPermission.MICROPHONE)

            assertTrue(effects.isEmpty())
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
            entryPoint = CameraEntryPoint(
                isSecureSession = isSecureSession,
                isCaptureSession = false,
                isVideoOnlySession = false,
                requiresVideoModeOnly = false,
                allowsQrScanning = true,
                showsCameraModeTabs = true,
            ),
        )
        delegate.bind(stateHolder)

        return delegate
    }
}
