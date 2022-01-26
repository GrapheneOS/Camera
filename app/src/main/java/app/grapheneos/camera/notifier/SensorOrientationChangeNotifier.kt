package app.grapheneos.camera.notifier

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.round


class SensorOrientationChangeNotifier private constructor(
    private val mainActivity: MainActivity
) {

    companion object {
        private var mInstance: SensorOrientationChangeNotifier? = null
        fun getInstance(mActivity: MainActivity): SensorOrientationChangeNotifier? {
            if (mInstance == null) mInstance = SensorOrientationChangeNotifier(mActivity)
            return mInstance
        }

        fun clearInstance() {
            mInstance = null
        }

        // Greater the threshold (in degrees), more the chances of the gyroscope being
        // visible via the ENTRY_CRITERIA
        private const val X_THRESHOLD = 5

        // The gyroscope shall be explicitly made visible only if it's within the ENTRY_
        // CRITERIA and if the device isn't moving too fast i.e. (lastX - currentX) is below
        // threshold
        private const val X_ENTRY_MIN = -10F
        private const val X_ENTRY_MAX = 10F

        // If the current angle for a given axis is beyond the EXIT_CRITERIA the listener
        // will just hide the gyroscope (and just return the control back from the method as
        // executing further statements won't make sense)
        private const val X_EXIT_MIN = -45F
        private const val X_EXIT_MAX = 45F


        private const val Z_THRESHOLD = 5

        private const val Z_ENTRY_MIN = -60F
        private const val Z_ENTRY_MAX = 60F

        private const val Z_EXIT_MIN = -60F
        private const val Z_EXIT_MAX = 60F
    }

    var mOrientation = mainActivity.getRotation()
        private set

    private val mListeners = ArrayList<WeakReference<Listener?>>(3)
    private val mSensorEventListener: NotifierSensorEventListener
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

    inner class NotifierSensorEventListener : SensorEventListener {

        var lastX = 0f
        var lastZ = 0f

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

            if (!camConfig.shouldShowGyroscope()) return

            val dAngle = if (mainActivity.gCircleFrame.rotation == 270f) {
                90f
            } else {
                mainActivity.gCircleFrame.rotation
            }

            val zAngle = (atan(z) / (Math.PI / 180)).toFloat()

            if (zAngle < Z_EXIT_MIN || zAngle > Z_EXIT_MAX) {
                mainActivity.gCircleFrame.visibility = View.GONE
                return
            }

            val hAngle = atan2(x, y) / (Math.PI / 180)
            val aAngle = abs(hAngle)
            val rAngle = (round(aAngle) - dAngle) % 90

            val fAngle = if (hAngle < 0) {
                -rAngle
            } else {
                rAngle
            }.toFloat()


            if (fAngle < X_EXIT_MIN || fAngle > X_EXIT_MAX) {
                mainActivity.gCircleFrame.visibility = View.GONE
                return
            }

            if (fAngle in X_ENTRY_MIN..X_ENTRY_MAX) {
                if (abs(fAngle - lastX) < X_THRESHOLD) {
                    if (zAngle in Z_ENTRY_MIN..Z_ENTRY_MAX) {
                        if (abs(zAngle - lastZ) < Z_THRESHOLD) {
                            mainActivity.gCircleFrame.visibility = View.VISIBLE
                        }
                    }
                }
            }

            updateGyro(fAngle, zAngle)
        }

        fun updateGyro(fAngle: Float, zAngle: Float) {
            mainActivity.onDeviceAngleChange(fAngle, zAngle)

            lastX = fAngle
            lastZ = zAngle
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun forceUpdateGyro() {
        mSensorEventListener.let {
            it.updateGyro(it.lastX, it.lastZ)
        }
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

    fun notifyListeners(manualUpdate: Boolean = false) {

        if (manualUpdate) {
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

    init {
        mSensorEventListener = NotifierSensorEventListener()
        mSensorManager = mainActivity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
}