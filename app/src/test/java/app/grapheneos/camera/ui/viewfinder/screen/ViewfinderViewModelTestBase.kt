package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraControlsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderDialogDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderGalleryDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderLocationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeSwitchDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderOrientationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderPermissionDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderPreviewControlsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderScreenWakeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.VolumeKeyActionMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCameraEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderLevelState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule

open class ViewfinderViewModelTestBase {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected val settingsDelegate = mockk<ViewfinderSettingsDelegate>(relaxed = true)
    protected val modeDelegate = mockk<ViewfinderModeDelegate>(relaxed = true)
    protected val cameraDelegate = mockk<ViewfinderCameraDelegate>(relaxed = true)
    protected val captureDelegate = mockk<ViewfinderCaptureDelegate>(relaxed = true)
    protected val recordingDelegate = mockk<ViewfinderRecordingDelegate>(relaxed = true)
    protected val permissionDelegate = mockk<ViewfinderPermissionDelegate>(relaxed = true)
    protected val locationDelegate = mockk<ViewfinderLocationDelegate>(relaxed = true)
    protected val orientationDelegate = mockk<ViewfinderOrientationDelegate>(relaxed = true)
    protected val screenWakeDelegate = mockk<ViewfinderScreenWakeDelegate>(relaxed = true)
    protected val galleryDelegate = mockk<ViewfinderGalleryDelegate>(relaxed = true)
    protected val dialogDelegate = mockk<ViewfinderDialogDelegate>(relaxed = true)
    protected val modeSwitchDelegate = mockk<ViewfinderModeSwitchDelegate>(relaxed = true)
    protected val cameraControlsDelegate = mockk<ViewfinderCameraControlsDelegate>(
        relaxed = true,
    )
    protected val previewControlsDelegate = mockk<ViewfinderPreviewControlsDelegate>(
        relaxed = true,
    )

    protected val uiStateMapper = mockk<ViewfinderUiStateMapper>(relaxed = true)
    protected val cameraBindSettingsMapper = mockk<CameraBindSettingsMapper>(relaxed = true)
    protected val volumeKeyActionMapper = mockk<VolumeKeyActionMapper>(relaxed = true)
    protected val swipeOutcomeMapper = mockk<SwipeOutcomeMapper>()

    protected val cameraEvents = MutableSharedFlow<ViewfinderCameraEvent>()
    protected val captureEvents = MutableSharedFlow<ViewfinderCaptureEvent>()
    protected val zoomChanges = MutableSharedFlow<Unit>()
    protected val recordingEvents = MutableSharedFlow<ViewfinderRecordingEvent>()

    protected lateinit var stateHolder: ViewfinderStateHolder
    protected lateinit var levelStateHolder: StateHolder<ViewfinderLevelState>

    private val viewModels = mutableListOf<ViewfinderViewModel>()

    @Before
    fun setUp() {
        every { modeDelegate.defaultMode } returns CameraMode.CAMERA
        every { cameraDelegate.cameraEvents } returns cameraEvents
        every { captureDelegate.captureEvents } returns captureEvents
        every { cameraControlsDelegate.zoomChanges } returns zoomChanges
        every { recordingDelegate.recordingEvents } returns recordingEvents
    }

    @After
    fun tearDown() {
        viewModels.forEach { viewModel -> viewModel.viewModelScope.cancel() }
    }

    protected fun createViewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
        outputUri: Uri? = null,
    ): ViewfinderViewModel {
        val viewModel = ViewfinderViewModel(
            outputUri = outputUri,
            entryPoint = entryPoint,
            settingsDelegate = settingsDelegate,
            modeDelegate = modeDelegate,
            cameraDelegate = cameraDelegate,
            cameraControlsDelegate = cameraControlsDelegate,
            captureDelegate = captureDelegate,
            recordingDelegate = recordingDelegate,
            permissionDelegate = permissionDelegate,
            locationDelegate = locationDelegate,
            orientationDelegate = orientationDelegate,
            screenWakeDelegate = screenWakeDelegate,
            galleryDelegate = galleryDelegate,
            dialogDelegate = dialogDelegate,
            modeSwitchDelegate = modeSwitchDelegate,
            previewControlsDelegate = previewControlsDelegate,
            uiStateMapper = uiStateMapper,
            cameraBindSettingsMapper = cameraBindSettingsMapper,
            volumeKeyActionMapper = volumeKeyActionMapper,
            swipeOutcomeMapper = swipeOutcomeMapper,
        )

        viewModels += viewModel

        val boundStateHolder = slot<ViewfinderStateHolder>()
        verify { modeDelegate.bind(capture(boundStateHolder)) }
        stateHolder = boundStateHolder.captured

        val boundLevelStateHolder = slot<StateHolder<ViewfinderLevelState>>()
        verify { orientationDelegate.bind(any(), capture(boundLevelStateHolder)) }
        levelStateHolder = boundLevelStateHolder.captured

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
