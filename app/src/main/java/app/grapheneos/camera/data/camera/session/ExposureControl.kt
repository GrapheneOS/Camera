package app.grapheneos.camera.data.camera.session

import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraExposure
import javax.inject.Inject

interface ExposureControl {

    val exposure: CameraExposure?

    fun setCompensationIndex(index: Int)
}

internal class ExposureControlImpl @Inject constructor(
    private val session: CameraSession,
    private val cameraXStateMapper: CameraXStateMapper,
) : ExposureControl {

    override val exposure: CameraExposure?
        get() {
            return session.camera?.cameraInfo?.exposureState?.let { cameraXStateMapper.map(it) }
        }

    override fun setCompensationIndex(index: Int) {
        session.camera?.cameraControl?.setExposureCompensationIndex(index)
    }
}
