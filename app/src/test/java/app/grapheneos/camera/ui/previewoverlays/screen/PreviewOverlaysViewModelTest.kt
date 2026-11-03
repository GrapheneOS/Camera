package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewContentState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewOverlaysViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = MutableStateFlow(CameraSettings(gridType = GridType.FOUR_BY_FOUR))
    private val settingsRepository = mockk<SettingsRepository>()
    private val mapper = mockk<PreviewOverlaysUiStateMapper>()
    private val mappedState = slot<PreviewOverlaysState>()
    private val viewModels = mutableListOf<PreviewOverlaysViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { viewModel -> viewModel.viewModelScope.cancel() }
    }

    @Test
    fun initialState_readsSessionSettingsAndWaitsForPreviewContent() {
        runTest {
            createViewModel()

            assertEquals(GridType.FOUR_BY_FOUR, mappedState.captured.gridType)
            assertNull(mappedState.captured.previewContent)
        }
    }

    @Test
    fun previewContentChanged_updatesUiStateBeforeReturning() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(isTransitionFrameVisible = true)
            val mapped = PreviewOverlaysUiState(gridPattern = CompositionGridPattern.Quarters)
            every { mapper.map(capture(mappedState)) } returns mapped

            viewModel.onAction(Action.PreviewContentChanged(content = content))

            assertEquals(content, mappedState.captured.previewContent)
            assertSame(mapped, viewModel.uiState.value)
        }
    }

    @Test
    fun settingsChanges_preservePreviewContentAndFlash() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(mode = CameraMode.QR_SCAN)
            viewModel.onAction(Action.PreviewContentChanged(content = content))
            viewModel.onAction(Action.PreviewFlashVisibilityChanged(visible = true))

            settings.value = settings.value.copy(gridType = GridType.GOLDEN_RATIO)

            assertEquals(
                PreviewOverlaysState(
                    gridType = GridType.GOLDEN_RATIO,
                    previewContent = content,
                    isPreviewFlashVisible = true,
                ),
                mappedState.captured,
            )
        }
    }

    @Test
    fun endingAFlash_preservesPreviewContentAndGrid() {
        runTest {
            val viewModel = createViewModel()
            val content = PreviewContentState(isCaptureResultVisible = true)
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

    private fun createViewModel(): PreviewOverlaysViewModel {
        every { settingsRepository.settings } returns settings
        every { mapper.map(capture(mappedState)) } returns PreviewOverlaysUiState()
        val viewModel = PreviewOverlaysViewModel(
            settingsRepository = settingsRepository,
            uiStateMapper = mapper,
        )

        viewModels += viewModel
        return viewModel
    }
}
