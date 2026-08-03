package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.ui.activities.MainActivity

class IsoBar : AppCompatSeekBar {


    private val isoValues = arrayOf(
        50, 100, 125, 150, 175,
        200, 225, 250, 275,
        300, 325, 350, 375,
        400, 425, 450, 475,
        500, 600, 700, 800
    )
    private lateinit var mainActivity: MainActivity

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)


    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity
        this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(progress < isoValues.size){
                    val selectedIso = isoValues[progress]

                    mainActivity.isoValueText.text = selectedIso.toString();

                    // Future implementation of
                    // camConfig.setISO(selectedIso)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}