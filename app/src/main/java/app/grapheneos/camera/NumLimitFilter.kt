package app.grapheneos.camera

import android.text.InputFilter
import android.text.Spanned

// Ensures that the field is within the min/max limits for a number type input field
class NumLimitFilter(
    val min: Float,
    val max: Float,
    val onOutOfRange: () -> Unit,
) : InputFilter {

    constructor(min: Int, max: Int, onOutOfRange: () -> Unit) : this(min.toFloat(), max.toFloat(), onOutOfRange)

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        try {
            val input = (dest.subSequence(0, dstart).toString() + source + dest.subSequence(
                dend,
                dest.length
            )).toFloat()
            if (isInRange(input)) {
                return null
            } else {
                this.onOutOfRange()
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return null
        }
        return ""
    }

    private fun isInRange(value: Float): Boolean {
        return value in this.min..this.max
    }
}
