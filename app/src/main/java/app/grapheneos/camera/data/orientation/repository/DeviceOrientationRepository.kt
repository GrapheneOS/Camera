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
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.IoDispatcher
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

interface DeviceOrientationRepository {

    val motion: Flow<DeviceMotion>

    val autoRotateEnabled: Flow<Boolean>
}

internal class DeviceOrientationRepositoryImpl @Inject constructor(
    private val sensorManager: SensorManager,
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope,
) : DeviceOrientationRepository {

    override val motion: Flow<DeviceMotion> = observeMotion()
        .conflate()
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(replayExpiration = Duration.ZERO),
            replay = 1,
        )

    override val autoRotateEnabled: Flow<Boolean> = observeAutoRotate()
        .flowOn(ioDispatcher)

    private fun observeMotion(): Flow<DeviceMotion> {
        return callbackFlow {
            var gravity: FloatArray? = null
            var orientation = DeviceOrientation.DEGREES_0
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val sensor = gravitySensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val reading = event.values.copyOf(newSize = GRAVITY_AXES)
                    val previous = gravity
                    val current = when {
                        gravitySensor != null || previous == null -> reading
                        else -> smooth(previous = previous, reading = reading)
                    }
                    gravity = current
                    orientation = orientationOf(
                        gravityX = current[0],
                        gravityY = current[1],
                    ) ?: orientation

                    trySend(motionOf(gravity = current, orientation = orientation))
                }

                @Suppress("EmptyFunctionBlock")
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    private fun observeAutoRotate(): Flow<Boolean> {
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
        }
    }

    private fun smooth(
        previous: FloatArray,
        reading: FloatArray,
    ): FloatArray {
        return FloatArray(size = reading.size) { axis ->
            previous[axis] + (reading[axis] - previous[axis]) * READING_WEIGHT
        }
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
        val isVerticalAxisReversed = orientation == DeviceOrientation.DEGREES_90 ||
            orientation == DeviceOrientation.DEGREES_180

        val across = when {
            isSideways -> gravity[1]
            else -> gravity[0]
        }
        val along = when {
            isSideways -> gravity[0]
            else -> gravity[1]
        }
        val depth = gravity[2]

        val horizontal = when {
            isUpsideDown -> -across
            else -> across
        }
        val vertical = when {
            isVerticalAxisReversed -> -along
            else -> along
        }
        val tilt = degrees(atan2(horizontal, vertical))
        val horizon = degrees(atan2(depth, sqrt(along * along + across * across)))

        return DeviceMotion(
            orientation = orientation,
            tiltDegrees = tilt,
            horizonDegrees = horizon,
        )
    }

    private fun degrees(radians: Float): Float {
        return Math.toDegrees(radians.toDouble()).toFloat()
    }

    private fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }

    private companion object {
        private const val GRAVITY_AXES = 3
        private const val READING_WEIGHT = 0.3f
        private const val TILT_THRESHOLD = 5f
    }
}
