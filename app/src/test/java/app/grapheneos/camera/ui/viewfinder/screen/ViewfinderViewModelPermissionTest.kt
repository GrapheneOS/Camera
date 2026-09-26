package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelPermissionTest : ViewfinderViewModelTestBase() {

    @Test
    fun screenResumed_withoutTheCameraPermission_asksForItInsteadOfStartingTheCamera() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel()
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.CAMERA)) }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            verify(exactly = 1) {
                permissionDelegate.request(
                    permission = AppPermission.CAMERA,
                    explainsFirst = true,
                )
            }
        }
    }

    @Test
    fun screenResumed_overAQrResult_keepsTheCameraItHas() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel()
            stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = true)) }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = false) }
        }
    }

    @Test
    fun screenResumed_whileACaptureSessionRecordingIsSaved_keepsTheCameraItHas() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(
                    isCaptureSession = true,
                    requiresVideoModeOnly = true,
                    recording = it.recording.copy(isSaving = true),
                )
            }

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = false) }
        }
    }

    @Test
    fun rationaleRequired_showsThePermissionDialog() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(PermissionAction.RationaleRequired(AppPermission.CAMERA))

            verify(exactly = 1) { permissionDelegate.showDialog(AppPermission.CAMERA) }
        }
    }

    @Test
    fun dialogDismissed_afterTheDialogWasAlreadyClosed_doesNothing() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(PermissionAction.DialogDismissed(AppPermission.CAMERA))

            verify(exactly = 0) { permissionDelegate.onDialogDismissed(any()) }
        }
    }

    @Test
    fun cameraPermissionAnswered_leavesStartingTheCameraToTheResume() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel()
            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.CAMERA))

            verify(exactly = 1) { permissionDelegate.refresh() }
            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
        }
    }

    @Test
    fun microphonePermissionAnswered_withAGrant_rebindsAndRetriesOnceStreaming() {
        runTest {
            val viewModel = createViewModel()

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
            val viewModel = createViewModel()
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.MICROPHONE)) }

            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.MICROPHONE))

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            verify(exactly = 1) { permissionDelegate.showDialog(AppPermission.MICROPHONE) }
        }
    }
}
