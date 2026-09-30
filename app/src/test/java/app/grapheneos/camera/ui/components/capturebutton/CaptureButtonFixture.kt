package app.grapheneos.camera.ui.components.capturebutton

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
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonCore
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonProgress
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTone
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger
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

    var isShown by mutableStateOf(true)
    var enabled by mutableStateOf(true)
    var tone by mutableStateOf(CaptureButtonTone.Neutral)
    var progress by mutableStateOf<CaptureButtonProgress>(CaptureButtonProgress.None)
    var trigger by mutableStateOf(CaptureButtonTrigger.Release)
    var icon by mutableStateOf<ImageVector?>(null)
    var isHoldEnabled by mutableStateOf(false)
    var holdTargets by mutableStateOf<List<CaptureButtonTarget>>(emptyList())

    fun setContent() {
        composeRule.setContent {
            CameraTheme {
                if (isShown) {
                    CaptureButton(
                        onClick = { clicks += 1 },
                        core = CaptureButtonCore.Disc,
                        modifier = Modifier.semantics { contentDescription = DESCRIPTION },
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
