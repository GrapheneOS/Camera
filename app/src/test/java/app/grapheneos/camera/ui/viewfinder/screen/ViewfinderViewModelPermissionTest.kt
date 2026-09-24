package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
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
class ViewfinderViewModelPermissionTest : ViewfinderViewModelTestBase() {

    @Test
    fun screenResumed_withoutTheCameraPermission_asksForItInsteadOfStartingTheCamera() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.CAMERA)) }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Permission.Request(
                        permission = AppPermission.CAMERA,
                        explainsFirst = true,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun screenResumed_overAQrResult_keepsTheCameraItHas() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = true)) }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = false) }
        }
    }

    @Test
    fun screenResumed_whileACaptureSessionRecordingIsSaved_keepsTheCameraItHas() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(
                    isCaptureSession = true,
                    requiresVideoModeOnly = true,
                    capture = it.capture.copy(isSavingRecording = true),
                )
            }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = false) }
        }
    }

    @Test
    fun screenResumed_withTheExplainedPermissionGrantedMeanwhile_closesItsDialog() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(permissionDialog = AppPermission.CAMERA) }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { permissionDelegate.dismissDialog() }
            assertEquals(listOf(ViewfinderScreenEffect.Permission.DismissDialog), effects)
        }
    }

    @Test
    fun rationaleRequired_showsThePermissionDialog() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(PermissionAction.RationaleRequired(AppPermission.CAMERA))

            verify(exactly = 1) { permissionDelegate.showDialog(AppPermission.CAMERA) }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Permission.ShowDialog(
                        permission = AppPermission.CAMERA,
                        offersSettings = true,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun rationaleRequired_withTheDialogAlreadyUp_doesNotStackAnother() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(permissionDialog = AppPermission.CAMERA) }

            viewModel.onAction(PermissionAction.RationaleRequired(AppPermission.CAMERA))

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun settingsClicked_closesTheDialogAndOpensTheSettings() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(PermissionAction.SettingsClicked)

            verify(exactly = 1) { permissionDelegate.dismissDialog() }
            assertEquals(listOf(ViewfinderScreenEffect.Permission.OpenSettings), effects)
        }
    }

    @Test
    fun cameraDialogDismissed_closesTheScreen() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(permissionDialog = AppPermission.CAMERA) }

            viewModel.onAction(PermissionAction.DialogDismissed(AppPermission.CAMERA))

            verify(exactly = 1) { permissionDelegate.dismissDialog() }
            assertEquals(listOf(ViewfinderScreenEffect.CloseScreen), effects)
        }
    }

    @Test
    fun microphoneDialogDismissed_keepsTheScreen() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(permissionDialog = AppPermission.MICROPHONE) }

            viewModel.onAction(PermissionAction.DialogDismissed(AppPermission.MICROPHONE))

            verify(exactly = 1) { permissionDelegate.dismissDialog() }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun dialogDismissed_afterTheDialogWasAlreadyClosed_doesNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(PermissionAction.DialogDismissed(AppPermission.CAMERA))

            verify(exactly = 0) { permissionDelegate.dismissDialog() }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun cameraPermissionAnswered_leavesStartingTheCameraToTheResume() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.CAMERA))

            verify(exactly = 1) { permissionDelegate.refresh() }
            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
        }
    }

    @Test
    fun microphonePermissionAnswered_withAGrant_rebindsAndRetriesOnceStreaming() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.MICROPHONE))

            verifyOrder {
                permissionDelegate.refresh()
                recordingDelegate.retryOnceStreaming()
                cameraDelegate.canBeginBind(forced = true)
            }
            verify(exactly = 0) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun microphonePermissionAnswered_withARefusal_explainsWhatAudioNeeds() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.MICROPHONE)) }

            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.MICROPHONE))

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            verify(exactly = 1) { permissionDelegate.showDialog(AppPermission.MICROPHONE) }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Permission.ShowDialog(
                        permission = AppPermission.MICROPHONE,
                        offersSettings = true,
                    ),
                ),
                effects,
            )
        }
    }
}
