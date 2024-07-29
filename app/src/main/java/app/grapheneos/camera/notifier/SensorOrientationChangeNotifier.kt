package app.grapheneos.camera.notifier

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import app.grapheneos.camera.ui.activities.MainActivity
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt

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
        private const val X_ENTRY_MIN = -8F
        private const val X_ENTRY_MAX = 8F

        // If the current angle for a given axis is beyond the EXIT_CRITERIA the listener
        // will just hide the gyroscope (and just return the control back from the method as
        // executing further statements won't make sense)
        private const val X_EXIT_MIN = -45F
        private const val X_EXIT_MAX = 45F


        private const val Z_THRESHOLD = 5

        private const val Z_ENTRY_MIN = -10F
        private const val Z_ENTRY_MAX = 10F

        private const val Z_EXIT_MIN = -45F
        private const val Z_EXIT_MAX = 45F
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

        private val ALPHA = 0.7f
        private val filteredValues = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {

            filteredValues[0] = ALPHA * filteredValues[0] + (1 - ALPHA) * event.values[0]
            filteredValues[1] = ALPHA * filteredValues[1] + (1 - ALPHA) * event.values[1]
            filteredValues[2] = ALPHA * filteredValues[2] + (1 - ALPHA) * event.values[2]

            var x : Float = filteredValues[0]
            var y : Float = filteredValues[1]
            var z : Float = filteredValues[2]

            var newOrientation = mOrientation
            if (x < 5 && x > -5 && y > 5) newOrientation =
                0 else if (x < -5 && y < 5 && y > -5) newOrientation =
                90 else if (x < 5 && x > -5 && y < -5) newOrientation =
                180 else if (x > 5 && y < 5 && y > -5) newOrientation = 270

            if (mOrientation != newOrientation) {
                mOrientation = newOrientation
                notifyListeners()
            }

            if (!mainActivity.camConfig.shouldShowGyroscope()) {
                mainActivity.gCircleFrame.visibility = View.GONE
                return
            }

            if (newOrientation == 90 || newOrientation == 270) {
                val t = x
                x = y
                y = t
            }

            x = ((180 / Math.PI) * atan(x / sqrt(y * y + z * z))).toFloat()
            z = ((180 / Math.PI) * atan(z / sqrt(y * y + x * x))).toFloat()

            if (z < Z_EXIT_MIN || z > Z_EXIT_MAX) {
                mainActivity.gCircleFrame.visibility = View.GONE
                return
            }

            if (x < X_EXIT_MIN || x > X_EXIT_MAX) {
                mainActivity.gCircleFrame.visibility = View.GONE
                return
            }

            if (x in X_ENTRY_MIN..X_ENTRY_MAX) {
                if (abs(x - lastX) < X_THRESHOLD) {
                    if (z in Z_ENTRY_MIN..Z_ENTRY_MAX) {
                        if (abs(z - lastZ) < Z_THRESHOLD) {
                            mainActivity.gCircleFrame.visibility = View.VISIBLE
                        }
                    }
                }
            }

            updateGyro(x, z)
        }

        fun updateGyro(xAngle: Float, zAngle: Float) {

            val xAngle = floor(xAngle)
            val zAngle = floor(zAngle)

            mainActivity.onDeviceAngleChange(xAngle, zAngle)

            lastX = xAngle
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
