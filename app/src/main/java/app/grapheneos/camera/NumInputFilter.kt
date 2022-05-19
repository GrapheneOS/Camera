package app.grapheneos.camera

import android.text.InputFilter
import android.text.Spanned
import app.grapheneos.camera.ui.activities.MoreSettings

class NumInputFilter(private val settings: MoreSettings) : InputFilter {

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
            )).toInt()
            if (isInRange(input)) {
                return null
            } else {
                settings.showMessage(settings.getString(
                    R.string.photo_quality_number_limit, min, max))
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return ""
    }

    private fun isInRange(value: Int): Boolean {
        return value in min..max
    }

    companion object {
        const val min = 1
        const val max = 100
    }
}
