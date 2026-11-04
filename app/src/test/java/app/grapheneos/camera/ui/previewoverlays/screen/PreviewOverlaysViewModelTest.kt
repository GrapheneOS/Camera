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
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val levelDelegate = mockk<LevelDelegate>(relaxed = true)
    private val viewModels = mutableListOf<PreviewOverlaysViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { viewModel -> viewModel.viewModelScope.cancel() }
    }

    @Test
    fun initialState_readsSessionSettingsAndWaitsForPreviewContent() {
        runTest {
            settings.value = settings.value.copy(gyroscopeSuggestions = true)
            createViewModel()

            assertEquals(GridType.FOUR_BY_FOUR, mappedState.captured.gridType)
            assertTrue(mappedState.captured.level.isEnabled)
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

            viewModel.onAction(Action.PreviewContentChanged(content = content))

            assertEquals(content, mappedState.captured.previewContent)
            assertSame(mapped, viewModel.uiState.value)
        }
    }

    @Test
    fun settingsChanges_preservePreviewContentAndFlashButClearAnUnavailableLevel() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(
                mode = CameraMode.QR_SCAN,
                selfTimerRemaining = 3.seconds,
            )
            viewModel.onAction(Action.PreviewContentChanged(content = content))
            viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = true))
            val level = LevelState(
                pose = LevelPose.Horizon,
                tiltDegrees = 3f,
                horizonDegrees = 12f,
                orientation = DeviceOrientation.DEGREES_90,
            )
            boundStateHolder.captured.update { it.copy(level = level) }

            settings.value = settings.value.copy(
                gridType = GridType.GOLDEN_RATIO,
                gyroscopeSuggestions = true,
            )

            assertEquals(
                PreviewOverlaysState(
                    gridType = GridType.GOLDEN_RATIO,
                    previewContent = content,
                    isPreviewFlashVisible = true,
                    level = LevelState(isEnabled = true),
                ),
                mappedState.captured,
            )

            settings.value = settings.value.copy(gyroscopeSuggestions = false)
            assertEquals(LevelState(isEnabled = false), mappedState.captured.level)
        }
    }

    @Test
    fun endingAFlash_preservesPreviewContentAndGrid() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(
                isCaptureResultVisible = true,
                selfTimerRemaining = 3.seconds,
            )
            viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = true))
            viewModel.onAction(Action.PreviewContentChanged(content = content))
            viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = false))

            assertEquals(
                PreviewOverlaysState(
                    gridType = GridType.FOUR_BY_FOUR,
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
    fun screenPaused_stopsLevelTrackingAndClearsThePreviewFlash() {
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

            viewModel.onAction(Action.ScreenResumed)
            viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = true))
            boundStateHolder.captured.update { state ->
                state.copy(level = state.level.copy(visible = true))
            }
            assertTrue(started)

            viewModel.onAction(Action.ScreenPaused)

            assertTrue(stopped)
            assertFalse(mappedState.captured.isPreviewFlashVisible)
            assertFalse(mappedState.captured.level.visible)
        }
    }

    @Test
    fun previewContextChanges_clearTrackingImmediatelyAndDoNotRestoreItOnReturn() {
        runTest {
            settings.value = settings.value.copy(gyroscopeSuggestions = true)
            val viewModel = createViewModel()
            val content = PreviewContentState(isPreviewStreaming = true)
            val changes = listOf<Action>(
                Action.PreviewContentChanged(content.copy(selfTimerRemaining = 3.seconds)),
                Action.PreviewContentChanged(content.copy(isTakingPicture = true)),
                Action.PreviewContentChanged(content.copy(isPreviewStreaming = false)),
                Action.PreviewContentChanged(content.copy(isTransitionFrameVisible = true)),
                Action.PreviewContentChanged(content.copy(isCaptureResultVisible = true)),
                Action.PreviewContentChanged(content.copy(mode = CameraMode.VIDEO)),
                Action.PreviewContentChanged(content.copy(mode = CameraMode.QR_SCAN)),
                Action.PreviewContentChanged(content.copy(mode = CameraMode.NIGHT)),
                Action.PreviewContentChanged(content.copy(lensFacing = LensFacing.FRONT)),
                Action.PreviewFlashVisibilityChanged(visible = true),
            )

            changes.forEach { action ->
                viewModel.onAction(Action.PreviewContentChanged(content = content))
                boundStateHolder.captured.update { state ->
                    state.copy(
                        level = LevelState(
                            isEnabled = true,
                            visible = true,
                            pose = LevelPose.Horizon,
                            tiltDegrees = 3f,
                        ),
                    )
                }

                viewModel.onAction(action)

                assertEquals(
                    action.toString(),
                    LevelState(isEnabled = true),
                    mappedState.captured.level,
                )

                viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = false))
                viewModel.onAction(Action.PreviewContentChanged(content = content))

                assertEquals(LevelState(isEnabled = true), mappedState.captured.level)
            }
        }
    }

    @Test
    fun uninterruptedFraming_preservesTrackingAcrossUiUpdates() {
        runTest {
            settings.value = settings.value.copy(gyroscopeSuggestions = true)
            val viewModel = createViewModel()
            val content = PreviewContentState(isPreviewStreaming = true)
            val level = LevelState(
                isEnabled = true,
                visible = true,
                pose = LevelPose.Horizon,
                tiltDegrees = 3f,
            )
            viewModel.onAction(Action.PreviewContentChanged(content = content))
            boundStateHolder.captured.update { it.copy(level = level) }

            val actions = listOf(
                Action.PreviewContentChanged(content = content),
                Action.DisplayRotationChanged(rotation = DeviceOrientation.DEGREES_90),
                Action.PreviewFlashVisibilityChanged(visible = false),
            )
            actions.forEach { action ->
                viewModel.onAction(action)

                assertEquals(action.toString(), level, mappedState.captured.level)
            }

            settings.value = settings.value.copy(gridType = GridType.GOLDEN_RATIO)

            assertEquals(level, mappedState.captured.level)
        }
    }

    @Test
    fun repeatedResume_keepsOneSubscriptionAndPauseAllowsRestarting() {
        runTest {
            var trackingStarts = 0
            coEvery { levelDelegate.trackLevel() } coAnswers {
                trackingStarts += 1
                awaitCancellation()
            }
            val viewModel = createViewModel()

            viewModel.onAction(Action.ScreenResumed)
            viewModel.onAction(Action.ScreenResumed)

            assertEquals(1, trackingStarts)

            viewModel.onAction(Action.ScreenPaused)
            viewModel.onAction(Action.ScreenResumed)

            assertEquals(2, trackingStarts)
        }
    }

    @Test
    fun displayRotation_isPublishedBeforeAnotherSensorReading() {
        runTest {
            val viewModel = createViewModel()
            val rotated = LevelUiState(rotationDegrees = 180f)
            every { mapper.mapLevel(any()) } returns rotated

            viewModel.onAction(
                Action.DisplayRotationChanged(rotation = DeviceOrientation.DEGREES_270),
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
        )

        viewModels += viewModel
        verify { levelDelegate.bind(capture(boundStateHolder)) }
        return viewModel
    }
}
