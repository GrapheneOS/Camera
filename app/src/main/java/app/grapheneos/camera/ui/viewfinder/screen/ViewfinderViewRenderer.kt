package app.grapheneos.camera.ui.viewfinder.screen

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.transition.Fade
import androidx.transition.TransitionManager
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.core.theme.cameraColorScheme
import app.grapheneos.camera.ui.viewfinder.screen.model.CaptureButtonUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ExposureUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.FlipCameraUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.PreviewUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.QrUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.RotationUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.SelfTimerUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ThirdOptionUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import com.google.android.material.color.MaterialColors
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
    private var renderedKeepScreenOn = false
    private var renderedSelfIlluminating = false
    private var litFraction = 0f
    private var settingsPanelFraction = 0f
    private val panelTextColor by lazy { panelTextColor() }
    private var renderedThumbnail: Bitmap? = null

    fun render(state: ViewfinderUiState) {
        renderPreview(state)
        renderQr(state.qr)
        renderModeTabs(state)
        renderCancelButton(state.cancelButtonVisible)
        renderCaptureButton(state.captureButton)
        renderFlipCamera(state.flipCamera)
        renderThirdOption(state.thirdOption)
        renderSelfTimer(state.selfTimer)
        renderRecording(state.recording)
        renderExposure(state.exposure)
        renderKeepScreenOn(state.keepScreenOn)
        renderSelfIllumination(state.isSelfIlluminating)
        renderRotation(state.rotation)
    }

    fun renderZoom(zoom: ZoomUiState) {
        activity.zoomBar.render(zoom)
        renderPanel(
            panel = activity.zoomBarPanel,
            visible = zoom.panelVisible,
            fadeTarget = R.id.zoom_bar_panel,
        )
    }

    fun renderLevel(level: LevelUiState) {
        activity.gCircleFrame.visibility = visibleOrGone(level.visible)

        renderTilt(level.tiltDegrees)
        renderHorizon(level.horizonDegrees)
    }

    private fun renderPreview(state: ViewfinderUiState) {
        activity.previewView.visibility = visibleOrInvisible(!state.capturedPreviewVisible)
        (activity as? CaptureActivity)?.renderCapturedPreview(state.capturedPreviewVisible)
        activity.previewGrid.gridType = state.preview.gridType

        renderBoundPreview(state.preview)
    }

    private fun renderQr(qr: QrUiState) {
        activity.qrOverlay.visibility = visibleOrInvisible(qr.overlayVisible)
        activity.qrScanToggles.visibility = visibleOrGone(qr.scanTogglesVisible)
    }

    private fun renderCancelButton(visible: Boolean) {
        activity.cancelButtonView.visibility = visibleOrInvisible(visible)
    }

    private fun renderCaptureButton(captureButton: CaptureButtonUiState) {
        activity.captureButton.render(captureButton)
    }

    private fun renderFlipCamera(flipCamera: FlipCameraUiState) {
        activity.flipCameraCircle.visibility = visibleOrInvisible(flipCamera.visible)
        activity.setFlipCameraIcon(
            icon = flipCamera.icon,
            description = flipCamera.description,
        )
    }

    private fun renderThirdOption(thirdOption: ThirdOptionUiState) {
        activity.thirdOption.visibility = visibleOrInvisible(thirdOption.visible)
        activity.setThirdCircleIcon(
            icon = thirdOption.icon,
            description = thirdOption.description,
        )

        renderThumbnailLoader(thirdOption.thumbnailLoaderVisible)
        renderThumbnail(thirdOption.thumbnail)
    }

    private fun renderSelfTimer(selfTimer: SelfTimerUiState) {
        val isRunning = selfTimer.remaining != null

        activity.cbText.text = selfTimer.badge
        activity.cbText.visibility = visibleOrInvisible(selfTimer.badgeVisible)
        activity.cdTimer.visibility = visibleOrGone(isRunning)
        activity.cdTimer.render(selfTimer.remaining)
        activity.cbCross.visibility = visibleOrInvisible(isRunning)
    }

    private fun renderRecording(recording: RecordingUiState) {
        activity.micOffIcon.visibility = visibleOrGone(recording.micOffIconVisible)
        activity.muteToggle.visibility = visibleOrGone(recording.muteToggleVisible)
        activity.setMuteToggleState(muted = recording.isMuted)
        activity.timerView.visibility = visibleOrGone(recording.timerVisible)
        activity.timerView.text = recording.timerText
    }

    private fun renderExposure(exposure: ExposureUiState) {
        activity.exposureBar.render(exposure)
        renderPanel(
            panel = activity.exposureBarPanel,
            visible = exposure.panelVisible,
            fadeTarget = R.id.exposure_bar,
        )
    }

    private fun renderPanel(
        panel: View,
        visible: Boolean,
        @IdRes fadeTarget: Int,
    ) {
        val visibility = visibleOrGone(visible)
        if (panel.visibility == visibility) return

        val transition = Fade().addTarget(fadeTarget)
        transition.duration = when {
            visible -> 0L
            else -> PANEL_FADE_OUT_DURATION
        }

        TransitionManager.beginDelayedTransition(
            activity.window.decorView.rootView as ViewGroup,
            transition,
        )
        panel.visibility = visibility
    }

    private fun renderKeepScreenOn(keepScreenOn: Boolean) {
        if (keepScreenOn == renderedKeepScreenOn) return
        renderedKeepScreenOn = keepScreenOn

        when {
            keepScreenOn -> activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else -> activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun renderSelfIllumination(selfIlluminating: Boolean) {
        if (selfIlluminating == renderedSelfIlluminating) return
        renderedSelfIlluminating = selfIlluminating

        animateSelfIllumination(selfIlluminating)
        renderScreenBrightness(selfIlluminating)
    }

    private fun animateSelfIllumination(lit: Boolean) {
        val tabLayout = activity.tabLayout
        val light = activity.getColor(R.color.self_illumination_light)
        val indicator = MaterialColors.getColor(tabLayout, androidx.appcompat.R.attr.colorPrimary)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SELF_ILLUMINATION_DURATION
            addUpdateListener { animator ->
                val fraction = when {
                    lit -> animator.animatedFraction
                    else -> 1f - animator.animatedFraction
                }
                val background = ColorUtils.blendARGB(Color.BLACK, light, fraction)
                litFraction = fraction

                activity.previewView.setBackgroundColor(background)
                activity.rootView.setBackgroundColor(background)
                activity.bottomOverlay.setBackgroundColor(background)
                renderTabTextColors()
                tabLayout.setSelectedTabIndicatorColor(
                    ColorUtils.blendARGB(indicator, Color.BLACK, fraction),
                )
            }
            start()
        }
    }

    fun renderSettingsPanelFraction(fraction: Float) {
        settingsPanelFraction = fraction
        renderTabTextColors()
    }

    private fun renderTabTextColors() {
        val tabLayout = activity.tabLayout
        val selectedText = MaterialColors.getColor(
            tabLayout,
            com.google.android.material.R.attr.colorOnPrimary,
        )
        val text = ColorUtils.blendARGB(Color.WHITE, panelTextColor, settingsPanelFraction)

        tabLayout.setTabTextColors(
            ColorUtils.blendARGB(text, Color.BLACK, litFraction),
            ColorUtils.blendARGB(selectedText, Color.WHITE, litFraction),
        )
    }

    private fun panelTextColor(): Int {
        val nightMode = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK

        return cameraColorScheme(
            context = activity,
            darkTheme = nightMode == Configuration.UI_MODE_NIGHT_YES,
        ).onSurface.toArgb()
    }

    private fun renderScreenBrightness(lit: Boolean) {
        val attributes = activity.window.attributes
        attributes.screenBrightness = when {
            lit -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        activity.window.attributes = attributes
    }

    private fun renderThumbnailLoader(visible: Boolean) {
        if (visible == renderedThumbnailLoaderVisible) return
        renderedThumbnailLoaderVisible = visible

        activity.previewLoader.visibility = visibleOrGone(visible)
    }

    private fun renderThumbnail(thumbnail: Bitmap?) {
        if (thumbnail === renderedThumbnail) return
        renderedThumbnail = thumbnail

        activity.imagePreview.setImageBitmap(thumbnail)
    }

    private fun renderModeTabs(state: ViewfinderUiState) {
        activity.tabLayout.visibility = visibleOrInvisible(state.modeTabs.visible)

        val availableModes = state.modeTabs.availableModes
        if (availableModes == renderedModes) return
        renderedModes = availableModes

        activity.tabLayout.setModes(
            modes = availableModes,
            currentMode = state.mode,
            onTabTouched = activity::finalizeMode,
        )
    }

    private fun renderBoundPreview(preview: PreviewUiState) {
        val sensorOrientationDegrees = preview.sensorOrientationDegrees ?: return

        activity.previewView.setOnTouchListener(activity.gestureHandler)

        if (preview.aspectRatio != renderedAspectRatio ||
            sensorOrientationDegrees != renderedSensorOrientationDegrees
        ) {
            renderedAspectRatio = preview.aspectRatio
            renderedSensorOrientationDegrees = sensorOrientationDegrees

            activity.previewView.applyPreviewRatio(
                aspectRatio = preview.aspectRatio,
                sensorOrientationDegrees = sensorOrientationDegrees,
            )
        }
    }

    private fun renderRotation(rotation: RotationUiState) {
        if (rotation.iconDegrees != renderedIconRotation) {
            renderedIconRotation = rotation.iconDegrees

            rotatingIcons().forEach { icon ->
                rotate(icon, rotation.iconDegrees)
            }
        }

        if (rotation.levelFrameDegrees != renderedLevelFrameRotation) {
            renderedLevelFrameRotation = rotation.levelFrameDegrees

            rotate(activity.gCircleFrame, rotation.levelFrameDegrees)
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
            activity.micOffIcon,
            activity.muteToggle,
        )
    }

    @Suppress("MagicNumber")
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
        private const val SELF_ILLUMINATION_DURATION = 300L
        private const val PANEL_FADE_OUT_DURATION = 300L
        private const val LEVEL_LINE_THICKNESS_DP = 4
        private const val TILTED_LINE_THICKNESS_DP = 2
        private const val HORIZON_RANGE_DEGREES = 45
        private const val HORIZON_OFFSET_DIVISOR = 60f
        private const val HORIZON_OFFSET_DP = 32
    }
}
