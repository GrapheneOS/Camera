package app.grapheneos.camera.ui

import android.content.Context
import android.util.AttributeSet
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.imageview.ShapeableImageView

class QRToggle @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ShapeableImageView(context, attrs) {

    lateinit var mActivity: MainActivity
    lateinit var key: String

    init {
        setOnClickListener {
            isSelected = !isSelected
        }

        refreshToggleUI()
    }

    private fun refreshToggleUI() {
        alpha = if (isSelected) {
            selectedAlpha
        } else {
            deselectedAlpha
        }
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        val camConfig = mActivity.camConfig

        if (!selected && camConfig.allowedFormats.size == 1) {
            mActivity.showMessage(mActivity.getString(
                R.string.couldnt_exclude_qr_format, key
            ))
            isSelected = true
        } else {
            camConfig.setQRScanningFor(key, selected)
        }

        refreshToggleUI()
    }

    companion object {
        private const val selectedAlpha = 1f
        private const val deselectedAlpha = 0.3f
    }
}
