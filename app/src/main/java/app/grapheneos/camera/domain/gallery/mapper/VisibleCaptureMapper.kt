package app.grapheneos.camera.domain.gallery.mapper

import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import javax.inject.Inject

interface VisibleCaptureMapper {
    fun map(item: CapturedItem?): CapturedItem?
}

internal class VisibleCaptureMapperImpl @Inject constructor(
    private val entryPoint: CameraEntryPoint,
) : VisibleCaptureMapper {

    override fun map(item: CapturedItem?): CapturedItem? {
        return when {
            item?.type == ITEM_TYPE_IMAGE && entryPoint.isVideoOnlySession -> null
            else -> item
        }
    }
}
