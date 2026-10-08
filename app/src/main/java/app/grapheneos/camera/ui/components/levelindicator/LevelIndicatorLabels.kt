package app.grapheneos.camera.ui.components.levelindicator

import android.icu.text.NumberFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.components.text.CenteredText
import app.grapheneos.camera.ui.core.TABULAR_FIGURES

private val LABEL_SIZE = 12.dp

@Stable
internal class LevelIndicatorLabels(
    private val textMeasurer: TextMeasurer,
    private val style: TextStyle,
    private val numberFormat: NumberFormat,
) {

    private val labels = HashMap<Int, CenteredText>()

    fun label(degrees: Int): CenteredText {
        return labels.getOrPut(degrees) {
            CenteredText(
                layout = textMeasurer.measure(
                    text = numberFormat.format(degrees.toLong()) + DEGREE_SIGN,
                    style = style,
                ),
            )
        }
    }

    private companion object {
        private const val DEGREE_SIGN = "°"
    }
}

@Composable
internal fun rememberLevelIndicatorLabels(
    shadowColor: Color,
    shadowRadius: Dp,
): LevelIndicatorLabels {
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val textMeasurer = rememberTextMeasurer()
    val shadowRadiusPx = with(density) { shadowRadius.toPx() }
    val style = MaterialTheme.typography.labelMedium.copy(
        fontSize = with(density) { LABEL_SIZE.toSp() },
        fontFeatureSettings = TABULAR_FIGURES,
        shadow = Shadow(
            color = shadowColor,
            blurRadius = shadowRadiusPx,
        ),
    )

    return remember(textMeasurer, locale, style) {
        LevelIndicatorLabels(
            textMeasurer = textMeasurer,
            style = style,
            numberFormat = NumberFormat.getIntegerInstance(locale),
        )
    }
}
