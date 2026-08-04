package app.grapheneos.camera.ui.seekbar

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import app.grapheneos.camera.ui.activities.MainActivity

class ShutterSpeedBar : AppCompatSeekBar {

    // Valores en segundos (Double) para generar los nanosegundos reales
    private val shutterSpeeds = listOf(
        1/1000.0, 1/500.0, 1/250.0, 1/125.0, 1/60.0, 1/30.0,
        1/15.0, 1/8.0, 1/4.0, 1/2.0, 1.0, 2.0
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
                        mainActivity.camConfig.manualExposureValue = selectedNanos.toInt()
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
        val minNanos = range?.lower ?: 100_000L // 1/10000s por defecto
        val maxNanos = range?.upper ?: 1_000_000_000L // 1s por defecto

        // Convertimos nuestra lista de segundos a nanosegundos (1s = 10^9 ns)
        shutterValues = shutterSpeeds.map { (it * 1_000_000_000L).toLong() }
            .filter { it in minNanos..maxNanos }

        this.max = shutterValues.size - 1
    }

    private fun formatShutterSpeed(seconds: Double): String {
        return if (seconds < 1.0) {
            "1/${(1.0 / seconds).toInt()}"
        } else {
            "${seconds.toInt()}s"
        }
    }

    fun getCurrentShutterValue(): Long {
        return if (progress < shutterValues.size) shutterValues[progress] else shutterValues.last()
    }
}