package app.grapheneos.camera.inputfilter

import android.text.InputFilter
import android.text.Spanned

open class CustomNumFilter(
    val shouldAcceptNumber : (Float) -> Boolean,
) : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val transformedString = source.subSequence(start, end).replace(Regex("[^0-9]"), "")

        try {
            val input = (dest.subSequence(0, dstart).toString() + source + dest.subSequence(
                dend,
                dest.length
            )).toFloat()
            if (this.shouldAcceptNumber(input)) {
                return transformedString
            } else {
                return ""
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return transformedString
        }
        return transformedString
    }
}