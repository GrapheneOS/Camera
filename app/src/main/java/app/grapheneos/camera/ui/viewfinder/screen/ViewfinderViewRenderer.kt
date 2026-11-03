package app.grapheneos.camera.ui.viewfinder.screen

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toArgb
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.TransitionManager
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.core.theme.cameraColorScheme
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureButtonUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureResultUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ExposureUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FlipCameraUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FocusRingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.PreviewUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.QrUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RecordingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RotationUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.SelfTimerUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ThirdOptionUiModel
import app.grapheneos.camera.util.setBlurBitmapCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ViewfinderViewRenderer(
    private val activity: MainActivity,
    private val onAction: (ViewfinderAction) -> Unit,
) {

    private val modeStripRenderer by lazy {
        ViewfinderModeStripRenderer(
            tabs = activity.tabLayout,
            onAction = onAction,
        )
    }

    private var renderedThumbnailLoaderVisible = false
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
    private var renderedFocusRingId: Int? = null
    private var renderedLensFacing: LensFacing? = null
    private var renderedLastFrameVisible: Boolean? = null
    private var renderedStreaming: Boolean? = null
    private var blursLastFrame = true
    private var renderedResultVisible = false
    private var renderedResultImage: Bitmap? = null
    private var isFocusRingRendered = false
    private var focusRingTimeout: Job? = null

    fun render(state: ViewfinderUiState) {
        renderPreview(state)
        renderQr(state.qr)
        modeStripRenderer.render(state.modeTabs)
        renderCancelButton(state.cancelButtonVisible)
        renderCaptureButton(state.captureButton)
        renderFlipCamera(state.flipCamera)
        renderThirdOption(state.thirdOption)
        renderSelfTimer(state.selfTimer)
        renderRecording(state.recording)
        renderFocusRing(state.focusRing)
        renderExposure(state.exposure)
        renderKeepScreenOn(state.keepScreenOn)
        renderSelfIllumination(state.isSelfIlluminating)
        renderRotation(state.rotation)
        renderSettingsPanelAvailability(state.isSettingsPanelAvailable)
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
        val result = state.captureResult

        activity.previewView.visibility = visibleOrInvisible(!result.visible)
        activity.previewGrid.gridType = state.preview.gridType

        renderBoundPreview(state.preview)
        renderCaptureResult(result)
        renderLastFrame(
            preview = state.preview,
            isQrMode = state.mode.isQr,
        )
    }

    private fun renderCaptureResult(result: CaptureResultUiModel) {
        val binding = activity.binding
        binding.flipCameraIconContent.visibility = visibleOrInvisible(!result.visible)
        binding.retakeIcon.visibility = visibleOrInvisible(result.visible)
        binding.confirmButton.visibility = visibleOrInvisible(result.visible)

        val isNewResult = result.visible &&
            (!renderedResultVisible || result.image !== renderedResultImage)
        renderedResultVisible = result.visible
        renderedResultImage = result.image

        if (!isNewResult) return

        // The captured image covers the preview until the camera streams again, so a retake does
        // not flash the empty preview the unbind leaves behind. It is taken down from there.
        activity.mainOverlay.setImageBitmap(result.image)
        activity.mainOverlay.visibility = View.VISIBLE
    }

    fun reapplyPreviewRatio() {
        val aspectRatio = renderedAspectRatio ?: return
        val sensorOrientationDegrees = renderedSensorOrientationDegrees ?: return

        activity.previewView.applyPreviewRatio(
            aspectRatio = aspectRatio,
            sensorOrientationDegrees = sensorOrientationDegrees,
        )
    }

    fun refreshLastFrame() {
        if (renderedLastFrameVisible == true) {
            showLastFrame()
        }
    }

    private fun renderLastFrame(
        preview: PreviewUiModel,
        isQrMode: Boolean,
    ) {
        // A camera that stops streaming under a frame already up puts the still up again, now over
        // the frame held for the bind.
        val isUnchanged = preview.lastFrameVisible == renderedLastFrameVisible &&
            preview.isStreaming == renderedStreaming
        renderedLastFrameVisible = preview.lastFrameVisible
        renderedStreaming = preview.isStreaming
        blursLastFrame = preview.blursLastFrame

        when {
            isUnchanged -> Unit
            preview.lastFrameVisible -> showLastFrame()
            else -> hideLastFrame(isQrMode)
        }
    }

    // The blurred still that stands in for the preview whenever the camera is not streaming.
    private fun showLastFrame() {
        activity.previewGrid.visibility = View.INVISIBLE

        val lastFrame = activity.previewFrames.lastFrame
        if (lastFrame == null || !blursLastFrame) return

        // The still is of the camera being left behind, and the box under it takes the new camera's
        // aspect ratio partway through the switch. Cropping keeps it covering that box; the layout's
        // fitStart, which is there for the captured photo CaptureActivity shows in this same view,
        // would leave a bar down the side of the preview until the new camera streams.
        activity.mainOverlay.scaleType = ImageView.ScaleType.CENTER_CROP
        setBlurBitmapCompat(activity.mainOverlay, lastFrame)
        activity.mainOverlay.visibility = View.VISIBLE
    }

    private fun hideLastFrame(isQrMode: Boolean) {
        activity.mainOverlay.visibility = View.INVISIBLE
        activity.previewGrid.visibility = visibleOrInvisible(!isQrMode)
    }

    private fun renderQr(qr: QrUiModel) {
        activity.qrOverlay.visibility = visibleOrInvisible(qr.overlayVisible)
        activity.qrScanToggles.visibility = visibleOrGone(qr.scanTogglesVisible)
    }

    private fun renderCancelButton(visible: Boolean) {
        activity.cancelButtonView.visibility = visibleOrInvisible(visible)
    }

    private fun renderCaptureButton(captureButton: CaptureButtonUiModel) {
        activity.captureButton.render(captureButton)
    }

    private fun renderFlipCamera(flipCamera: FlipCameraUiModel) {
        activity.flipCameraCircle.visibility = visibleOrInvisible(flipCamera.visible)
        activity.setFlipCameraIcon(
            icon = flipCamera.icon,
            description = flipCamera.description,
        )

        if (renderedLensFacing != null && flipCamera.lensFacing != renderedLensFacing) {
            spinFlipCamera()
        }
        renderedLensFacing = flipCamera.lensFacing
    }

    private fun spinFlipCamera() {
        val spin = RotateAnimation(
            0f,
            HALF_TURN_DEGREES,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
        )
        spin.duration = LENS_SWITCH_DURATION
        spin.interpolator = LinearInterpolator()

        activity.flipCameraCircle.startAnimation(spin)
    }

    private fun renderThirdOption(thirdOption: ThirdOptionUiModel) {
        activity.thirdOption.visibility = visibleOrInvisible(thirdOption.visible)
        activity.setThirdCircleIcon(
            icon = thirdOption.icon,
            description = thirdOption.description,
        )

        renderThumbnailLoader(thirdOption.thumbnailLoaderVisible)
        renderThumbnail(thirdOption.thumbnail)
    }

    private fun renderSelfTimer(selfTimer: SelfTimerUiModel) {
        val isRunning = selfTimer.remaining != null

        activity.cbText.text = selfTimer.badge
        activity.cbText.visibility = visibleOrInvisible(selfTimer.badgeVisible)
        activity.cdTimer.visibility = visibleOrGone(isRunning)
        activity.cdTimer.render(selfTimer.remaining)
        activity.cbCross.visibility = visibleOrInvisible(isRunning)
    }

    private fun renderRecording(recording: RecordingUiModel) {
        activity.micOffIcon.visibility = visibleOrGone(recording.micOffIconVisible)
        activity.muteToggle.visibility = visibleOrGone(recording.muteToggleVisible)
        activity.setMuteToggleState(muted = recording.isMuted)
        activity.timerView.visibility = visibleOrGone(recording.timerVisible)
        activity.timerView.text = recording.timerText
    }

    private fun renderFocusRing(focusRing: FocusRingUiModel?) {
        // A recreated screen is handed the last request again, and that ring has already faded.
        val newRing = focusRing?.takeIf { ring ->
            isFocusRingRendered && ring.id != renderedFocusRingId
        }
        renderedFocusRingId = focusRing?.id
        isFocusRingRendered = true

        newRing?.let(::showFocusRing)
    }

    private fun showFocusRing(focusRing: FocusRingUiModel) {
        val ring = activity.binding.focusRing
        val radius = ring.width / 2f

        ring.updateLayoutParams<ConstraintLayout.LayoutParams> {
            updateMargins(
                left = (focusRing.x - radius).roundToInt(),
                top = (focusRing.y - radius).roundToInt(),
            )
        }
        ring.visibility = View.VISIBLE
        ring.alpha = 1f

        when {
            areSystemAnimationsEnabled() -> {
                ring.animate()
                    .setStartDelay(FOCUS_RING_FADE_DELAY)
                    .setDuration(FOCUS_RING_FADE_DURATION)
                    .alpha(0f)
                    .withEndAction { ring.visibility = View.INVISIBLE }
                    .start()
            }

            else -> {
                focusRingTimeout?.cancel()
                focusRingTimeout = activity.lifecycleScope.launch {
                    delay(FOCUS_RING_STILL_DURATION)
                    ring.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun areSystemAnimationsEnabled(): Boolean {
        val resolver = activity.contentResolver
        val duration = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val transition = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )

        return duration != 0f && transition != 0f
    }

    private fun renderExposure(exposure: ExposureUiModel) {
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

    private fun renderSettingsPanelAvailability(available: Boolean) {
        activity.gestureHandler.isSettingsPanelAvailable = available
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

    private fun renderBoundPreview(preview: PreviewUiModel) {
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

    private fun renderRotation(rotation: RotationUiModel) {
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
        private const val FOCUS_RING_FADE_DELAY = 500L
        private const val FOCUS_RING_FADE_DURATION = 300L
        private const val LENS_SWITCH_DURATION = 400L
        private const val HALF_TURN_DEGREES = 180f
        private const val LEVEL_LINE_THICKNESS_DP = 4
        private const val TILTED_LINE_THICKNESS_DP = 2
        private const val HORIZON_RANGE_DEGREES = 45
        private const val HORIZON_OFFSET_DIVISOR = 60f
        private const val HORIZON_OFFSET_DP = 32

        private val FOCUS_RING_STILL_DURATION = 800.milliseconds
    }
}
