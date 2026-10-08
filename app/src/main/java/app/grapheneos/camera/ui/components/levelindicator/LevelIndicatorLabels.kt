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
    private val shadow: Shadow,
) {

    private val labels = HashMap<Int, CenteredText>()

    fun shadow(alpha: Float): Shadow {
        return when {
            alpha >= 1f -> shadow
            else -> shadow.copy(color = shadow.color.copy(alpha = shadow.color.alpha * alpha))
        }
    }

    fun label(degrees: Int): CenteredText {
        return labels.getOrPut(degrees) {
            val number = numberFormat.format(degrees.toLong())

            CenteredText(
                layout = textMeasurer.measure(
                    text = number + DEGREE_SIGN,
                    style = style,
                ),
                centeredLength = number.length,
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
    )

    return remember(textMeasurer, locale, style, shadowColor, shadowRadiusPx) {
        LevelIndicatorLabels(
            textMeasurer = textMeasurer,
            style = style,
            numberFormat = NumberFormat.getIntegerInstance(locale),
            shadow = Shadow(
                color = shadowColor,
                blurRadius = shadowRadiusPx,
            ),
        )
    }
}
