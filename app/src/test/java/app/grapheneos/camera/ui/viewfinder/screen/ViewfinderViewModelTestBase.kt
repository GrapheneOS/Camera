package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule

open class ViewfinderViewModelTestBase {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected val revertToMediaStoreLocation = mockk<RevertToMediaStoreLocation>()
    protected val settingsDelegate = mockk<ViewfinderSettingsDelegate>(relaxed = true)
    protected val modeDelegate = mockk<ViewfinderModeDelegate>(relaxed = true)
    protected val cameraDelegate = mockk<ViewfinderCameraDelegate>(relaxed = true)
    protected val captureDelegate = mockk<ViewfinderCaptureDelegate>(relaxed = true)
    protected val recordingDelegate = mockk<ViewfinderRecordingDelegate>(relaxed = true)
    protected val locationRepository = mockk<LocationRepository>(relaxed = true)

    protected val sessionEvents = MutableSharedFlow<CameraSessionEvent>()
    protected val captureEvents = MutableSharedFlow<CapturedImageEvent>()
    protected val recordingEvents = MutableSharedFlow<RecordedVideoEvent>()

    protected val reverted = CompletableDeferred<Unit>()

    protected var revertFinished = false

    protected lateinit var stateHolder: ViewfinderStateHolder

    @Before
    fun setUp() {
        coEvery { revertToMediaStoreLocation() } coAnswers {
            reverted.await()
            revertFinished = true
        }
        every { modeDelegate.defaultMode } returns CameraMode.CAMERA
        every { cameraDelegate.sessionEvents } returns sessionEvents
        every { captureDelegate.captureEvents } returns captureEvents
        every { recordingDelegate.recordingEvents } returns recordingEvents
    }

    protected fun createViewModel(
        applicationScope: CoroutineScope,
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): ViewfinderViewModel {
        val viewModel = ViewfinderViewModel(
            entryPoint = entryPoint,
            settingsDelegate = settingsDelegate,
            modeDelegate = modeDelegate,
            cameraDelegate = cameraDelegate,
            captureDelegate = captureDelegate,
            recordingDelegate = recordingDelegate,
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            locationRepository = locationRepository,
            uiStateMapper = mockk(relaxed = true),
            cameraBindSettingsMapper = mockk(relaxed = true),
            applicationScope = applicationScope,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )

        val boundStateHolder = slot<ViewfinderStateHolder>()
        verify { modeDelegate.bind(capture(boundStateHolder)) }
        stateHolder = boundStateHolder.captured

        return viewModel
    }

    protected fun TestScope.collectEffects(
        viewModel: ViewfinderViewModel,
    ): List<ViewfinderScreenEffect> {
        val effects = mutableListOf<ViewfinderScreenEffect>()

        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.effects.collect { effects += it }
        }

        return effects
    }
}
