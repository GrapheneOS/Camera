package app.grapheneos.camera.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.video.QualitySelector
import app.grapheneos.camera.R
import app.grapheneos.camera.config.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity

class SettingsDialog(mActivity: MainActivity) : Dialog(mActivity, R.style.Theme_App) {

    private var dialog : View
    var locToggle: ToggleButton
    var flashToggle: ImageView
    var aRToggle: ToggleButton
    var torchToggle: ToggleButton
    private var gridToggle: ImageView
    private var mActivity: MainActivity
    var videoQualitySpinner : Spinner
    private lateinit var vQAdapter: ArrayAdapter<String>
    private var focusTimeoutSpinner: Spinner
    private var timerSpinner: Spinner

    var mScrollView: ScrollView
    var mScrollViewContent: View

    var cmRadioGroup: RadioGroup
    var cmRadio: RadioButton

    var includeAudioToggle : SwitchCompat

    var selfIlluminationToggle : SwitchCompat
    var csSwitch: SwitchCompat

    private val timeOptions = mActivity.resources.getStringArray(R.array.time_options)

    private var includeAudioSetting : View
    private var selfIlluminationSetting : View
    private var videoQualitySetting : View

    init {
        setContentView(R.layout.settings)

        dialog = findViewById(R.id.settings_dialog)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setDimAmount(0f)

        setOnDismissListener {
            mActivity.settingsIcon.visibility = View.VISIBLE
        }

        this.mActivity = mActivity

        val background : View = findViewById(R.id.background)
        background.setOnClickListener {
            slideDialogUp()
        }

        locToggle = findViewById(R.id.location_toggle)
        locToggle.setOnClickListener {
            if (mActivity.videoCapturer.isRecording) {
                locToggle.isChecked = !locToggle.isChecked
                Toast.makeText(
                    mActivity,
                    "Can't toggle geo-tagging for ongoing recording",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                mActivity.config.requireLocation = locToggle.isChecked
            }
        }

        flashToggle = findViewById(R.id.flash_toggle_option)
        flashToggle.setOnClickListener {
            mActivity.config.toggleFlashMode()
        }

        aRToggle = findViewById(R.id.aspect_ratio_toggle)
        aRToggle.setOnClickListener {
            if(mActivity.config.isVideoMode){
                aRToggle.isChecked = mActivity.config.aspectRatio == AspectRatio.RATIO_16_9
                Toast.makeText(mActivity,
                    "4:3 isn't supported in video mode",
                    Toast.LENGTH_LONG).show()
            } else {
                mActivity.config.toggleAspectRatio()
            }
        }

        torchToggle = findViewById(R.id.torch_toggle_option)
        torchToggle.setOnClickListener {
            if(mActivity.config.isFlashAvailable) {
                mActivity.config.camera?.cameraControl?.enableTorch(
                    mActivity.config.camera?.cameraInfo?.torchState?.value ==
                            TorchState.OFF
                )
            } else {
                torchToggle.isChecked = false
                Toast.makeText(mActivity,
                    "Flash/Torch is unavailable for this mode", Toast.LENGTH_LONG)
                    .show()
            }
        }

        gridToggle = findViewById(R.id.grid_toggle_option)
        gridToggle.setOnClickListener {
            mActivity.config.gridType = when(mActivity.config.gridType){
                CamConfig.Grid.NONE -> CamConfig.Grid.THREE_BY_THREE
                CamConfig.Grid.THREE_BY_THREE -> CamConfig.Grid.FOUR_BY_FOUR
                CamConfig.Grid.FOUR_BY_FOUR -> CamConfig.Grid.GOLDEN_RATIO
                CamConfig.Grid.GOLDEN_RATIO -> CamConfig.Grid.NONE
            }
            updateGridToggleUI()
        }

        videoQualitySpinner = findViewById(R.id.video_quality_spinner)

        videoQualitySpinner.onItemSelectedListener =
            object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {

                    val choice = vQAdapter.getItem(position) as String
                    updateVideoQuality(choice)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        cmRadio = findViewById(R.id.quality_radio)

        cmRadioGroup = findViewById(R.id.cm_radio_group)
        cmRadioGroup.setOnCheckedChangeListener { _, _ ->
            mActivity.config.emphasisQuality = cmRadio.isChecked
            if (mActivity.config.cameraProvider != null) {
                mActivity.config.startCamera(true)
            }
        }

        selfIlluminationToggle = findViewById(R.id.self_illumination_switch)
        selfIlluminationToggle.setOnCheckedChangeListener { _, value ->
            mActivity.config.selfIlluminate = value
        }

        focusTimeoutSpinner = findViewById(R.id.focus_timeout_spinner)
        focusTimeoutSpinner.onItemSelectedListener =
            object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {

                    val selectedOption = focusTimeoutSpinner.selectedItem.toString()
                    updateFocusTimeout(selectedOption)

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        focusTimeoutSpinner.setSelection(2)

        timerSpinner = findViewById(R.id.timer_spinner)
        timerSpinner.onItemSelectedListener =
            object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {

                    val selectedOption = timerSpinner.selectedItem.toString()

                    if(selectedOption == "Off"){
                        mActivity.timerDuration = 0
                    } else {

                        try {
                            val durS = selectedOption.substring(0, selectedOption.length - 1)
                            val dur = durS.toInt()

                            mActivity.timerDuration = dur

                            mActivity.cbText.text = selectedOption
                            mActivity.cbText.visibility = View.VISIBLE

                        } catch (exception: Exception) {

                            Toast.makeText(
                                mActivity,
                                "An unexpected error occurred while setting focus timeout",
                                Toast.LENGTH_LONG
                            ).show()

                        }

                    }

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        mScrollView = findViewById(R.id.settings_scrollview)
        mScrollViewContent = findViewById(R.id.settings_scrollview_content)

        csSwitch = findViewById(R.id.camera_sounds_switch)
        csSwitch.setOnCheckedChangeListener { _, value ->
            mActivity.config.enableCameraSounds = value
        }

        includeAudioSetting = findViewById(R.id.include_audio_setting)
        selfIlluminationSetting = findViewById(R.id.self_illumination_setting)
        videoQualitySetting = findViewById(R.id.video_quality_setting)

        includeAudioToggle = findViewById(R.id.include_audio_switch)
        includeAudioToggle.setOnCheckedChangeListener { _, _ ->
            mActivity.config.startCamera(true)
        }
    }

    private fun resize() {
        mScrollViewContent.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {

                mScrollViewContent.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val sdHM = mActivity.resources.getDimension(R.dimen.settings_dialog_horizontal_margin)

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
        if (mActivity.config.isVideoMode) {
            includeAudioSetting.visibility = View.VISIBLE
            videoQualitySetting.visibility = View.VISIBLE
        } else {
            includeAudioSetting.visibility = View.GONE
            videoQualitySetting.visibility = View.GONE
        }

        selfIlluminationSetting.visibility =
            if(mActivity.config.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }


    fun updateFocusTimeout(selectedOption: String) {

        if(selectedOption == "Off"){
            mActivity.config.focusTimeout = 0
        } else {

            try {
                val durS = selectedOption.substring(0, selectedOption.length-1)
                val dur = durS.toLong()

                mActivity.config.focusTimeout = dur

            } catch (exception: Exception) {

                Toast.makeText(
                    mActivity,
                    "An unexpected error occurred while setting focus timeout",
                    Toast.LENGTH_LONG
                ).show()

            }
        }

        focusTimeoutSpinner.setSelection(timeOptions.indexOf(selectedOption), false)
    }

    fun updateVideoQuality(choice: String, resCam: Boolean = true){

        val quality = titleToQuality(choice)

        if (quality == mActivity.config.videoQuality) return

        mActivity.config.videoQuality = quality

        if(resCam) {
            mActivity.config.startCamera(true)
        } else {
            videoQualitySpinner.setSelection(getAvailableQTitles().indexOf(choice))

        }
    }

    fun titleToQuality(title: String): Int {
        return when(title) {
            "2160p (UHD)" -> QualitySelector.QUALITY_UHD
            "1080p (FHD)" -> QualitySelector.QUALITY_FHD
            "720p (HD)" -> QualitySelector.QUALITY_HD
            "480p (SD)" -> QualitySelector.QUALITY_SD
            else -> {
                Log.e("TAG", "Unknown quality: $title")
                QualitySelector.QUALITY_SD
            }
        }
    }

//    fun selfIllumination(value: Boolean){
//
//        if(value) {
//
//            val colorFrom: Int = Color.BLACK
//            val colorTo: Int = mActivity.getColor(R.color.self_illumination_light)
//
//            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
//            colorAnimation1.duration = 300
//            colorAnimation1.addUpdateListener {
//                    animator ->
//                mActivity.previewView.setBackgroundColor(animator.animatedValue as Int)
//                mActivity.rootView.setBackgroundColor(animator.animatedValue as Int)
//            }
//
//            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.BLACK)
//            colorAnimation2.duration = 300
//            colorAnimation2.addUpdateListener {
//                    animator ->
//                mActivity.tabLayout.setTabTextColors(
//                    animator.animatedValue as Int,
//                    Color.WHITE
//                )
//            }
//
//            colorAnimation1.start()
//            colorAnimation2.start()
//
//        } else {
//
//            val colorFrom: Int = mActivity.getColor(R.color.self_illumination_light)
//            val colorTo: Int = Color.BLACK
//
//            val colorAnimation1 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
//            colorAnimation1.duration = 300
//            colorAnimation1.addUpdateListener {
//                    animator ->
//                mActivity.previewView.setBackgroundColor(animator.animatedValue as Int)
//                mActivity.rootView.setBackgroundColor(animator.animatedValue as Int)
//            }
//
//            val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, Color.WHITE)
//            colorAnimation2.duration = 300
//            colorAnimation2.addUpdateListener {
//                    animator ->
//                mActivity.tabLayout.setTabTextColors(
//                    animator.animatedValue as Int,
//                    Color.WHITE
//                )
//            }
//
//            colorAnimation1.start()
//            colorAnimation2.start()
//        }
//    }

    private val slideDownAnimation : Animation by lazy {
        AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_down
        )
    }

    private val slideUpAnimation : Animation by lazy {
        val anim = AnimationUtils.loadAnimation(
            mActivity,
            R.anim.slide_up
        )

        anim.setAnimationListener(
            object: Animation.AnimationListener {

                override fun onAnimationStart(p0: Animation?) {}

                override fun onAnimationEnd(p0: Animation?) {
                    dismiss()
                }

                override fun onAnimationRepeat(p0: Animation?)
                {}

            }
        )

        anim
    }

    private fun slideDialogDown() {
        dialog.startAnimation(slideDownAnimation)
    }

    private fun slideDialogUp() {
        dialog.startAnimation(slideUpAnimation)
    }

    private fun getAvailableQualities(): List<Int> {
        return QualitySelector.getSupportedQualities(
            mActivity.config.camera!!.cameraInfo
        )
    }

    private fun getAvailableQTitles(): List<String> {

        val titles = arrayListOf<String>()

        getAvailableQualities().forEach {
            titles.add(getTitleFor(it))
        }

        return titles

    }

    private fun getTitleFor(quality: Int): String {
        return when(quality){
            QualitySelector.QUALITY_UHD -> "2160p (UHD)"
            QualitySelector.QUALITY_FHD -> "1080p (FHD)"
            QualitySelector.QUALITY_HD -> "720p (HD)"
            QualitySelector.QUALITY_SD -> "480p (SD)"
            else -> {
                Log.i("TAG", "Unknown constant: $quality")
                "Unknown"
            }
        }
    }

    fun updateGridToggleUI(){
        mActivity.previewGrid.postInvalidate()
        gridToggle.setImageResource(
            when(mActivity.config.gridType) {
                CamConfig.Grid.NONE -> R.drawable.grid_off_circle
                CamConfig.Grid.THREE_BY_THREE -> R.drawable.grid_3x3_circle
                CamConfig.Grid.FOUR_BY_FOUR -> R.drawable.grid_4x4_circle
                CamConfig.Grid.GOLDEN_RATIO -> R.drawable.grid_goldenratio_circle
            }
        )
    }

    fun updateFlashMode() {
        flashToggle.setImageResource(
            if(mActivity.config.isFlashAvailable) {
                when (mActivity.config.flashMode) {
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

        aRToggle.isChecked = mActivity.config.aspectRatio == AspectRatio.RATIO_16_9
        torchToggle.isChecked =
            mActivity.config.camera?.cameraInfo?.torchState?.value == TorchState.ON

        updateGridToggleUI()

        mActivity.settingsIcon.visibility = View.INVISIBLE
        super.show()

        slideDialogDown()
    }

    fun reloadQualities(qualityText : String = "") {

        val titles = getAvailableQTitles()

        vQAdapter = ArrayAdapter<String>(
            mActivity,
            android.R.layout.simple_spinner_item,
            titles
        )

        vQAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item)

        videoQualitySpinner.adapter = vQAdapter

        val qt = if(qualityText.isEmpty()) {
            getTitleFor(mActivity.config.videoQuality)
        } else {
            qualityText
        }

        videoQualitySpinner.setSelection(titles.indexOf(qt))
    }
}
