package app.grapheneos.camera.ui.components.adjustmentbar

import android.icu.text.NumberFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButton
import app.grapheneos.camera.ui.components.valuechip.ValueChip
import app.grapheneos.camera.ui.core.CameraPreviewControl
import app.grapheneos.camera.ui.core.CameraPreviewSample
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_HIGH_ICON
import app.grapheneos.camera.ui.core.PREVIEW_BRIGHTNESS_LOW_ICON
import app.grapheneos.camera.ui.core.PREVIEW_COOL_ICON
import app.grapheneos.camera.ui.core.PREVIEW_COOL_TINT
import app.grapheneos.camera.ui.core.PREVIEW_RESTART_ICON
import app.grapheneos.camera.ui.core.PREVIEW_THERMOMETER_ADD_ICON
import app.grapheneos.camera.ui.core.PREVIEW_THERMOMETER_MINUS_ICON
import app.grapheneos.camera.ui.core.PREVIEW_WARM_ICON
import app.grapheneos.camera.ui.core.PREVIEW_WARM_TINT
import kotlin.math.roundToLong

private const val AUTO_TEMPERATURE = 5_000f

private const val POP_SCALE = 0.8f

private val RESET_BUTTON_SIZE = 48.dp
private val RESET_BUTTON_GAP = 8.dp
private val CHIP_SLOT_HEIGHT = 32.dp
private val POP_IN = fadeIn() + scaleIn(initialScale = POP_SCALE)
private val POP_OUT = fadeOut() + scaleOut(targetScale = POP_SCALE)

private val EXPOSURE_RANGE = -12f..12f
private const val EXPOSURE_STEPS = 23
private val TEMPERATURE_RANGE = 2_000f..8_000f
private const val TEMPERATURE_STEPS = 59

private enum class SampleAdjustment(
    val label: String,
    val startIcon: ImageVector,
    val endIcon: ImageVector,
) {
    Brightness(
        label = "Brightness",
        startIcon = PREVIEW_BRIGHTNESS_LOW_ICON,
        endIcon = PREVIEW_BRIGHTNESS_HIGH_ICON,
    ),
    WhiteBalance(
        label = "White balance",
        startIcon = PREVIEW_THERMOMETER_MINUS_ICON,
        endIcon = PREVIEW_THERMOMETER_ADD_ICON,
    ),
}

@Preview(heightDp = 720)
@Composable
private fun AdjustmentBarSamplePreview() {
    AdjustmentBarSample()
}

@Composable
private fun AdjustmentBarSample() {
    val state = remember { AdjustmentBarSampleState() }
    val valueLabel = rememberValueLabel(state = state)

    CameraPreviewSample(
        status = "${state.adjustment.label}: $valueLabel",
        controls = {
            CameraPreviewControl(
                text = state.adjustment.label,
                onClick = state::nextAdjustment,
            )
        },
    ) {
        SampleControls(
            state = state,
            valueLabel = valueLabel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SampleControls(
    state: AdjustmentBarSampleState,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        SampleValueChip(
            isVisible = state.adjustment == SampleAdjustment.WhiteBalance,
            text = valueLabel,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            SampleAdjustmentBar(
                state = state,
                valueLabel = valueLabel,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = RESET_BUTTON_SIZE + RESET_BUTTON_GAP * 2),
            )
            SampleResetButton(
                isVisible = state.isAdjusted,
                onClick = state::reset,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = RESET_BUTTON_GAP),
            )
        }
    }
}

/** The slot keeps its height while the chip is hidden, so the bar below it never moves. */
@Composable
private fun SampleValueChip(
    isVisible: Boolean,
    text: String,
) {
    Box(
        modifier = Modifier.heightIn(min = CHIP_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = POP_IN,
            exit = POP_OUT,
        ) {
            ValueChip(text = text)
        }
    }
}

@Composable
private fun SampleResetButton(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = POP_IN,
        exit = POP_OUT,
    ) {
        OverlayIconButton(
            onClick = onClick,
            icon = PREVIEW_RESTART_ICON,
            modifier = Modifier.semantics { contentDescription = "Reset" },
        )
    }
}

@Composable
private fun SampleAdjustmentBar(
    state: AdjustmentBarSampleState,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    val adjustment = state.adjustment
    val colors = when (adjustment) {
        SampleAdjustment.Brightness -> AdjustmentBarColors.fromTheme()
        SampleAdjustment.WhiteBalance -> AdjustmentBarColors.fromTheme().copy(
            startTint = PREVIEW_COOL_TINT,
            endTint = PREVIEW_WARM_TINT,
            startIconColor = PREVIEW_COOL_ICON,
            endIconColor = PREVIEW_WARM_ICON,
        )
    }

    key(adjustment) {
        AdjustmentBar(
            value = state.value,
            onValueChange = state::change,
            valueRange = state.valueRange,
            steps = state.steps,
            modifier = modifier.semantics {
                contentDescription = adjustment.label
                stateDescription = valueLabel
            },
            startIcon = adjustment.startIcon,
            endIcon = adjustment.endIcon,
            colors = colors,
        )
    }
}

@Composable
private fun rememberValueLabel(state: AdjustmentBarSampleState): String {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val temperature = state.temperature

    return when {
        state.adjustment == SampleAdjustment.Brightness -> {
            numberFormat.format(state.exposure.roundToLong())
        }

        temperature == null -> "Auto"

        else -> "${numberFormat.format(temperature.roundToLong())}K"
    }
}

@Stable
private class AdjustmentBarSampleState {

    var adjustment by mutableStateOf(SampleAdjustment.Brightness)
        private set
    var exposure by mutableFloatStateOf(0f)
        private set
    var isExposureAdjusted by mutableStateOf(false)
        private set
    var temperature by mutableStateOf<Float?>(null)
        private set

    val value: Float
        get() {
            return when (adjustment) {
                SampleAdjustment.Brightness -> exposure
                SampleAdjustment.WhiteBalance -> temperature ?: AUTO_TEMPERATURE
            }
        }

    val valueRange: ClosedFloatingPointRange<Float>
        get() {
            return when (adjustment) {
                SampleAdjustment.Brightness -> EXPOSURE_RANGE
                SampleAdjustment.WhiteBalance -> TEMPERATURE_RANGE
            }
        }

    val steps: Int
        get() {
            return when (adjustment) {
                SampleAdjustment.Brightness -> EXPOSURE_STEPS
                SampleAdjustment.WhiteBalance -> TEMPERATURE_STEPS
            }
        }

    val isAdjusted: Boolean
        get() {
            return when (adjustment) {
                SampleAdjustment.Brightness -> isExposureAdjusted
                SampleAdjustment.WhiteBalance -> temperature != null
            }
        }

    fun nextAdjustment() {
        val adjustments = SampleAdjustment.entries

        adjustment = adjustments[(adjustment.ordinal + 1) % adjustments.size]
    }

    fun change(value: Float) {
        when (adjustment) {
            SampleAdjustment.Brightness -> {
                exposure = value
                isExposureAdjusted = true
            }

            SampleAdjustment.WhiteBalance -> temperature = value
        }
    }

    fun reset() {
        when (adjustment) {
            SampleAdjustment.Brightness -> {
                exposure = 0f
                isExposureAdjusted = false
            }

            SampleAdjustment.WhiteBalance -> temperature = null
        }
    }
}
