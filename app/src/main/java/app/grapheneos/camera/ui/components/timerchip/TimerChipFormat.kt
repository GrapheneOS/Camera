package app.grapheneos.camera.ui.components.timerchip

import android.icu.text.NumberFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import kotlin.time.Duration

@Immutable
internal class TimerChipFormat(
    locale: Locale,
) {

    private val twoDigits = NumberFormat.getIntegerInstance(locale).apply {
        minimumIntegerDigits = 2
        isGroupingUsed = false
    }

    fun format(elapsed: Duration): String {
        return elapsed.coerceAtLeast(Duration.ZERO).toComponents { hours, minutes, seconds, _ ->
            val clock = "${twoDigits.format(minutes)}:${twoDigits.format(seconds)}"

            when {
                hours > 0 -> "${twoDigits.format(hours)}:$clock"
                else -> clock
            }
        }
    }
}

@Composable
internal fun rememberTimerChipFormat(): TimerChipFormat {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale) { TimerChipFormat(locale = locale) }
}
