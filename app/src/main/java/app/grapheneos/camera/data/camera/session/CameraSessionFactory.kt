package app.grapheneos.camera.data.camera.session

import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject

interface CameraSessionFactory {
    fun create(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        meteringPointFactory: MeteringPointFactory,
    ): CameraSession
}

internal class CameraSessionFactoryImpl @Inject constructor(
    private val factory: CameraSessionImpl.Factory,
) : CameraSessionFactory {

    override fun create(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        meteringPointFactory: MeteringPointFactory,
    ): CameraSession {
        return factory.create(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            meteringPointFactory = meteringPointFactory,
        )
    }
}
