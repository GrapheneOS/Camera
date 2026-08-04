package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.ui.activities.MainActivity

class ShutterSpeedBar : AppCompatSeekBar {

    // Values in seconds (Double) to generate real ns
    private val shutterSpeeds = listOf(
        1/6000.0, 1/4000.0, 1/3000.0, 1/2000.0, 1/1000.0, 1/750.0, 1/500.0,
        1/350.0, 1/250.0, 1/125.0, 1/60.0, 1/30.0, 1/25.0, 1/15.0, 1/10.0,
        1/8.0, 1/6.0, 1/4.0, 0.3, 0.5, 1.0, 2.0, 4.0, 8.0, 10.0, 15.0, 30.0
    )


    private var shutterValues: List<Long> = emptyList()
    private lateinit var mainActivity: MainActivity

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity
        refreshShutterValues()

        this.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress < shutterValues.size) {
                    val selectedNanos = shutterValues[progress]

                    val label = formatShutterSpeed(shutterSpeeds[progress])
                    mainActivity.shutterSpeedValueText.text = label
                    mainActivity.shutterSpeedValueText.text = label

                    if(mainActivity.camConfig.isManualMode){
                        mainActivity.camConfig.manualExposureValue = selectedNanos
                        mainActivity.camConfig.applyManualSettings()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun refreshShutterValues() {
        val range = mainActivity.camConfig.getShutterRange()
        val minNanos = range?.lower ?: 100_000L // 1/10000s default
        val maxNanos = range?.upper ?: 1_000_000_000L // 1s default

        val validPairs = shutterSpeeds.map { (it * 1_000_000_000L).toLong() to it }
            .filter { it.first in minNanos..maxNanos }

        shutterValues = validPairs.map { it.first }
        val filteredSpeeds = validPairs.map { it.second }

        this.max = shutterValues.size - 1

        if (this.progress >= shutterValues.size) {
            this.progress = 0
        }

        val initialLabel = formatShutterSpeed(filteredSpeeds[this.progress])
        mainActivity.shutterSpeedValueText.text = initialLabel
        mainActivity.shutterButton.text = initialLabel
    }

    private fun formatShutterSpeed(seconds: Double): String {
        return when {
            seconds >= 1.0 -> "${seconds.toInt()}s"
            seconds == 0.3 || seconds == 0.5 -> "$seconds"
            else -> {
                val denominator = (1.0 / seconds + 0.5).toInt()
                "1/$denominator"
            }
        }
    }

    fun getCurrentShutterValue(): Long {
        return if (progress < shutterValues.size) shutterValues[progress] else shutterValues.last()
    }
}