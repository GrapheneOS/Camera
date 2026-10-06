package app.grapheneos.camera.ui.components.timerchip

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimerChipFormatTest {

    private val format = TimerChipFormat(locale = Locale.US)

    @Test
    fun format_underAnHour_showsMinutesAndSeconds() {
        assertEquals("00:00", format.format(elapsed = Duration.ZERO))
        assertEquals("01:05", format.format(elapsed = 1.minutes + 5.seconds))
    }

    @Test
    fun format_fromAnHour_addsTwoDigitHours() {
        assertEquals("01:02:03", format.format(elapsed = 1.hours + 2.minutes + 3.seconds))
        assertEquals("10:00:00", format.format(elapsed = 10.hours))
    }

    @Test
    fun format_dropsTheFractionOfASecond() {
        assertEquals("00:01", format.format(elapsed = 1999.milliseconds))
    }

    @Test
    fun format_followsTheLocale() {
        val persian = TimerChipFormat(locale = Locale.forLanguageTag("fa"))

        assertEquals("۰۱:۰۵", persian.format(elapsed = 1.minutes + 5.seconds))
    }
}
