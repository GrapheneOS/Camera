package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.ui.activities.MainActivity

class IsoBar : AppCompatSeekBar {


    private val isoValues = arrayOf(50, 100, 200, 400, 800, 1600)
    private lateinit var mainActivity: MainActivity

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)


    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity
        this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val selectedIso = isoValues[progress]

                // Future implementation of
                // camConfig.setISO(selectedIso)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}