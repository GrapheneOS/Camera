package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.FocusDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.PreviewAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusSelection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewOverlaysViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = MutableStateFlow(
        CameraSettings(
            gridType = GridType.FOUR_BY_FOUR,
            gyroscopeSuggestions = false,
        ),
    )
    private val settingsRepository = mockk<SettingsRepository>()
    private val mapper = mockk<PreviewOverlaysUiStateMapper>()
    private val mappedState = slot<PreviewOverlaysState>()
    private val boundStateHolder = slot<StateHolder<PreviewOverlaysState>>()
    private val levelDelegate = mockk<LevelDelegate>(relaxed = true) {
        every { levelAfterChange(any(), any()) } answers { secondArg<PreviewOverlaysState>().level }
    }
    private val focusDelegate = mockk<FocusDelegate>(relaxed = true) {
        every { focusAfterChange(any(), any()) } answers { secondArg<PreviewOverlaysState>().focus }
    }
    private val viewModels = mutableListOf<PreviewOverlaysViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { viewModel -> viewModel.viewModelScope.cancel() }
    }

    @Test
    fun initialState_readsSessionSettingsAndWaitsForPreviewContent() {
        runTest {
            settings.value = settings.value.copy(
                focusTimeoutSeconds = 7,
                gyroscopeSuggestions = true,
            )
            createViewModel()

            assertEquals(settings.value, mappedState.captured.settings)
            assertNull(mappedState.captured.previewContent)
        }
    }

    @Test
    fun previewContentChanged_updatesUiStateBeforeReturning() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(selfTimerRemaining = 3.seconds)
            val mapped = PreviewOverlaysUiState(
                gridPattern = CompositionGridPattern.Quarters,
                selfTimerRemaining = 3.seconds,
            )
            every { mapper.map(capture(mappedState)) } returns mapped

            viewModel.onAction(PreviewAction.PreviewContentChanged(content = content))

            assertEquals(content, mappedState.captured.previewContent)
            assertSame(mapped, viewModel.uiState.value)
        }
    }

    @Test
    fun settingsChange_reachesTheDelegatesWithThePreviewAndFlashKept() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(mode = CameraMode.QR_SCAN)
            viewModel.onAction(PreviewAction.PreviewContentChanged(content = content))
            viewModel.onAction(PreviewAction.PreviewFlashVisibilityChanged(visible = true))
            val changed = settings.value.copy(gridType = GridType.GOLDEN_RATIO)

            settings.value = changed

            val expected = PreviewOverlaysState(
                settings = changed,
                previewContent = content,
                isPreviewFlashVisible = true,
            )
            assertEquals(expected, mappedState.captured)
            verify { levelDelegate.levelAfterChange(previous = any(), current = expected) }
            verify { focusDelegate.focusAfterChange(previous = any(), current = expected) }
        }
    }

    @Test
    fun endingAFlash_preservesPreviewContentAndSettings() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(
                isCaptureResultVisible = true,
                selfTimerRemaining = 3.seconds,
            )
            viewModel.onAction(PreviewAction.PreviewFlashVisibilityChanged(visible = true))
            viewModel.onAction(PreviewAction.PreviewContentChanged(content = content))
            viewModel.onAction(PreviewAction.PreviewFlashVisibilityChanged(visible = false))

            assertEquals(
                PreviewOverlaysState(
                    settings = settings.value,
                    previewContent = content,
                ),
                mappedState.captured,
            )
        }
    }

    @Test
    fun levelState_isMappedInitiallyAndWhenTheLevelChanges() {
        runTest {
            val viewModel = createViewModel()
            val initial = viewModel.levelUiState.value
            val changed = LevelState(
                visible = true,
                tiltDegrees = -3f,
                horizonDegrees = -12f,
            )
            val hidden = changed.copy(visible = false)
            val changedUiState = LevelUiState(
                visible = true,
                rollDegrees = -3f,
                pitchDegrees = 12f,
            )
            val hiddenUiState = changedUiState.copy(visible = false)
            every { mapper.mapLevel(any()) } answers {
                when (firstArg<PreviewOverlaysState>().level) {
                    changed -> changedUiState
                    hidden -> hiddenUiState
                    else -> initial
                }
            }
            val screenState = viewModel.uiState.value
            val levels = mutableListOf<LevelUiState>()
            val collection = backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.levelUiState.collect { levels += it }
            }

            assertEquals(listOf(initial), levels)

            boundStateHolder.captured.update { it.copy(level = changed) }

            assertSame(changedUiState, viewModel.levelUiState.value)
            assertSame(changedUiState, levels.last())
            assertSame(screenState, viewModel.uiState.value)

            collection.cancel()
            boundStateHolder.captured.update { it.copy(level = hidden) }

            assertSame(hiddenUiState, viewModel.levelUiState.value)
        }
    }

    @Test
    fun screenPaused_stopsTrackingAndClearsThePreviewFlash() {
        runTest {
            var started = false
            var stopped = false
            coEvery { levelDelegate.trackLevel() } coAnswers {
                started = true
                try {
                    awaitCancellation()
                } finally {
                    stopped = true
                }
            }
            val viewModel = createViewModel()
            assertFalse(started)

            viewModel.onAction(LifecycleAction.ScreenResumed)
            viewModel.onAction(PreviewAction.PreviewFlashVisibilityChanged(visible = true))

            assertTrue(started)

            viewModel.onAction(LifecycleAction.ScreenPaused)

            assertTrue(stopped)
            assertFalse(mappedState.captured.isPreviewFlashVisible)
            verify { levelDelegate.onScreenPaused() }
            verify { focusDelegate.onScreenPaused() }
        }
    }

    @Test
    fun previewAndFlashChanges_publishBothDelegatesTransitionsInOneUpdate() {
        runTest {
            val viewModel = createViewModel()
            val level = LevelState(tiltDegrees = 3f)
            val focus = FocusState(
                selection = FocusSelection(requestId = 1, x = 10f, y = 20f, visible = false),
            )
            every { levelDelegate.levelAfterChange(any(), any()) } returns level
            every { focusDelegate.focusAfterChange(any(), any()) } returns focus
            val published = mutableListOf<PreviewOverlaysState>()
            val collection = backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                boundStateHolder.captured.state.collect { published += it }
            }
            val changes = listOf(
                PreviewAction.PreviewContentChanged(PreviewContentState(mode = CameraMode.NIGHT)),
                PreviewAction.PreviewFlashVisibilityChanged(visible = true),
            )

            changes.forEach { action ->
                published.clear()

                viewModel.onAction(action)

                val changed = published.single()
                assertEquals(action.toString(), level, changed.level)
                assertEquals(action.toString(), focus, changed.focus)
            }
            collection.cancel()
        }
    }

    @Test
    fun repeatedResume_keepsOneSubscriptionAndPauseAllowsRestarting() {
        runTest {
            var trackingStarts = 0
            var focusTrackingStarts = 0
            coEvery { levelDelegate.trackLevel() } coAnswers {
                trackingStarts += 1
                awaitCancellation()
            }
            coEvery { focusDelegate.trackFocus() } coAnswers {
                focusTrackingStarts += 1
                awaitCancellation()
            }
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenResumed)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertEquals(1, trackingStarts)
            assertEquals(1, focusTrackingStarts)

            viewModel.onAction(LifecycleAction.ScreenPaused)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertEquals(2, trackingStarts)
            assertEquals(2, focusTrackingStarts)
        }
    }

    @Test
    fun displayRotation_isPublishedBeforeAnotherSensorReading() {
        runTest {
            val viewModel = createViewModel()
            val rotated = LevelUiState(rotationDegrees = 180f)
            every { mapper.mapLevel(any()) } returns rotated

            viewModel.onAction(
                PreviewAction.DisplayRotationChanged(rotation = DeviceOrientation.DEGREES_270),
            )

            assertEquals(DeviceOrientation.DEGREES_270, mappedState.captured.displayRotation)
            assertSame(rotated, viewModel.levelUiState.value)
        }
    }

    private fun createViewModel(): PreviewOverlaysViewModel {
        every { settingsRepository.settings } returns settings
        every { mapper.map(capture(mappedState)) } returns PreviewOverlaysUiState()
        every { mapper.mapLevel(any()) } returns LevelUiState()
        val viewModel = PreviewOverlaysViewModel(
            settingsRepository = settingsRepository,
            uiStateMapper = mapper,
            levelDelegate = levelDelegate,
            focusDelegate = focusDelegate,
        )

        viewModels += viewModel
        verify { levelDelegate.bind(capture(boundStateHolder)) }
        return viewModel
    }
}
