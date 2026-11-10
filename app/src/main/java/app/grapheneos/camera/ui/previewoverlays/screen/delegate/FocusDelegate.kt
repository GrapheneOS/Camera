package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraFocusEvent
import app.grapheneos.camera.data.camera.model.CameraFocusRequest
import app.grapheneos.camera.data.camera.session.FocusControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.FocusAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPhase
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPress
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusSelection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface FocusDelegate {

    fun bind(stateHolder: StateHolder<PreviewOverlaysState>)

    fun focusAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): FocusState

    fun onAction(action: FocusAction)

    fun onScreenPaused()

    suspend fun trackFocus()
}

internal class FocusDelegateImpl @Inject constructor(
    private val focusControl: FocusControl,
    private val playCameraSound: PlayCameraSound,
) : FocusDelegate {

    private lateinit var stateHolder: StateHolder<PreviewOverlaysState>

    private val state: PreviewOverlaysState
        get() = stateHolder.state.value

    private var lastRequestId = 0L
    private var activeRequestId: Long? = null

    override fun bind(stateHolder: StateHolder<PreviewOverlaysState>) {
        this.stateHolder = stateHolder
    }

    override fun focusAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): FocusState {
        val modeChanged = previous.previewContent?.mode != current.previewContent?.mode
        return when {
            current.isPreviewInteractive() && !modeChanged -> current.focus
            else -> current.focus.hidden()
        }
    }

    override fun onAction(action: FocusAction) {
        when (action) {
            is FocusAction.FocusKeyPressed -> cancelFocus()
            is FocusAction.PreviewTapped -> onPreviewTapped()
            is FocusAction.PreviewLongPressed -> onPreviewLongPressed()
            is FocusAction.PreviewGestureStarted -> onPreviewGestureStarted()
            is FocusAction.PreviewPressed -> onPreviewPressed(
                x = action.x,
                y = action.y,
                hitRadius = action.hitRadius,
            )
        }
    }

    private fun onPreviewPressed(
        x: Float,
        y: Float,
        hitRadius: Float,
    ) {
        if (!state.isPreviewInputAvailable()) return

        val selection = state.focus.selection
        val isOnRing = selection != null && selection.visible &&
            selection.isNear(
                x = x,
                y = y,
                radius = hitRadius,
            )
        val press = when {
            isOnRing -> FocusPress.OnRing(
                x = x,
                y = y,
            )
            else -> newPoint(
                x = x,
                y = y,
            )
        }
        updateFocus { focus -> focus.copy(press = press) }
    }

    private fun onPreviewTapped() {
        when (val press = takePress()) {
            null -> Unit
            is FocusPress.OnRing -> cancelFocus()
            is FocusPress.NewPoint -> startFocus(
                press = press,
                isLocked = false,
            )
        }
    }

    private fun onPreviewLongPressed() {
        when (val press = takePress()) {
            null -> Unit
            is FocusPress.OnRing -> lockFocus(press)
            is FocusPress.NewPoint -> startFocus(
                press = press,
                isLocked = true,
            )
        }
    }

    private fun onPreviewGestureStarted() {
        updateFocus { focus -> focus.copy(press = null) }
    }

    private fun cancelFocus() {
        cancelMetering()
        updateFocus { focus -> focus.hidden() }
    }

    override fun onScreenPaused() {
        cancelMetering()
        updateFocus { FocusState() }
    }

    override suspend fun trackFocus() {
        coroutineScope {
            launch { trackHiddenSelection() }
            focusControl.events.collect { event ->
                when (event) {
                    is CameraFocusEvent.Started -> {
                        if (state.previewContent?.mode != CameraMode.VIDEO) {
                            this@coroutineScope.launch { playCameraSound(CameraSound.FOCUS_START) }
                        }
                    }

                    is CameraFocusEvent.Focused -> updateSelection(event.requestId) { selection ->
                        when (selection.phase) {
                            FocusPhase.Locked -> selection
                            else -> selection.copy(phase = FocusPhase.Focused)
                        }
                    }

                    is CameraFocusEvent.Locked -> updateSelection(event.requestId) { selection ->
                        selection.copy(phase = FocusPhase.Locked)
                    }

                    is CameraFocusEvent.Cancelled -> {
                        if (activeRequestId == event.requestId) {
                            activeRequestId = null
                        }
                        updateSelection(event.requestId) { selection ->
                            selection.copy(visible = false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun trackHiddenSelection() {
        stateHolder.state
            .map { state ->
                state.focus.selection
                    ?.takeUnless { selection -> selection.visible }
                    ?.requestId
            }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { requestId ->
                if (activeRequestId == requestId) {
                    cancelMetering()
                }
            }
    }

    private fun cancelMetering() {
        activeRequestId = null
        focusControl.cancel()
    }

    private fun takePress(): FocusPress? {
        val press = state.focus.press?.takeIf { state.isPreviewInputAvailable() }
        updateFocus { focus -> focus.copy(press = null) }
        return press
    }

    private fun lockFocus(press: FocusPress.OnRing) {
        when {
            state.focus.selection?.visible == true -> focusControl.lock()
            else -> startFocus(
                press = newPoint(
                    x = press.x,
                    y = press.y,
                ),
                isLocked = true,
            )
        }
    }

    private fun startFocus(
        press: FocusPress.NewPoint,
        isLocked: Boolean,
    ) {
        activeRequestId = press.requestId
        updateFocus {
            FocusState(
                selection = FocusSelection(
                    requestId = press.requestId,
                    x = press.x,
                    y = press.y,
                ),
            )
        }
        focusControl.focus(
            request = CameraFocusRequest(
                id = press.requestId,
                x = press.x,
                y = press.y,
                autoCancelAfter = state.settings.focusTimeoutSeconds.seconds,
                isLocked = isLocked,
            ),
        )
    }

    private fun newPoint(
        x: Float,
        y: Float,
    ): FocusPress.NewPoint {
        lastRequestId += 1
        return FocusPress.NewPoint(
            requestId = lastRequestId,
            x = x,
            y = y,
        )
    }

    private fun updateFocus(transform: (FocusState) -> FocusState) {
        stateHolder.update { state -> state.copy(focus = transform(state.focus)) }
    }

    private fun updateSelection(
        requestId: Long,
        transform: (FocusSelection) -> FocusSelection,
    ) {
        updateFocus { focus ->
            val selection = focus.selection
            when {
                selection?.requestId != requestId -> focus
                else -> focus.copy(selection = transform(selection))
            }
        }
    }
}
