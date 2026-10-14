package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.VolumeKey
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomDirection
import javax.inject.Inject

interface VolumeKeyActionMapper {

    fun map(
        event: CaptureAction.VolumeKeyEvent,
        volumeKeyAction: VolumeKeyAction,
    ): ViewfinderAction?
}

internal class VolumeKeyActionMapperImpl @Inject constructor() : VolumeKeyActionMapper {

    override fun map(
        event: CaptureAction.VolumeKeyEvent,
        volumeKeyAction: VolumeKeyAction,
    ): ViewfinderAction? {
        return when (volumeKeyAction) {
            VolumeKeyAction.SHUTTER -> shutter(event)
            VolumeKeyAction.ZOOM -> zoom(event)
            VolumeKeyAction.VOLUME -> null
            VolumeKeyAction.OFF -> null
        }
    }

    private fun shutter(event: CaptureAction.VolumeKeyEvent): ViewfinderAction? {
        return when (event) {
            is CaptureAction.VolumeKeyEvent.Pressed -> null
            is CaptureAction.VolumeKeyEvent.Released -> CaptureAction.CaptureKeyPressed
        }
    }

    private fun zoom(event: CaptureAction.VolumeKeyEvent): ViewfinderAction {
        return when (event) {
            is CaptureAction.VolumeKeyEvent.Released -> CameraAction.ZoomStepKey.Released

            is CaptureAction.VolumeKeyEvent.Pressed -> when (event.key) {
                VolumeKey.UP -> CameraAction.ZoomStepKey.Pressed(ZoomDirection.IN)
                VolumeKey.DOWN -> CameraAction.ZoomStepKey.Pressed(ZoomDirection.OUT)
            }
        }
    }
}
