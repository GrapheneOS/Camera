package app.grapheneos.camera.ui.components.countdowntimer

import android.icu.text.NumberFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

@Composable
internal fun rememberCountDownTimerText(value: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    return remember(numberFormat, value) { numberFormat.format(value.toLong()) }
}

@Composable
internal fun rememberCountDownTimerLayout(
    text: String,
    fontSize: TextUnit,
): TextLayoutResult {
    val textMeasurer = rememberTextMeasurer()
    val layoutDirection = LocalLayoutDirection.current

    return remember(
        textMeasurer,
        text,
        fontSize,
        layoutDirection,
    ) {
        textMeasurer.measure(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Thin,
                fontSize = fontSize,
                fontFeatureSettings = "tnum",
                lineHeight = fontSize,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1,
            layoutDirection = layoutDirection,
        )
    }
}

/**
 * The digits are a thin glyph outlined with a round stroke, which rounds their corners and ends
 * and gives them their weight. Strokes are kept per step so that an animation does not allocate.
 */
internal class CountDownTimerStrokes(
    private val emSize: Float,
) {

    private val strokes = arrayOfNulls<Stroke>(size = MAX_STEP + 1)

    fun stroke(boldness: Float): Stroke {
        val step = step(boldness = boldness)

        return strokes[step] ?: createStroke(step = step).also { strokes[step] = it }
    }

    private fun createStroke(step: Int): Stroke {
        return Stroke(
            width = step * emSize / STEPS_PER_EM,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    }

    companion object {
        private const val REST_WIDTH = 0.05f
        private const val PEAK_WIDTH = 0.15f
        private const val STEPS_PER_EM = 1000

        private val MAX_STEP = (width(boldness = MAX_BOLDNESS) * STEPS_PER_EM).roundToInt()

        internal fun width(boldness: Float): Float {
            return REST_WIDTH + (PEAK_WIDTH - REST_WIDTH) * boldness
        }

        internal fun step(boldness: Float): Int {
            return (width(boldness = boldness) * STEPS_PER_EM)
                .roundToInt()
                .coerceIn(1, MAX_STEP)
        }
    }
}
