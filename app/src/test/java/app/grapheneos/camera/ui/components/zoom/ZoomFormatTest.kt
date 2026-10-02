package app.grapheneos.camera.ui.components.zoom

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomFormatTest {

    private val format = ZoomFormat(locale = Locale.US)

    @Test
    fun format_dropsTheLeadingZero() {
        assertEquals(".6", format.format(value = 0.6f))
    }

    @Test
    fun format_dropsAWholeNumbersFraction() {
        assertEquals("1", format.format(value = 1f))
        assertEquals("2", format.format(value = 2f))
    }

    @Test
    fun format_keepsTenthsOnly() {
        assertEquals("4.5", format.format(value = 4.56f))
    }

    @Test
    fun format_cutsInsteadOfRounding() {
        assertEquals(".9", format.format(value = 0.99f))
    }

    @Test
    fun format_isNotFooledByFloatError() {
        assertEquals("1.9", format.format(value = 1.9f))
    }

    @Test
    fun format_followsTheLocale() {
        assertEquals("4,5", ZoomFormat(locale = Locale.GERMANY).format(value = 4.5f))
    }
}
