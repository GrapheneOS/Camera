package app.grapheneos.camera.ui.camerascreen

import android.app.Activity
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.camerasettings.navigation.CameraSettingsIntents
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.gallery.navigation.GalleryIntents
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsNavEvent
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarNavEvent
import app.grapheneos.camera.ui.videoplayer.navigation.VideoPlayerLauncher
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent

internal interface CameraScreenNavigationHandler {

    fun handle(event: ViewfinderNavEvent)
    fun handle(event: QuickSettingsNavEvent)
    fun handle(event: StatusBarNavEvent)
}

internal class CameraScreenNavigationHandlerImpl(
    private val activity: Activity,
    private val entryPoint: CameraEntryPoint,
    private val videoPlayerLauncher: VideoPlayerLauncher,
    private val cameraSettingsIntents: CameraSettingsIntents,
    private val galleryIntents: GalleryIntents,
    private val onOpenSettingsPanel: (QuickSetting?) -> Unit,
    private val onCloseSettingsPanel: () -> Unit,
    private val onCloseScreen: () -> Unit,
) : CameraScreenNavigationHandler {

    override fun handle(event: ViewfinderNavEvent) {
        when (event) {
            is ViewfinderNavEvent.CloseScreen -> onCloseScreen()
            is ViewfinderNavEvent.OpenSettingsPanel -> onOpenSettingsPanel(event.highlight)
            is ViewfinderNavEvent.CloseSettingsPanel -> onCloseSettingsPanel()
            is ViewfinderNavEvent.OpenCameraSettings -> openCameraSettings(event.capabilities)

            is ViewfinderNavEvent.OpenVideo -> videoPlayerLauncher.open(
                activity = activity,
                uri = event.uri,
            )

            is ViewfinderNavEvent.OpenGallery -> galleryIntents.open(
                activity = activity,
                entryPoint = entryPoint,
                lastCapturedItem = event.lastCapturedItem,
            )

            is ViewfinderNavEvent.OpenSecureGallery -> galleryIntents.openSecure(
                activity = activity,
                entryPoint = entryPoint,
                capturedItems = event.capturedItems,
                lastCapturedItem = event.lastCapturedItem,
            )
        }
    }

    override fun handle(event: QuickSettingsNavEvent) {
        when (event) {
            is QuickSettingsNavEvent.OpenCameraSettings -> openCameraSettings(
                entryPoint = event.entryPoint,
                capabilities = event.capabilities,
            )
        }
    }

    override fun handle(event: StatusBarNavEvent) {
        when (event) {
            is StatusBarNavEvent.OpenCameraSettings -> openCameraSettings(
                entryPoint = event.entryPoint,
                capabilities = event.capabilities,
            )
        }
    }

    private fun openCameraSettings(
        capabilities: CameraCapabilities,
        entryPoint: CameraEntryPoint = this.entryPoint,
    ) {
        cameraSettingsIntents.open(
            activity = activity,
            entryPoint = entryPoint,
            capabilities = capabilities,
        )
    }
}
