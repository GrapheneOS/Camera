package app.grapheneos.camera.ui.components.zoom

import android.icu.text.DecimalFormat
import android.icu.text.DecimalFormatSymbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.math.RoundingMode
import java.util.Locale

/** Tenths at most, cut rather than rounded, so a value never shows the stop it has not reached. */
@Immutable
internal class ZoomFormat(
    locale: Locale,
) {

    private val decimalFormat = DecimalFormat(PATTERN, DecimalFormatSymbols(locale)).apply {
        minimumIntegerDigits = 0
    }

    fun format(value: Float): String {
        return decimalFormat.format(value.toBigDecimal().setScale(1, RoundingMode.DOWN))
    }

    private companion object {
        private const val PATTERN = "#.#"
    }
}

@Composable
internal fun rememberZoomFormat(): ZoomFormat {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale) { ZoomFormat(locale = locale) }
}
