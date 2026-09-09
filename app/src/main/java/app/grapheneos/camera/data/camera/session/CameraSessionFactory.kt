package app.grapheneos.camera.data.camera.session

import javax.inject.Inject

interface CameraSessionFactory {
    fun create(environment: CameraSessionEnvironment): CameraSession
}

internal class CameraSessionFactoryImpl @Inject constructor(
    private val factory: CameraSessionImpl.Factory,
) : CameraSessionFactory {

    override fun create(environment: CameraSessionEnvironment): CameraSession {
        return factory.create(environment)
    }
}
