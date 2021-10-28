package app.grapheneos.camera.notifier

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.grapheneos.camera.ui.activities.MainActivity
import java.lang.ref.WeakReference


class SensorOrientationChangeNotifier private constructor(mainActivity: MainActivity) {
    private val mListeners = ArrayList<WeakReference<Listener?>>(3)
    private var mOrientation = 0
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
            var newOrientation = mOrientation
            if (x < 5 && x > -5 && y > 5) newOrientation =
                0 else if (x < -5 && y < 5 && y > -5) newOrientation =
                90 else if (x < 5 && x > -5 && y < -5) newOrientation =
                180 else if (x > 5 && y < 5 && y > -5) newOrientation = 270

            //Log.e(TAG,"mOrientation="+mOrientation+"   ["+event.values[0]+","+event.values[1]+","+event.values[2]+"]");
            if (mOrientation != newOrientation) {
                mOrientation = newOrientation
                notifyListeners()
            }
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

    private fun notifyListeners() {
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