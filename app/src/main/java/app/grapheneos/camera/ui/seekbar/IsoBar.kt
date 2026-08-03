package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.ui.activities.MainActivity

class IsoBar : AppCompatSeekBar {


    private var isoValues: List<Int> = listOf(50, 100, 200, 400, 800, 1600)
    private lateinit var mainActivity: MainActivity

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)


    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity

        val range = mainActivity.camConfig.getIsoRange()

        if (range != null) {
            val minIso = range.lower
            val maxIso = range.upper
            isoValues = listOf(minIso, 100, 200, 400, 800, 1600, maxIso).distinct().sorted()
        }
        this.max = isoValues.size - 1

        this.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(progress < isoValues.size){
                    val selectedIso = isoValues[progress]
                    mainActivity.isoValueText.text = selectedIso.toString();

                    // If manual button is active, apply the setting
                    if (mainActivity.isoButton.isSelected) {
                        mainActivity.camConfig.manualIsoValue = selectedIso
                        mainActivity.camConfig.applyManualSettings()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun refreshIsoValues() {
        val range = mainActivity.camConfig.getIsoRange()
        if (range != null) {
            val minIso = range.lower
            val maxIso = range.upper
            isoValues = listOf(minIso, 100, 200, 400, 800, 1600, maxIso).distinct().sorted()
            this.max = isoValues.size - 1
            
            // Sync UI
            if (this.progress >= isoValues.size) {
                this.progress = 0
            }
            val selectedIso = isoValues[this.progress]
            mainActivity.isoValueText.text = selectedIso.toString()
            
            // Sync current ISO to config if manual mode is active
            if (mainActivity.isoButton.isSelected) {
                mainActivity.camConfig.manualIsoValue = selectedIso
                mainActivity.camConfig.applyManualSettings()
            }
        }
    }

    fun getCurrentIsoValue(): Int {
        return if (progress < isoValues.size) isoValues[progress] else isoValues.last()
    }

}