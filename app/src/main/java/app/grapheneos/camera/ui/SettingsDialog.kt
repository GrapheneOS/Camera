package app.grapheneos.camera.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ToggleButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.QualitySelector
import app.grapheneos.camera.R
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity
import android.provider.Settings
import androidx.camera.video.Quality
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity


class SettingsDialog(mActivity: MainActivity) :
    Dialog(mActivity, R.style.Theme_App) {

    private var dialog: View
    var locToggle: ToggleButton
    private var flashToggle: ImageView
    private var aRToggle: ToggleButton
    var torchToggle: ToggleButton
    private var gridToggle: ImageView
    private var mActivity: MainActivity
    var videoQualitySpinner: Spinner
    private lateinit var vQAdapter: ArrayAdapter<String>
    private var focusTimeoutSpinner: Spinner
    private var timerSpinner: Spinner

    var mScrollView: ScrollView
    var mScrollViewContent: View

    var cmRadioGroup: RadioGroup
    var qRadio: RadioButton
    var lRadio: RadioButton

    var includeAudioToggle: SwitchCompat

    var selfIlluminationToggle: SwitchCompat
    var csSwitch: SwitchCompat

    private val timeOptions = mActivity.resources.getStringArray(R.array.time_options)

    private var includeAudioSetting: View
    private var selfIlluminationSetting: View
    private var videoQualitySetting: View
    private var timerSetting: View

    var settingsFrame: View

    private var moreSettingsButton: View

    private val bgBlue = mActivity.getColor(R.color.selected_option_bg)

    init {
        setContentView(R.layout.settings)

        dialog = findViewById(R.id.settings_dialog)
        dialog.setOnClickListener {}

        moreSettingsButton = findViewById(R.id.more_settings)
        moreSettingsButton.setOnClickListener {
            if (!mActivity.videoCapturer.isRecording) {
                openMoreSettings()
            } else {
                mActivity.showMessage("Cannot switch activities when recording")
            }
        }

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setDimAmount(0f)

        setOnDismissListener {
            mActivity.settingsIcon.visibility = View.VISIBLE
        }

        this.mActivity = mActivity

        val background: View = findViewById(R.id.background)
        background.setOnClickListener {
            slideDialogUp()
        }

        val rootView = findViewById<SettingsFrameLayout>(R.id.root)
        rootView.setOnInterceptTouchEventListener(
            object : SettingsFrameLayout.OnInterceptTouchEventListener {

                override fun onInterceptTouchEvent(
                    view: SettingsFrameLayout?,
                    ev: MotionEvent?,
                    disallowIntercept: Boolean
                ): Boolean {
                    return mActivity.gestureDetectorCompat.onTouchEvent(ev)
                }

                override fun onTouchEvent(
                    view: SettingsFrameLayout?,
                    event: MotionEvent?
                ): Boolean {
                    return false
                }
            }
        )

        settingsFrame = findViewById(R.id.settings_frame)

        rootView.viewTreeObserver.addOnPreDrawListener(
            object : OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    rootView.viewTreeObserver.removeOnPreDrawListener(this)

                    settingsFrame.layoutParams =
                        (settingsFrame.layoutParams as ViewGroup.MarginLayoutParams).let {
                            val marginTop =
                                (mActivity.rootView.layoutParams as ViewGroup.MarginLayoutParams).topMargin
                            it.height = (marginTop + (rootView.measuredWidth * 4 / 3))
                            it
                        }

                    return true
                }
            }
        )

        locToggle = findViewById(R.id.location_toggle)
        locToggle.setOnClickListener {

            if (camConfig.isVideoMode) {
                camConfig.requireLocation = false
                mActivity.showMessage(
                    "Geo-tagging currently is not supported for video mode"
                )
                return@setOnClickListener
            }

            if (mActivity.videoCapturer.isRecording) {
                locToggle.isChecked = !locToggle.isChecked
                mActivity.showMessage(
                    "Can't toggle geo-tagging for ongoing recording"
                )
            } else {
                camConfig.requireLocation = locToggle.isChecked
            }
        }

        flashToggle = findViewById(R.id.flash_toggle_option)
        flashToggle.setOnClickListener {
            if (mActivity.requiresVideoModeOnly) {
                mActivity.showMessage(
                    "Cannot switch flash mode in this mode"
                )
            } else {
                camConfig.toggleFlashMode()
            }
        }

        aRToggle = findViewById(R.id.aspect_ratio_toggle)
        aRToggle.setOnClickListener {
            if (camConfig.isVideoMode) {
                aRToggle.isChecked = true
                mActivity.showMessage(
                    "4:3 is not supported in video mode"
                )
            } else {
                camConfig.toggleAspectRatio()
            }
        }

        torchToggle = findViewById(R.id.torch_toggle_option)
        torchToggle.setOnClickListener {
            if (camConfig.isFlashAvailable) {
                camConfig.toggleTorchState()
            } else {
                torchToggle.isChecked = false
                mActivity.showMessage(
                    "Flash/Torch is unavailable for this mode"
                )
            }
        }

        gridToggle = findViewById(R.id.grid_toggle_option)
        gridToggle.setOnClickListener {
            camConfig.gridType = when (camConfig.gridType) {
                CamConfig.GridType.NONE -> CamConfig.GridType.THREE_BY_THREE
                CamConfig.GridType.THREE_BY_THREE -> CamConfig.GridType.FOUR_BY_FOUR
                CamConfig.GridType.FOUR_BY_FOUR -> CamConfig.GridType.GOLDEN_RATIO
                CamConfig.GridType.GOLDEN_RATIO -> CamConfig.GridType.NONE
            }
            updateGridToggleUI()
        }

        videoQualitySpinner = findViewById(R.id.video_quality_spinner)

        videoQualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val choice = vQAdapter.getItem(position) as String
                    updateVideoQuality(choice)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        qRadio = findViewById(R.id.quality_radio)
        lRadio = findViewById(R.id.latency_radio)

        if (mActivity.requiresVideoModeOnly) {
            qRadio.isEnabled = false
            lRadio.isEnabled = false
        }

        cmRadioGroup = findViewById(R.id.cm_radio_group)
        cmRadioGroup.setOnCheckedChangeListener { _, _ ->
            camConfig.emphasisQuality = qRadio.isChecked
            if (camConfig.cameraProvider != null) {
                camConfig.startCamera(true)
            }
        }

        selfIlluminationToggle = findViewById(R.id.self_illumination_switch)
        selfIlluminationToggle.setOnClickListener {
            camConfig.selfIlluminate = selfIlluminationToggle.isChecked
        }

        focusTimeoutSpinner = findViewById(R.id.focus_timeout_spinner)
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

        timerSpinner = findViewById(R.id.timer_spinner)
        timerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long
                ) {

                    val selectedOption = timerSpinner.selectedItem.toString()

                    if (selectedOption == "Off") {
                        mActivity.timerDuration = 0
                        mActivity.cbText.visibility = View.INVISIBLE
                    } else {

                        try {
                            val durS = selectedOption.substring(0, selectedOption.length - 1)
                            val dur = durS.toInt()

                            mActivity.timerDuration = dur

                            mActivity.cbText.text = selectedOption
                            mActivity.cbText.visibility = View.VISIBLE

                        } catch (exception: Exception) {

                            mActivity.showMessage(
                                "An unexpected error occurred while setting focus timeout"
                            )

                        }

                    }

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        mScrollView = findViewById(R.id.settings_scrollview)
        mScrollViewContent = findViewById(R.id.settings_scrollview_content)

        csSwitch = findViewById(R.id.camera_sounds_switch)
        csSwitch.setOnCheckedChangeListener { _, value ->
            camConfig.enableCameraSounds = value
        }

        includeAudioSetting = findViewById(R.id.include_audio_setting)
        selfIlluminationSetting = findViewById(R.id.self_illumination_setting)
        videoQualitySetting = findViewById(R.id.video_quality_setting)
        timerSetting = findViewById(R.id.timer_setting)

        includeAudioToggle = findViewById(R.id.include_audio_switch)
        includeAudioToggle.setOnClickListener {
            camConfig.includeAudio = includeAudioToggle.isChecked
        }
        includeAudioToggle.setOnCheckedChangeListener { _, _ ->
            camConfig.startCamera(true)
        }

        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    private fun resize() {
        mScrollViewContent.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                mScrollViewContent.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val sdHM =
                    mActivity.resources.getDimension(R.dimen.settings_dialog_horizontal_margin)

                val sH = (mScrollViewContent.width - (sdHM * 8)).toInt()

                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    sH.coerceAtMost(mScrollViewContent.height)
                )

                mScrollView.layoutParams = lp
            }
        })
    }

    fun showOnlyRelevantSettings() {
        if (camConfig.isVideoMode) {
            includeAudioSetting.visibility = View.VISIBLE
            videoQualitySetting.visibility = View.VISIBLE
        } else {
            includeAudioSetting.visibility = View.GONE
            videoQualitySetting.visibility = View.GONE
        }

        selfIlluminationSetting.visibility =
            if (camConfig.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                View.VISIBLE
            } else {
                View.GONE
            }

        timerSetting.visibility = if (camConfig.isVideoMode) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }


    fun updateFocusTimeout(selectedOption: String) {

        if (selectedOption == "Off") {
            camConfig.focusTimeout = 0
        } else {

            try {
                val durS = selectedOption.substring(0, selectedOption.length - 1)
                val dur = durS.toLong()

                camConfig.focusTimeout = dur

            } catch (exception: Exception) {

                mActivity.showMessage(
                    "An unexpected error occurred while setting focus timeout"
                )

            }
        }

        focusTimeoutSpinner.setSelection(timeOptions.indexOf(selectedOption), false)
    }

    fun updateVideoQuality(choice: String, resCam: Boolean = true) {

        val quality = titleToQuality(choice)

        if (quality == camConfig.videoQuality) return

        camConfig.videoQuality = quality

        if (resCam) {
            camConfig.startCamera(true)
        } else {
            videoQualitySpinner.setSelection(getAvailableQTitles().indexOf(choice))

        }
    }

    fun titleToQuality(title: String): Quality {
        return when (title) {
            "2160p (UHD)" -> Quality.UHD
            "1080p (FHD)" -> Quality.FHD
            "720p (HD)" -> Quality.HD
            "480p (SD)" -> Quality.SD
            else -> {
                Log.e("TAG", "Unknown quality: $title")
                Quality.SD
            }
        }
    }

    private var wasSelfIlluminationOn = false

    fun selfIllumination() {

        if (camConfig.selfIlluminate) {

            val colorFrom: Int = Color.BLACK
            val colorTo: Int = mActivity.getColor(R.color.self_illumination_light)

            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation1.duration = 300
            colorAnimation1.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                mActivity.previewView.setBackgroundColor(color)
                mActivity.rootView.setBackgroundColor(color)
                window?.statusBarColor = color
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.BLACK)
            colorAnimation2.duration = 300
            colorAnimation2.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    animator.animatedValue as Int,
                    Color.WHITE
                )
            }

            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), bgBlue, Color.BLACK)
            colorAnimation3.duration = 300
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()

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
                window?.statusBarColor = color
            }

            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, Color.WHITE)
            colorAnimation2.duration = 300
            colorAnimation2.addUpdateListener { animator ->
                mActivity.tabLayout.setTabTextColors(
                    animator.animatedValue as Int,
                    Color.WHITE
                )
            }

            val colorAnimation3 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, bgBlue)
            colorAnimation3.duration = 300
            colorAnimation3.addUpdateListener { animator ->
                mActivity.tabLayout.setSelectedTabIndicatorColor(animator.animatedValue as Int)
            }

            colorAnimation1.start()
            colorAnimation2.start()
            colorAnimation3.start()

            setBrightness(getSystemBrightness())
        }

        wasSelfIlluminationOn = camConfig.selfIlluminate
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
                    moreSettingsButton.visibility = View.GONE
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

    private fun getSystemBrightness(): Float {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            -1
        ) / 255f
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
        dialog.startAnimation(slideDownAnimation)
    }

    fun slideDialogUp() {
        dialog.startAnimation(slideUpAnimation)
    }

    private fun getAvailableQualities(): List<Quality> {
        return QualitySelector.getSupportedQualities(
            camConfig.camera!!.cameraInfo
        )
    }

    private fun getAvailableQTitles(): List<String> {

        val titles = arrayListOf<String>()

        getAvailableQualities().forEach {
            titles.add(getTitleFor(it))
        }

        return titles

    }

    private fun getTitleFor(quality: Quality): String {
        return when (quality) {
            Quality.UHD -> "2160p (UHD)"
            Quality.FHD -> "1080p (FHD)"
            Quality.HD -> "720p (HD)"
            Quality.SD -> "480p (SD)"
            else -> {
                Log.i("TAG", "Unknown constant: $quality")
                "Unknown"
            }
        }
    }

    fun updateGridToggleUI() {
        mActivity.previewGrid.postInvalidate()
        gridToggle.setImageResource(
            when (camConfig.gridType) {
                CamConfig.GridType.NONE -> R.drawable.grid_off_circle
                CamConfig.GridType.THREE_BY_THREE -> R.drawable.grid_3x3_circle
                CamConfig.GridType.FOUR_BY_FOUR -> R.drawable.grid_4x4_circle
                CamConfig.GridType.GOLDEN_RATIO -> R.drawable.grid_goldenratio_circle
            }
        )
    }

    fun updateFlashMode() {
        flashToggle.setImageResource(
            if (camConfig.isFlashAvailable) {
                when (camConfig.flashMode) {
                    ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on_circle
                    ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto_circle
                    else -> R.drawable.flash_off_circle
                }
            } else {
                R.drawable.flash_off_circle
            }
        )
    }

    override fun show() {

        this.resize()

        updateFlashMode()

        if (camConfig.isVideoMode) {
            aRToggle.isChecked = true
        } else {
            aRToggle.isChecked = camConfig.aspectRatio == AspectRatio.RATIO_16_9
        }

        torchToggle.isChecked = camConfig.isTorchOn

        updateGridToggleUI()

        mActivity.settingsIcon.visibility = View.INVISIBLE
        super.show()

        slideDialogDown()
    }

    fun reloadQualities(qualityText: String = "") {

        val titles = getAvailableQTitles()

        vQAdapter = ArrayAdapter<String>(
            mActivity,
            android.R.layout.simple_spinner_item,
            titles
        )

        vQAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        videoQualitySpinner.adapter = vQAdapter

        val qt = qualityText.ifEmpty {
            camConfig.videoQuality?.let { getTitleFor(it) }
        }

        videoQualitySpinner.setSelection(titles.indexOf(qt))
    }

    fun openMoreSettings() {
        val mSIntent = Intent(mActivity, MoreSettings::class.java)
        mSIntent.putExtra(
            "show_storage_settings",
            !(mActivity is SecureMainActivity ||
                    (mActivity is CaptureActivity &&
                            mActivity !is VideoCaptureActivity))
        )
        mActivity.startActivity(mSIntent)
    }
}