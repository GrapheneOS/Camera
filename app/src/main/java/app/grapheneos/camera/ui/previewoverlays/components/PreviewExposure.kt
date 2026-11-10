package app.grapheneos.camera.ui.previewoverlays.components

import android.icu.text.NumberFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBar
import app.grapheneos.camera.ui.components.motion.POP_IN
import app.grapheneos.camera.ui.components.motion.POP_OUT
import app.grapheneos.camera.ui.components.overlayiconbutton.OverlayIconButton
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ExposureAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel

private const val EXPOSURE_FRACTION_DIGITS = 2

@Composable
internal fun PreviewExposure(
    exposure: PreviewControlUiModel.Exposure,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true,
) {
    val description = stringResource(R.string.exposure_compensation)
    val valueDescription = exposureValueDescription(exposure.value)

    AdjustmentBar(
        value = exposure.value,
        onValueChange = { value -> onAction(Action.ValueChanged(value)) },
        valueRange = exposure.range,
        steps = exposure.steps,
        majorTickInterval = exposure.majorTickInterval,
        startIcon = Icons.Outlined.BrightnessLow,
        endIcon = Icons.Outlined.BrightnessHigh,
        showContainer = showContainer,
        onPressChanged = { pressed -> onAction(Action.PressChanged(isPressed = pressed)) },
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = valueDescription
        },
    )
}

@Composable
private fun exposureValueDescription(value: Float): String {
    val locale = LocalLocale.current.platformLocale
    val format = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = EXPOSURE_FRACTION_DIGITS
        }
    }

    return stringResource(
        R.string.exposure_value,
        format.format(value.toDouble()),
    )
}

@Composable
internal fun PreviewExposureReset(
    exposure: PreviewControlUiModel.Exposure,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.exposure_reset)

    AnimatedVisibility(
        visible = exposure.isResetVisible,
        modifier = modifier,
        enter = POP_IN,
        exit = POP_OUT,
    ) {
        OverlayIconButton(
            onClick = { onAction(Action.ResetClicked) },
            icon = Icons.Outlined.RestartAlt,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
