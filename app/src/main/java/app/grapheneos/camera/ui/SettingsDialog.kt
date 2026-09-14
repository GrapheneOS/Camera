package app.grapheneos.camera.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ToggleButton
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import app.grapheneos.camera.R
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.focusTimeoutLabel
import app.grapheneos.camera.databinding.SettingsBinding
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.SettingsSheetUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Collections
import kotlin.math.max

@SuppressLint("ClickableViewAccessibility")
class SettingsDialog(val mActivity: MainActivity, themedContext: Context) :
    Dialog(themedContext) {
    val viewfinder = mActivity.viewfinder

    private val session = mActivity.session

    private val binding: SettingsBinding by lazy { SettingsBinding.inflate(layoutInflater) }
    private var dialog: View
    var locToggle: ToggleButton
    private var flashToggle: ImageView
    private var aRToggle: ToggleButton
    var torchToggle: ToggleButton
    private var gridToggle: ImageView
    var videoQualitySpinner: Spinner
    internal var videoQualities: List<Quality> = emptyList()
        private set

    private var focusTimeoutSpinner: Spinner
    private var timerSpinner: Spinner

    var mScrollView: ScrollView
    var mScrollViewContent: View

    var includeAudioToggle: MaterialSwitch
    var enableEISToggle: MaterialSwitch

    var selfIlluminationToggle: MaterialSwitch

    var waitForFocusLockSwitch: MaterialSwitch

    private val timeOptions = mActivity.resources.getStringArray(R.array.time_options)

    private var includeAudioSetting: View
    private var enableEISSetting: View
    private var selfIlluminationSetting: View
    private var videoQualitySetting: View
    private var timerSetting: View

    var settingsFrame: View

    // Region the panel was last sized for, so the sizing runs when it moves and not on every
    // frame the panel is drawn in.
    private val sizedForRegion = Rect()

    private var moreSettingsButton: View

    private val tabSelectedColor =
        MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)

    private fun getString(@StringRes id: Int) = mActivity.getString(id)

    // The panel window is not focusable, so it never sees the back event itself and back would
    // otherwise fall through to the activity and close the app.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            slideDialogUp()
        }
    }

    init {
        setContentView(binding.root)

        mActivity.onBackPressedDispatcher.addCallback(mActivity, backCallback)

        dialog = binding.settingsDialog
        dialog.setOnClickListener {}

        moreSettingsButton = binding.moreSettings
        moreSettingsButton.setOnClickListener {
            if (!mActivity.videoCapturer.isRecording) {
                MoreSettings.start(mActivity)
            } else {
                mActivity.showMessage(getString(R.string.more_settings_unavailable_during_recording))
            }
        }

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setDimAmount(0f)

        setOnDismissListener {
            mActivity.settingsIcon.visibility = View.VISIBLE
        }

        val background: View = binding.background
        background.setOnClickListener {
            slideDialogUp()
        }

        val rootView = binding.root
        rootView.setOnInterceptTouchEventListener(
            object : SettingsFrameLayout.OnInterceptTouchEventListener {

                override fun onInterceptTouchEvent(
                    view: SettingsFrameLayout?,
                    ev: MotionEvent?,
                    disallowIntercept: Boolean
                ): Boolean {
                    return mActivity.gestureDetector.onTouchEvent(ev!!)
                }

                override fun onTouchEvent(
                    view: SettingsFrameLayout?,
                    event: MotionEvent?
                ): Boolean {
                    return false
                }
            }
        )

        settingsFrame = binding.settingsFrame

        binding.root.viewTreeObserver.addOnPreDrawListener { updatePanelRegion() }

        // The preview belongs to the activity's window, so resizing it schedules no traversal in
        // this one: without this the panel would keep the bounds of the preview it was opened over.
        mActivity.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updatePanelRegion()
        }

        locToggle = binding.locationToggle
        locToggle.setOnClickListener {
            if (mActivity.videoCapturer.isRecording) {
                locToggle.isChecked = !locToggle.isChecked
                mActivity.showMessage(
                    getString(R.string.toggle_geo_tagging_unsupported_while_recording)
                )
            } else {
                viewfinder.onAction(SettingsAction.GeoTaggingToggled(locToggle.isChecked))
            }
        }

        flashToggle = binding.flashToggleOption
        flashToggle.setOnClickListener {
            if (mActivity.requiresVideoModeOnly) {
                mActivity.showMessage(
                    getString(R.string.flash_switch_unsupported)
                )
            } else {
                viewfinder.onAction(CameraAction.FlashToggleClicked)
            }
        }

        aRToggle = binding.aspectRatioToggle
        aRToggle.setOnClickListener {
            if (sheetState.aspectRatioFixed) {
                aRToggle.isChecked = sheetState.is16by9
                mActivity.showMessage(
                    getString(R.string.four_by_three_unsupported_in_video)
                )
            } else {
                viewfinder.onAction(CameraAction.AspectRatioToggleClicked)
            }
        }

        torchToggle = binding.torchToggleOption
        torchToggle.setOnClickListener {
            if (session.isFlashAvailable) {
                session.toggleTorchState()
            } else {
                torchToggle.isChecked = false
                mActivity.showMessage(
                    getString(R.string.flash_unavailable_in_current_mode)
                )
            }
        }

        gridToggle = binding.gridToggleOption
        gridToggle.setOnClickListener {
            viewfinder.onAction(SettingsAction.GridToggleClicked)
        }

        videoQualitySpinner = binding.videoQualitySpinner

        videoQualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {
                    val quality = videoQualities.getOrNull(position) ?: return

                    updateVideoQuality(quality)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        if (mActivity.requiresVideoModeOnly) {
            binding.waitForFocusLockSetting.visibility = View.GONE
        }

        waitForFocusLockSwitch = binding.waitForFocusLockSwitch
        waitForFocusLockSwitch.setOnClickListener {
            viewfinder.onAction(
                SettingsAction.FocusLockToggled(waitForFocusLockSwitch.isChecked),
            )
        }

        selfIlluminationToggle = binding.selfIlluminationSwitch
        selfIlluminationToggle.setOnClickListener {
            viewfinder.onAction(
                SettingsAction.SelfIlluminationToggled(selfIlluminationToggle.isChecked),
            )
        }
        binding.selfIlluminationSwitchContainer.setOnTouchListener { _, event ->
            event.setLocation(0f, 0f)
            selfIlluminationToggle.dispatchTouchEvent(event)
            true
        }

        focusTimeoutSpinner = binding.focusTimeoutSpinner
        focusTimeoutSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val selectedOption = focusTimeoutSpinner.selectedItem.toString()
                    updateFocusTimeout(selectedOption)

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        focusTimeoutSpinner.setSelection(2)

        timerSpinner = binding.timerSpinner
        timerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val selectedOption = timerSpinner.selectedItem.toString()

                    if (selectedOption == timeOptions[0]) {
                        updateTimerDuration(0)
                    } else {
                        try {
                            val durS = selectedOption.substring(0, selectedOption.length - 1)
                            updateTimerDuration(durS.toInt())
                        } catch (exception: Exception) {
                            mActivity.showMessage(
                                getString(R.string.unexpected_error_while_setting_timer_duration)
                            )
                        }
                    }

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        restoreTimerDuration()

        mScrollView = binding.settingsScrollview
        mScrollViewContent = binding.settingsScrollviewContent

        includeAudioSetting = binding.includeAudioSetting
        enableEISSetting = binding.enableEisSetting
        selfIlluminationSetting = binding.selfIlluminationSetting
        videoQualitySetting = binding.videoQualitySetting
        timerSetting = binding.timerSetting

        includeAudioToggle = binding.includeAudioSwitch
        includeAudioToggle.setOnClickListener {
            viewfinder.onAction(SettingsAction.AudioToggled(includeAudioToggle.isChecked))
        }

        binding.includeAudioSwitchContainer.setOnTouchListener { _, event ->
            event.setLocation(0f, 0f)
            includeAudioToggle.dispatchTouchEvent(event)
            true
        }

        enableEISToggle = binding.enableEisSwitch
        enableEISToggle.setOnClickListener {
            viewfinder.onAction(SettingsAction.StabilizationToggled(enableEISToggle.isChecked))
        }
        binding.enableEisSwitchContainer.setOnTouchListener { _, event ->
            event.setLocation(0f, 0f)
            enableEISToggle.dispatchTouchEvent(event)
            true
        }

        window?.attributes?.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        var backgroundColor = ContextCompat.getColor(context, android.R.color.black)
        backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, 150)
        val settingsDialogBackgroundDrawable =
            ContextCompat.getDrawable(context, R.drawable.settings_bg)
        settingsDialogBackgroundDrawable?.setTint(backgroundColor)
        binding.settingsDialog.background = settingsDialogBackgroundDrawable

        val moreSettingsBackgroundDrawable =
            ContextCompat.getDrawable(context, R.drawable.settings_bg)
        moreSettingsBackgroundDrawable?.setTint(backgroundColor)
        binding.moreSettings.background = moreSettingsBackgroundDrawable
    }

    /**
     * The preview's rectangle, in the dialog window's coordinates. The panel is centred within
     * the preview, and reading the preview is what keeps that true at any window size and aspect
     * ratio — recomputing the geometry here would only be a second copy of it, free to disagree.
     */
    private fun previewRegion(): Rect? {
        val preview = mActivity.previewView
        if (preview.width == 0 || preview.height == 0) {
            return null
        }

        val previewLocation = IntArray(2)
        preview.getLocationOnScreen(previewLocation)

        val rootLocation = IntArray(2)
        binding.root.getLocationOnScreen(rootLocation)

        val left = previewLocation[0] - rootLocation[0]
        val top = previewLocation[1] - rootLocation[1]

        return Rect(left, top, left + preview.width, top + preview.height)
    }

    /**
     * Moves the panel onto the preview whenever the preview is not where it was last sized for.
     * The activity declares orientation as a config change it handles itself rather than being
     * recreated, so nothing else tells the panel it has been rotated — and it can be rotated, or
     * have its aspect ratio toggled, while it is open rather than between [show]s.
     */
    private fun updatePanelRegion(): Boolean {
        val region = previewRegion() ?: return true
        if (region == sizedForRegion) {
            return true
        }

        sizedForRegion.set(region)

        settingsFrame.layoutParams = (settingsFrame.layoutParams as FrameLayout.LayoutParams)
            .also {
                it.gravity = Gravity.TOP or Gravity.START
                it.leftMargin = region.left
                it.topMargin = region.top
                it.width = region.width()
                it.height = region.height()
            }

        // The list is capped against the region, so it has to be measured against the new one too.
        resize()

        // Drop the frame instead of drawing the panel where it does not belong: the new bounds
        // only take effect in the traversal the layout params above schedule.
        return false
    }

    private fun resize() {
        mScrollViewContent.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                mScrollViewContent.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val settingsDialogHorizontalMargin =
                    mActivity.resources.getDimensionPixelSize(R.dimen.settings_dialog_horizontal_margin)
                val moreSettingsButtonTopPadding =
                    (8 * mActivity.resources.displayMetrics.density).toInt()
                val totalDialogHeight = moreSettingsButton.height + moreSettingsButtonTopPadding +
                        dialog.height
                val availableWidth = dialog.width - (settingsDialogHorizontalMargin * 4)
                val regionHeight = previewRegion()?.height() ?: binding.root.measuredHeight
                val availableHeight = availableWidth.coerceAtMost(regionHeight) -
                        (totalDialogHeight - mScrollView.height)

                val height = if (mScrollViewContent.height < mScrollView.height) {
                    mScrollViewContent.height
                } else {
                    max(
                        mScrollView.height.coerceAtMost(availableHeight),
                        mScrollViewContent.height.coerceAtMost(availableHeight),
                    )
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height,
                )

                mScrollView.layoutParams = lp
            }
        })
    }

    private var sheetState = SettingsSheetUiState()

    private fun storedSheetState(): SettingsSheetUiState {
        return viewfinder.uiState.value.settingsSheet
    }

    fun render(state: SettingsSheetUiState) {
        sheetState = state

        flashToggle.setImageResource(state.flashIcon)
        flashToggle.contentDescription = mActivity.getString(state.flashDescription)

        includeAudioToggle.isChecked = state.includeAudio
        enableEISToggle.isChecked = state.stabilizationEnabled
        waitForFocusLockSwitch.isChecked = state.waitForFocusLock

        aRToggle.isChecked = state.is16by9
        ViewCompat.setStateDescription(aRToggle, mActivity.getString(state.aspectRatioDescription))

        gridToggle.setImageResource(state.gridIcon)
        gridToggle.contentDescription = mActivity.getString(state.gridDescription)

        locToggle.isChecked = state.geoTagging
        selfIlluminationToggle.isChecked = state.selfIllumination

        includeAudioSetting.visibility = visibleOrGone(state.includeAudioSettingVisible)
        videoQualitySetting.visibility = visibleOrGone(state.videoQualitySettingVisible)
        enableEISSetting.visibility = visibleOrGone(state.stabilizationSettingVisible)
        selfIlluminationSetting.visibility = visibleOrGone(state.selfIlluminationSettingVisible)
        timerSetting.visibility = visibleOrGone(state.timerSettingVisible)
    }

    private fun visibleOrGone(visible: Boolean): Int {
        return when {
            visible -> View.VISIBLE
            else -> View.GONE
        }
    }

    fun updateFocusTimeout(selectedOption: String) {

        if (selectedOption == timeOptions[0]) {
            viewfinder.onAction(SettingsAction.FocusTimeoutSelected(seconds = 0))
        } else {

            try {
                val durS = selectedOption.substring(0, selectedOption.length - 1)
                val dur = durS.toLong()

                viewfinder.onAction(SettingsAction.FocusTimeoutSelected(seconds = dur))

            } catch (exception: Exception) {

                mActivity.showMessage(
                    getString(R.string.unexpected_error_while_setting_focus_timeout)
                )

            }
        }

        focusTimeoutSpinner.setSelection(timeOptions.indexOf(selectedOption), false)
    }

    private fun updateTimerDuration(duration: Int) {
        mActivity.timerDuration = duration
        mActivity.updateSelfTimerBadge()
        // Common rather than per-mode: a mode's preferences are not slotted until the camera
        // starts, which happens after this dialog is built.
        viewfinder.onAction(SettingsAction.SelfTimerSelected(seconds = duration))
    }

    private fun restoreTimerDuration() {
        val duration = storedSheetState().selfTimerSeconds
        // Apply directly: Spinner.setSelection() only posts its selection callback, so the duration
        // would otherwise stay unset for a looper pass.
        updateTimerDuration(duration)

        val option = if (duration == 0) timeOptions[0] else "${duration}s"
        timerSpinner.setSelection(timeOptions.indexOf(option).coerceAtLeast(0), false)
    }

    fun updateVideoQuality(quality: Quality) {
        viewfinder.onAction(SettingsAction.VideoQualitySelected(quality))
    }

    private var wasSelfIlluminationOn = false

    fun selfIllumination(enabled: Boolean) {

        if (enabled) {

            val colorFrom: Int = Color.BLACK
            val colorTo: Int = mActivity.getColor(R.color.self_illumination_light)

            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation1.duration = 300
            colorAnimation1.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                mActivity.previewView.setBackgroundColor(color)
                mActivity.rootView.setBackgroundColor(color)
                mActivity.bottomOverlay.setBackgroundColor(color)
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.BLACK)
            colorAnimation2.duration = 300

            val selectedTextColor =
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), selectedTextColor, Color.WHITE)
            colorAnimation3.duration = 300

            var currentUnselectedColor = Color.WHITE
            colorAnimation2.addUpdateListener { animator ->
                currentUnselectedColor = animator.animatedValue as Int
            }
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    currentUnselectedColor,
                    animator.animatedValue as Int
                )
            }

            val colorAnimation4 =
                ValueAnimator.ofObject(ArgbEvaluator(), tabSelectedColor, Color.BLACK)
            colorAnimation4.duration = 300
            colorAnimation4.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()
            colorAnimation4.start()

            setBrightness(1f)

        } else if (wasSelfIlluminationOn) {

            val colorFrom: Int = mActivity.getColor(R.color.self_illumination_light)
            val colorTo: Int = Color.BLACK

            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation1.duration = 300
            colorAnimation1.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                mActivity.previewView.setBackgroundColor(color)
                mActivity.rootView.setBackgroundColor(color)
                mActivity.bottomOverlay.setBackgroundColor(color)
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, Color.WHITE)
            colorAnimation2.duration = 300

            val selectedTextColor =
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, selectedTextColor)
            colorAnimation3.duration = 300

            var currentUnselectedTextColor = Color.BLACK
            colorAnimation2.addUpdateListener { animator ->
                currentUnselectedTextColor = animator.animatedValue as Int
            }
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    currentUnselectedTextColor,
                    animator.animatedValue as Int
                )
            }

            val colorAnimation4 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, tabSelectedColor)
            colorAnimation4.duration = 300
            colorAnimation4.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()
            colorAnimation4.start()

            setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }

        wasSelfIlluminationOn = enabled
    }

    private val slideDownAnimation: Animation by lazy {
        val anim = AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_down
        )

        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {}

            override fun onAnimationEnd(p0: Animation?) {
                moreSettingsButton.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(p0: Animation?) {}

        })

        anim
    }

    val dismissHandler = Handler(Looper.myLooper()!!)
    val dismissCallback = Runnable {
        dismiss()
    }

    private val slideUpAnimation: Animation by lazy {
        val anim = AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_up
        )

        anim.setAnimationListener(
            object : Animation.AnimationListener {

                override fun onAnimationStart(p0: Animation?) {
                    moreSettingsButton.visibility = View.INVISIBLE
                }

                override fun onAnimationEnd(p0: Animation?) {
                    dismissHandler.removeCallbacks(dismissCallback)
                    dismissHandler.post(
                        dismissCallback
                    )
                }

                override fun onAnimationRepeat(p0: Animation?) {}

            }
        )

        anim
    }

    private fun setBrightness(brightness: Float) {

        val layout = mActivity.window.attributes
        layout.screenBrightness = brightness
        mActivity.window.attributes = layout

        window?.let {
            val dialogLayout = it.attributes
            dialogLayout.screenBrightness = brightness
            it.attributes = dialogLayout
        }

    }

    private fun slideDialogDown() {
        settingsFrame.startAnimation(slideDownAnimation)
    }

    fun slideDialogUp() {
        // Restarting the animation would bounce the panel back into view and postpone the dismissal
        // its end schedules, so ignore any further request to close while it plays out.
        if (slideUpAnimation.hasStarted() && !slideUpAnimation.hasEnded()) {
            return
        }
        settingsFrame.startAnimation(slideUpAnimation)
    }

    private fun getAvailableQualities(): List<Quality> {
        val cameraInfo = session.camera?.cameraInfo ?: return Collections.emptyList()
        return Recorder.getVideoCapabilities(cameraInfo).getSupportedQualities(DynamicRange.SDR)
    }

    fun loadInitialState() {
        updateFocusTimeout(focusTimeoutLabel(storedSheetState().focusTimeoutSeconds))
    }

    override fun show() {

        this.resize()

        torchToggle.isChecked = session.isTorchOn

        mActivity.settingsIcon.visibility = View.INVISIBLE
        super.show()
        backCallback.isEnabled = true

        slideDialogDown()
    }

    override fun dismiss() {
        backCallback.isEnabled = false
        super.dismiss()
    }

    fun reloadQualities() {
        videoQualities = getAvailableQualities()

        val adapter = ArrayAdapter(
            mActivity,
            android.R.layout.simple_spinner_item,
            videoQualities.map { videoQualityTitle(mActivity, it) },
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        videoQualitySpinner.adapter = adapter

        val storedQuality = storedSheetState().videoQuality

        if (storedQuality != Quality.HIGHEST) {
            videoQualitySpinner.setSelection(videoQualities.indexOf(storedQuality))
        }
    }
}
