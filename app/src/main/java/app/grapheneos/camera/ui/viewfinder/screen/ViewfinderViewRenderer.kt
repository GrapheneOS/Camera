package app.grapheneos.camera.ui.viewfinder.screen

import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlin.math.abs

internal class ViewfinderViewRenderer(
    private val activity: MainActivity,
) {

    private var renderedThumbnailLoaderVisible = false
    private var renderedModes: Set<CameraMode> = emptySet()
    private var renderedAspectRatio: AspectRatio? = null
    private var renderedSensorOrientationDegrees: Int? = null
    private var renderedIconRotation: Float? = null
    private var renderedLevelFrameRotation: Float? = null

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
        renderRotation(state)
    }

    fun renderLevel(level: LevelUiState) {
        activity.gCircleFrame.visibility = visibleOrGone(level.visible)

        renderTilt(level.tiltDegrees)
        renderHorizon(level.horizonDegrees)
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
    }

    private fun renderRotation(state: ViewfinderUiState) {
        if (state.iconRotationDegrees != renderedIconRotation) {
            renderedIconRotation = state.iconRotationDegrees

            rotatingIcons().forEach { icon ->
                rotate(icon, state.iconRotationDegrees)
            }
        }

        if (state.levelFrameRotationDegrees != renderedLevelFrameRotation) {
            renderedLevelFrameRotation = state.levelFrameRotationDegrees

            rotate(activity.gCircleFrame, state.levelFrameRotationDegrees)
        }
    }

    private fun rotatingIcons(): List<View> {
        return listOf(
            activity.flipCameraCircle,
            activity.cancelButtonView,
            activity.thirdOption,
            activity.binding.exposurePlusIcon,
            activity.binding.exposureNegIcon,
            activity.binding.zoomInIcon,
            activity.binding.zoomOutIcon,
            activity.settingsDialog.settingsFrame,
            activity.micOffIcon,
            activity.muteToggle,
        )
    }

    private fun rotate(
        view: View,
        degrees: Float,
    ) {
        view.animate().cancel()

        // Ensuring that the rotation seems continuous
        if (view.rotation == 0f && degrees == 270f) {
            view.rotation = 360f
        }

        if (view.rotation == 270f && degrees == 0f) {
            view.rotation = -90f
        }

        view.animate()
            .rotation(degrees)
            .setDuration(ROTATION_DURATION)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun renderTilt(tiltDegrees: Int) {
        val binding = activity.binding
        val isLevel = tiltDegrees == 0

        binding.gCircle.rotation = tiltDegrees.toFloat()
        binding.gCircleLineZ.rotation = tiltDegrees.toFloat()
        binding.gCircleText.text = activity.getString(R.string.degree_format, abs(tiltDegrees))

        val thickness = when {
            isLevel -> LEVEL_LINE_THICKNESS_DP
            else -> TILTED_LINE_THICKNESS_DP
        }

        listOf(
            binding.gCircleLeftDash,
            binding.gCircleRightDash,
            binding.gCircleLineX,
        ).forEach { line ->
            line.updateLayoutParams {
                height = pixels(thickness)
            }
        }
    }

    private fun renderHorizon(horizonDegrees: Int) {
        val binding = activity.binding
        val isLevel = horizonDegrees == 0

        val lineBackground = when {
            isLevel -> R.drawable.yellow_shadow_rect
            else -> R.drawable.white_shadow_rect
        }
        val textColor = when {
            isLevel -> R.color.z_yellow
            else -> android.R.color.white
        }

        binding.gCircleLineX.setBackgroundResource(lineBackground)
        binding.gCircleLeftDash.setBackgroundResource(lineBackground)
        binding.gCircleRightDash.setBackgroundResource(lineBackground)
        binding.gCircleText.setTextColor(ContextCompat.getColor(activity, textColor))
        binding.gCircleLineZ.visibility = visibleOrGone(!isLevel)

        val clampedHorizon = horizonDegrees.coerceIn(-HORIZON_RANGE_DEGREES, HORIZON_RANGE_DEGREES)
        val horizonOffset = clampedHorizon / HORIZON_OFFSET_DIVISOR * pixels(HORIZON_OFFSET_DP)

        binding.gCircleLineZ.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = horizonOffset.toInt()
        }
    }

    private fun pixels(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
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

    private companion object {
        private const val ROTATION_DURATION = 400L
        private const val LEVEL_LINE_THICKNESS_DP = 4
        private const val TILTED_LINE_THICKNESS_DP = 2
        private const val HORIZON_RANGE_DEGREES = 45
        private const val HORIZON_OFFSET_DIVISOR = 60f
        private const val HORIZON_OFFSET_DP = 32
    }
}
