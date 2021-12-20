package app.grapheneos.camera.notifier

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.round


class SensorOrientationChangeNotifier private constructor(
        private val mainActivity: MainActivity) {

    var mOrientation = mainActivity.getRotation()
        private set

    private val mListeners = ArrayList<WeakReference<Listener?>>(3)
    private val mSensorEventListener: SensorEventListener
    private val mSensorManager: SensorManager

    /**
     * Call on activity reset()
     */
    private fun onResume() {
        mSensorManager.registerListener(
            mSensorEventListener,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        notifyListeners(true)
    }

    /**
     * Call on activity onPause()
     */
    private fun onPause() {
        mSensorManager.unregisterListener(mSensorEventListener)
    }

    private inner class NotifierSensorEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            var newOrientation = mOrientation
            if (x < 5 && x > -5 && y > 5) newOrientation =
                0 else if (x < -5 && y < 5 && y > -5) newOrientation =
                90 else if (x < 5 && x > -5 && y < -5) newOrientation =
                180 else if (x > 5 && y < 5 && y > -5) newOrientation = 270

            if (mOrientation != newOrientation) {
                mOrientation = newOrientation
                notifyListeners()
            }

            if (!camConfig.gSuggestions)  return

            val dAngle = if (mainActivity.gCircleFrame.rotation == 270f) {
                90f
            } else {
                mainActivity.gCircleFrame.rotation
            }

            val hAngle = atan2(x, y) / (Math.PI / 180)
            val aAngle = abs(hAngle)
            val rAngle = (round(aAngle) - dAngle) % 90

            val fAngle = if (hAngle < 0) {
                -rAngle
            } else {
                rAngle
            }.toFloat()

            val vAngle = atan(z) / (Math.PI / 180)

            mainActivity.onDeviceAngleChange(fAngle, vAngle.toFloat())
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    interface Listener {
        fun onOrientationChange(orientation: Int)
    }

    fun addListener(listener: Listener) {
        if (get(listener) == null) // prevent duplications
            mListeners.add(WeakReference(listener))
        if (mListeners.size == 1) {
            onResume() // this is the first client
        }
    }

    fun remove(listener: Listener) {
        val listenerWR = get(listener)
        remove(listenerWR)
    }

    private fun remove(listenerWR: WeakReference<Listener?>?) {
        if (listenerWR != null) mListeners.remove(listenerWR)
        if (mListeners.size == 0) {
            onPause()
        }
    }

    private operator fun get(listener: Listener): WeakReference<Listener?>? {
        for (existingListener in mListeners) if (existingListener.get() === listener) return existingListener
        return null
    }

    fun notifyListeners(manualUpdate : Boolean = false) {

        if(manualUpdate){
           mOrientation = mainActivity.getRotation()
        }

        val deadLinksArr = ArrayList<WeakReference<Listener?>>()
        for (wr in mListeners) {
            if (wr.get() == null) deadLinksArr.add(wr) else wr.get()!!
                .onOrientationChange(mOrientation)
        }

        // remove dead references
        for (wr in deadLinksArr) {
            mListeners.remove(wr)
        }
    }

    companion object {
        private var mInstance: SensorOrientationChangeNotifier? = null
        fun getInstance(mActivity: MainActivity): SensorOrientationChangeNotifier? {
            if (mInstance == null) mInstance = SensorOrientationChangeNotifier(mActivity)
            return mInstance
        }
    }

    init {
        mSensorEventListener = NotifierSensorEventListener()
        mSensorManager = mainActivity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
}