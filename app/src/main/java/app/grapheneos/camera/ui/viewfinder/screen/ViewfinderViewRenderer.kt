package app.grapheneos.camera.ui.viewfinder.screen

import android.view.View
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

internal class ViewfinderViewRenderer(
    private val activity: MainActivity,
) {

    private var renderedThumbnailLoaderVisible = false
    private var renderedModes: Set<CameraMode> = emptySet()
    private var renderedAspectRatio: AspectRatio? = null
    private var renderedSensorOrientationDegrees: Int? = null
    private var renderedInPhotoMode: Boolean? = null

    fun render(state: ViewfinderUiState) {
        activity.qrOverlay.visibility = visibleOrInvisible(state.qrOverlayVisible)
        activity.qrScanToggles.visibility = visibleOrGone(state.qrScanTogglesVisible)
        activity.thirdOption.visibility = visibleOrInvisible(state.thirdOptionVisible)
        activity.cancelButtonView.visibility = visibleOrInvisible(state.cancelButtonVisible)
        activity.tabLayout.visibility = visibleOrInvisible(state.modeTabsVisible)

        activity.micOffIcon.visibility = visibleOrGone(state.micMutedIconVisible)
        activity.muteToggle.visibility = visibleOrGone(state.muteToggleVisible)
        activity.setMuteToggleState(muted = state.isRecordingMuted)
        activity.timerView.visibility = visibleOrGone(state.recordingTimerVisible)
        activity.timerView.text = state.recordingTimerText

        activity.previewView.keepScreenOn = state.keepScreenOn
        activity.previewView.visibility = visibleOrInvisible(state.cameraPreviewVisible)
        (activity as? CaptureActivity)?.renderCapturedPreview(state.capturedPreviewVisible)

        activity.captureButton.render(state.captureButton)
        activity.setThirdCircleIcon(
            icon = state.thirdCircleIcon,
            description = state.thirdCircleDescription,
        )
        activity.setFlipCameraIcon(
            icon = state.flipCameraIcon,
            description = state.flipCameraDescription,
        )

        activity.previewGrid.gridType = state.gridType
        activity.cbText.text = state.selfTimerBadge
        activity.cbText.visibility = visibleOrInvisible(state.selfTimerBadgeVisible)
        activity.cdTimer.visibility = visibleOrGone(state.selfTimerCountdownVisible)
        activity.cbCross.visibility = visibleOrInvisible(state.selfTimerCancelVisible)

        activity.settingsDialog.render(state.settingsSheet)
        activity.zoomBar.render(state.zoom)
        activity.exposureBar.render(state.exposure)

        renderThumbnailLoader(state.thumbnailLoaderVisible)
        renderModeTabs(state)
        renderBoundPreview(state)
    }

    private fun renderThumbnailLoader(visible: Boolean) {
        if (visible == renderedThumbnailLoaderVisible) return
        renderedThumbnailLoaderVisible = visible

        activity.previewLoader.visibility = visibleOrGone(visible)
    }

    private fun renderModeTabs(state: ViewfinderUiState) {
        if (state.availableModes == renderedModes) return
        renderedModes = state.availableModes

        activity.tabLayout.setModes(
            modes = state.availableModes,
            currentMode = state.mode,
            onTabTouched = activity::finalizeMode,
        )
    }

    private fun renderBoundPreview(state: ViewfinderUiState) {
        val sensorOrientationDegrees = state.sensorOrientationDegrees ?: return

        activity.previewView.setOnTouchListener(activity.gestureHandler)

        if (state.aspectRatio != renderedAspectRatio ||
            sensorOrientationDegrees != renderedSensorOrientationDegrees
        ) {
            renderedAspectRatio = state.aspectRatio
            renderedSensorOrientationDegrees = sensorOrientationDegrees

            activity.previewView.applyPreviewRatio(
                aspectRatio = state.aspectRatio,
                sensorOrientationDegrees = sensorOrientationDegrees,
            )
        }

        if (state.inPhotoMode != renderedInPhotoMode) {
            renderedInPhotoMode = state.inPhotoMode

            when {
                state.inPhotoMode -> activity.sensorNotifier?.forceUpdateGyro()
                else -> activity.gCircleFrame.visibility = View.GONE
            }
        }
    }

    private fun visibleOrInvisible(visible: Boolean): Int {
        return when {
            visible -> View.VISIBLE
            else -> View.INVISIBLE
        }
    }

    private fun visibleOrGone(visible: Boolean): Int {
        return when {
            visible -> View.VISIBLE
            else -> View.GONE
        }
    }
}
