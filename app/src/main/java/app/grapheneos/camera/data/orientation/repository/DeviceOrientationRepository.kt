package app.grapheneos.camera.data.orientation.repository

import android.content.ContentResolver
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.orientation.model.DeviceMotion
import app.grapheneos.camera.di.core.IoDispatcher
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

interface DeviceOrientationRepository {

    fun motion(): Flow<DeviceMotion>

    fun autoRotateEnabled(): Flow<Boolean>
}

internal class DeviceOrientationRepositoryImpl @Inject constructor(
    private val sensorManager: SensorManager,
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeviceOrientationRepository {

    override fun motion(): Flow<DeviceMotion> {
        return callbackFlow {
            val gravity = FloatArray(size = 3)
            var orientation: DeviceOrientation? = null

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    for (axis in gravity.indices) {
                        gravity[axis] = smooth(
                            previous = gravity[axis],
                            reading = event.values[axis],
                        )
                    }

                    orientation = orientationOf(
                        gravityX = gravity[0],
                        gravityY = gravity[1],
                    ) ?: orientation

                    orientation?.let { current ->
                        val deviceMotion = motionOf(
                            gravity = gravity,
                            orientation = current,
                        )

                        trySend(deviceMotion)
                    }
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
        }
    }

    override fun autoRotateEnabled(): Flow<Boolean> {
        return callbackFlow {
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(isAutoRotateEnabled())
                }
            }

            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                true,
                observer,
            )
            trySend(isAutoRotateEnabled())

            awaitClose {
                contentResolver.unregisterContentObserver(observer)
            }
        }.flowOn(ioDispatcher)
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

    private fun motionOf(
        gravity: FloatArray,
        orientation: DeviceOrientation,
    ): DeviceMotion {
        val isSideways = orientation == DeviceOrientation.DEGREES_90 ||
            orientation == DeviceOrientation.DEGREES_270
        val isUpsideDown = orientation == DeviceOrientation.DEGREES_180 ||
            orientation == DeviceOrientation.DEGREES_270

        val (across, along) = when {
            isSideways -> gravity[1] to gravity[0]
            else -> gravity[0] to gravity[1]
        }
        val depth = gravity[2]

        val tilt = floorDegrees(atan(across / sqrt(along * along + depth * depth)))
        val horizon = floorDegrees(atan(depth / sqrt(along * along + across * across)))

        return DeviceMotion(
            orientation = orientation,
            tiltDegrees = when {
                isUpsideDown -> -tilt
                else -> tilt
            },
            horizonDegrees = horizon,
        )
    }

    private fun floorDegrees(radians: Float): Int {
        return floor(Math.toDegrees(radians.toDouble())).toInt()
    }

    private fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }

    private companion object {
        private const val READING_WEIGHT = 0.3f
        private const val TILT_THRESHOLD = 5f
    }
}
