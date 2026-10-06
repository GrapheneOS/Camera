package app.grapheneos.camera.ui.components.capturebutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import app.grapheneos.camera.ui.components.capturebutton.gesture.CaptureButtonHoldState
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonSize
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
import app.grapheneos.camera.ui.components.progress.model.RingProgress
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone
import app.grapheneos.camera.ui.core.CameraTheme

internal class CaptureButtonFixture(
    private val composeRule: ComposeContentTestRule,
) {

    var clicks = 0
        private set
    var holdStarts = 0
        private set
    var drag = Offset.Zero
        private set
    val holdEnds = mutableListOf<CaptureButtonHoldEnd>()
    val holdState = CaptureButtonHoldState()
    var isPressed = false
        private set

    var isShown by mutableStateOf(true)
    var size by mutableStateOf(CaptureButtonSize.Regular)
    var enabled by mutableStateOf(true)
    var tone by mutableStateOf(ShutterTone.Neutral)
    var progress by mutableStateOf<RingProgress>(RingProgress.None)
    var trigger by mutableStateOf(CaptureButtonTrigger.Release)
    var icon by mutableStateOf<ImageVector?>(null)
    var isHoldEnabled by mutableStateOf(false)
    var holdTargets by mutableStateOf<List<CaptureButtonTarget>>(emptyList())

    private val interactionSource = MutableInteractionSource()

    fun setContent() {
        composeRule.setContent {
            isPressed = interactionSource.collectIsPressedAsState().value

            CameraTheme {
                if (isShown) {
                    CaptureButton(
                        onClick = { clicks += 1 },
                        core = ShutterCore.Disc,
                        modifier = Modifier.semantics { contentDescription = DESCRIPTION },
                        size = size,
                        tone = tone,
                        enabled = enabled,
                        progress = progress,
                        trigger = trigger,
                        icon = icon,
                        onHoldStart = when {
                            isHoldEnabled -> ::countHoldStart
                            else -> null
                        },
                        onHoldDrag = { delta -> drag += delta },
                        onHoldEnd = { end -> holdEnds += end },
                        holdTargets = holdTargets,
                        holdState = holdState,
                        interactionSource = interactionSource,
                    )
                }
            }
        }
    }

    fun button(): SemanticsNodeInteraction {
        return composeRule.onNodeWithContentDescription(DESCRIPTION)
    }

    fun hold() {
        button().performTouchInput { down(center) }
        waitForHold()
    }

    fun waitForHold() {
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
    }

    private fun countHoldStart() {
        holdStarts += 1
    }

    companion object {
        const val DESCRIPTION = "Capture"

        private const val HOLD_MILLIS = 2_000L
    }
}
