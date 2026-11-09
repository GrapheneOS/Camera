package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.FocusDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.LifecycleAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.PreviewAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal interface PreviewOverlaysScreenModel {

    val uiState: StateFlow<PreviewOverlaysUiState>

    val levelUiState: StateFlow<LevelUiState>

    fun onAction(action: Action)
}

@HiltViewModel
internal class PreviewOverlaysViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    uiStateMapper: PreviewOverlaysUiStateMapper,
    private val focusDelegate: FocusDelegate,
    private val levelDelegate: LevelDelegate,
) : ViewModel(),
    PreviewOverlaysScreenModel {

    private val stateHolder = StateHolder(
        initial = PreviewOverlaysState(
            settings = settingsRepository.settings.value,
        ),
    )

    override val uiState: StateFlow<PreviewOverlaysUiState> = stateHolder.derive { state ->
        uiStateMapper.map(state)
    }

    override val levelUiState: StateFlow<LevelUiState> = stateHolder.derive { state ->
        uiStateMapper.mapLevel(state)
    }

    private var resumedWork: Job? = null

    init {
        levelDelegate.bind(stateHolder)
        focusDelegate.bind(stateHolder)

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                updatePreview { state -> state.copy(settings = settings) }
            }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is LifecycleAction -> onLifecycleAction(action)
            is PreviewAction -> onPreviewAction(action)
        }
    }

    private fun onLifecycleAction(action: LifecycleAction) {
        when (action) {
            is LifecycleAction.ScreenResumed -> onScreenResumed()
            is LifecycleAction.ScreenPaused -> onScreenPaused()
        }
    }

    private fun onPreviewAction(action: PreviewAction) {
        when (action) {
            is PreviewAction.FocusKeyPressed -> onFocusKeyPressed()
            is PreviewAction.PreviewTapped -> onTapped()
            is PreviewAction.PreviewPressed -> onPressed(action)
            is PreviewAction.PreviewLongPressed -> onLongPressed()
            is PreviewAction.PreviewGestureStarted -> onGestureStarted()
            is PreviewAction.DisplayRotationChanged -> onRotationChanged(action)
            is PreviewAction.PreviewContentChanged -> onContentChanged(action)
            is PreviewAction.PreviewFlashVisibilityChanged -> onFlashChanged(action)
        }
    }

    private fun onFocusKeyPressed() {
        focusDelegate.cancelFocus()
    }

    private fun onTapped() {
        focusDelegate.onPreviewTapped()
    }

    private fun onPressed(action: PreviewAction.PreviewPressed) {
        focusDelegate.onPreviewPressed(
            x = action.x,
            y = action.y,
            hitRadius = action.hitRadius,
        )
    }

    private fun onLongPressed() {
        focusDelegate.onPreviewLongPressed()
    }

    private fun onGestureStarted() {
        focusDelegate.onPreviewGestureStarted()
    }

    private fun onRotationChanged(action: PreviewAction.DisplayRotationChanged) {
        stateHolder.update { state -> state.copy(displayRotation = action.rotation) }
    }

    private fun onContentChanged(action: PreviewAction.PreviewContentChanged) {
        updatePreview { state -> state.copy(previewContent = action.content) }
    }

    private fun onFlashChanged(action: PreviewAction.PreviewFlashVisibilityChanged) {
        updatePreview { state -> state.copy(isPreviewFlashVisible = action.visible) }
    }

    private fun updatePreview(transform: (PreviewOverlaysState) -> PreviewOverlaysState) {
        stateHolder.update { previous ->
            val current = transform(previous)
            current.copy(
                level = levelDelegate.levelAfterChange(previous = previous, current = current),
                focus = focusDelegate.focusAfterChange(previous = previous, current = current),
            )
        }
    }

    private fun onScreenResumed() {
        if (resumedWork?.isActive == true) return

        resumedWork = viewModelScope.launch {
            launch { levelDelegate.trackLevel() }
            launch { focusDelegate.trackFocus() }
        }
    }

    private fun onScreenPaused() {
        resumedWork?.cancel()
        resumedWork = null
        levelDelegate.onScreenPaused()
        focusDelegate.onScreenPaused()
        stateHolder.update { state -> state.copy(isPreviewFlashVisible = false) }
    }
}
