package app.grapheneos.camera.data.orientation.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.grapheneos.camera.data.core.model.DeviceOrientation
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface DeviceOrientationRepository {
    fun orientation(): Flow<DeviceOrientation>
}

internal class DeviceOrientationRepositoryImpl @Inject constructor(
    private val sensorManager: SensorManager,
) : DeviceOrientationRepository {

    override fun orientation(): Flow<DeviceOrientation> {
        return callbackFlow {
            val gravity = FloatArray(size = 3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    for (axis in gravity.indices) {
                        gravity[axis] = smooth(
                            previous = gravity[axis],
                            reading = event.values[axis],
                        )
                    }

                    orientationOf(
                        gravityX = gravity[0],
                        gravityY = gravity[1],
                    )?.let(::trySend)
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL,
            )

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }.distinctUntilChanged()
    }

    private fun smooth(
        previous: Float,
        reading: Float,
    ): Float {
        return previous + (reading - previous) * READING_WEIGHT
    }

    private fun orientationOf(
        gravityX: Float,
        gravityY: Float,
    ): DeviceOrientation? {
        val isXLevel = abs(gravityX) < TILT_THRESHOLD
        val isYLevel = abs(gravityY) < TILT_THRESHOLD

        return when {
            isXLevel && gravityY > TILT_THRESHOLD -> DeviceOrientation.DEGREES_0
            isYLevel && gravityX < -TILT_THRESHOLD -> DeviceOrientation.DEGREES_90
            isXLevel && gravityY < -TILT_THRESHOLD -> DeviceOrientation.DEGREES_180
            isYLevel && gravityX > TILT_THRESHOLD -> DeviceOrientation.DEGREES_270
            else -> null
        }
    }

    private companion object {
        private const val READING_WEIGHT = 0.3f
        private const val TILT_THRESHOLD = 5f
    }
}
