package app.grapheneos.camera.ui.viewfinder.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val revertToMediaStoreLocation = mockk<RevertToMediaStoreLocation>()
    private val modeDelegate = mockk<ViewfinderModeDelegate>(relaxed = true)
    private val cameraDelegate = mockk<ViewfinderCameraDelegate>(relaxed = true)

    private val sessionEvents = MutableSharedFlow<CameraSessionEvent>()

    private val reverted = CompletableDeferred<Unit>()

    private var revertFinished = false

    @Before
    fun setUp() {
        coEvery { revertToMediaStoreLocation() } coAnswers {
            reverted.await()
            revertFinished = true
        }
        every { modeDelegate.defaultMode } returns CameraMode.CAMERA
        every { modeDelegate.currentMode } returns CameraMode.CAMERA
        every { cameraDelegate.sessionEvents } returns sessionEvents
    }

    @Test
    fun screenResumed_withTheProviderReady_startsTheBindBeforeReturning() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.beginBind(forced = true) }
            verify(exactly = 0) { cameraDelegate.initialize(forced = any(), extensionMode = any()) }
        }
    }

    @Test
    fun screenResumed_withoutAProvider_asksTheSessionForOne() {
        runTest {
            every { cameraDelegate.isProviderReady } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) {
                cameraDelegate.initialize(
                    forced = true,
                    extensionMode = CameraMode.CAMERA.extensionMode,
                )
            }
            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
        }
    }

    @Test
    fun providerReady_fromTheAttachedSession_startsTheBind() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            attach(viewModel)

            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 1) { cameraDelegate.beginBind(forced = true) }
        }
    }

    @Test
    fun cameraProviderUnavailable_fromTheAttachedSession_showsAMessage() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = mutableListOf<ViewfinderScreenEffect>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.effects.collect { effects += it }
            }
            attach(viewModel)

            sessionEvents.emit(CameraSessionEvent.CameraProviderUnavailable)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.camera_provider_init_failure)),
                effects,
            )
        }
    }

    @Test
    fun sessionEvents_afterDetach_areIgnored() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            attach(viewModel)
            viewModel.detach()

            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
        }
    }

    @Test
    fun storageLocationNotFound_showsTheDialogOnlyOnceTheLocationIsReverted() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = mutableListOf<ViewfinderScreenEffect>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.effects.collect { effects += it }
            }

            viewModel.onAction(CaptureAction.StorageLocationNotFound)

            assertTrue(effects.isEmpty())

            reverted.complete(Unit)

            assertEquals(listOf(ViewfinderScreenEffect.ShowStorageLocationNotFound), effects)
            coVerify(exactly = 1) { revertToMediaStoreLocation() }
        }
    }

    @Test
    fun storageLocationNotFound_finishesTheRevertEvenWhenTheScreenGoesAway() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(CaptureAction.StorageLocationNotFound)
            viewModel.viewModelScope.cancel()
            reverted.complete(Unit)

            assertTrue(revertFinished)
        }
    }

    private fun createViewModel(applicationScope: CoroutineScope): ViewfinderViewModel {
        return ViewfinderViewModel(
            entryPoint = ENTRY_POINT,
            settingsDelegate = mockk<ViewfinderSettingsDelegate>(relaxed = true),
            modeDelegate = modeDelegate,
            cameraDelegate = cameraDelegate,
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            uiStateMapper = mockk(relaxed = true),
            cameraBindSettingsMapper = mockk(relaxed = true),
            applicationScope = applicationScope,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private fun attach(viewModel: ViewfinderViewModel) {
        viewModel.attach(
            environment = mockk(relaxed = true),
            effects = mockk(relaxed = true),
            chrome = mockk(relaxed = true),
            session = mockk(relaxed = true),
        )
    }

    private companion object {
        val ENTRY_POINT = CameraEntryPoint(
            isSecureSession = false,
            isCaptureSession = false,
            isVideoOnlySession = false,
            requiresVideoModeOnly = false,
            allowsQrScanning = true,
            showsCameraModeTabs = true,
        )
    }
}
