package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity

class ExposureTimeBar : AppCompatSeekBar {

    // Values in seconds (Double) to generate real ns
    private val exposureTimes = listOf(
        1/6000.0, 1/4000.0, 1/3000.0, 1/2000.0, 1/1000.0, 1/750.0, 1/500.0,
        1/350.0, 1/250.0, 1/125.0, 1/60.0, 1/30.0, 1/25.0, 1/15.0, 1/10.0,
        1/8.0, 1/6.0, 1/4.0, 0.3, 0.5, 1.0, 2.0, 4.0, 8.0, 10.0, 15.0, 30.0
    )


    private var exposureTimeValues: List<Long> = emptyList()
    private lateinit var mainActivity: MainActivity

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    fun setMainActivity(mainActivity: MainActivity) {
        this.mainActivity = mainActivity

        this.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress < exposureTimeValues.size) {
                    val selectedNanos = exposureTimeValues[progress]

                    val label = formatExposureValuesSpeed(exposureTimes[progress])
                    mainActivity.exposureTimeValueText.text = label
                    mainActivity.exposureTimeValueText.text = label

                    if(mainActivity.camConfig.isManualMode){
                        mainActivity.camConfig.manualExposureTimeValue = selectedNanos
                        mainActivity.camConfig.applyManualSettings()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun refreshExposureTimeValues() {
        val range = mainActivity.camConfig.getShutterRange()
        val minNanos = range?.lower ?: 100_000L // 1/10000s default
        val maxNanos = range?.upper ?: 1_000_000_000L // 1s default

        val validPairs = exposureTimes.map { (it * 1_000_000_000.0).toLong() to it }
            .filter { it.first in minNanos..maxNanos }

        exposureTimeValues = validPairs.map { it.first }
        val filteredSpeeds = validPairs.map { it.second }

        this.max = exposureTimeValues.size - 1

        if (this.progress >= exposureTimeValues.size) {
            this.progress = 0
        }

        val initialLabel = formatExposureValuesSpeed(filteredSpeeds[this.progress])
        mainActivity.exposureTimeValueText.text = initialLabel
    }

    private fun formatExposureValuesSpeed(seconds: Double): String {
        return when {
            seconds >= 1.0 -> context.getString(R.string.exposure_time_seconds, seconds.toInt())
            seconds == 0.3 || seconds == 0.5 -> context.getString(R.string.exposure_time_decimal, seconds)
            else -> {
                val denominator = (1.0 / seconds + 0.5).toInt()
                context.getString(R.string.exposure_time_fraction, denominator)
            }
        }
    }

    fun getCurrentExposureTimeValue(): Long {
        return if (progress < exposureTimeValues.size) exposureTimeValues[progress] else exposureTimeValues.last()
    }
}