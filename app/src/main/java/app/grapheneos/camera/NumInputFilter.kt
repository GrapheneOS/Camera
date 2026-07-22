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
        } catch (_: NumberFormatException) {
            // Nothing numeric to range-check: either the field is being emptied (deleting the
            // last digit is a legitimate edit that produces "") or the edit is not a number at
            // all. Both end up rejected below, which for a deletion is a no-op because the
            // replacement text is already empty. MoreSettings.dumpData() restores the stored
            // value if the field is left empty.
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
