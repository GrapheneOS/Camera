package app.grapheneos.camera.ui

import android.app.Dialog
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.video.QualitySelector
import app.grapheneos.camera.R
import app.grapheneos.camera.config.CamConfig
import app.grapheneos.camera.ui.activities.MainActivity

class SettingsDialog(mActivity: MainActivity) : Dialog(mActivity, R.style.Theme_App) {

    private var dialog : View
    private var locToggle: ToggleButton
    var flashToggle: ImageView
    var aRToggle: ToggleButton
    private var torchToggle: ToggleButton
    private var gridToggle: ImageView
    private var mActivity: MainActivity
    private var videoQualitySpinner : Spinner
    private lateinit var vQAdapter: ArrayAdapter<String>
    var cmRadio: RadioButton
    private var cmRadioGroup: RadioGroup

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

        flashToggle = findViewById(R.id.flash_toggle_option)
        flashToggle.setOnClickListener {
            mActivity.config.toggleFlashMode()
        }

        aRToggle = findViewById(R.id.aspect_ratio_toggle)
        aRToggle.setOnClickListener {
            if(mActivity.config.isVideoMode){
                aRToggle.isChecked = true
                Toast.makeText(mActivity,
                    "4:3 is not supported in video mode for now",
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
            mActivity.previewGrid.postInvalidate()
            updateGridToggleUI()
        }

        videoQualitySpinner = findViewById(R.id.video_quality_spinner)

        var avoidFirst = true

        videoQualitySpinner.onItemSelectedListener =
            object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {

                    // Avoid listening to the first default event
                    if(avoidFirst){
                        avoidFirst = false
                        return
                    }

                    val choice = vQAdapter.getItem(position)

                    val quality = when(choice){
                        "2160p (UHD)" -> QualitySelector.QUALITY_UHD
                        "1080p (FHD)" -> QualitySelector.QUALITY_FHD
                        "720p (HD)" -> QualitySelector.QUALITY_HD
                        "480p (SD)" -> QualitySelector.QUALITY_SD
                        else -> {
                            Log.i("TAG", "Unknown quality: $choice")
                            QualitySelector.QUALITY_SD
                        }
                    }

                    Log.i(choice, "quality: $quality")

                    mActivity.config.videoQuality =
                        QualitySelector.of(quality)

                    mActivity.config.startCamera(true)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        cmRadio = findViewById(R.id.quality_radio)

        cmRadioGroup = findViewById(R.id.cm_radio_group)
        cmRadioGroup.setOnCheckedChangeListener { _, _ ->
            mActivity.config.startCamera(true)
        }
    }

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

    fun getHighestQuality(): Int {
        return getAvailableQualities()[0]
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

    private fun updateGridToggleUI(){
        gridToggle.setImageResource(
            when(mActivity.config.gridType) {
                CamConfig.Grid.NONE -> R.drawable.grid_off_circle
                CamConfig.Grid.THREE_BY_THREE -> R.drawable.grid_3x3_circle
                CamConfig.Grid.FOUR_BY_FOUR -> R.drawable.grid_4x4_circle
                CamConfig.Grid.GOLDEN_RATIO -> R.drawable.grid_goldenratio_circle
            }
        )
    }

    override fun show() {
        flashToggle.setImageResource(
            when(mActivity.config.flashMode) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on_circle
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto_circle
                else -> R.drawable.flash_off_circle
            }
        )

        aRToggle.isChecked = mActivity.config.aspectRatio == AspectRatio.RATIO_16_9
        torchToggle.isChecked =
            mActivity.config.camera?.cameraInfo?.torchState?.value == TorchState.ON

        updateGridToggleUI()

        if(!::vQAdapter.isInitialized) {

            val titles = getAvailableQTitles()

            vQAdapter = ArrayAdapter<String>(
                mActivity,
                android.R.layout.simple_spinner_item,
                titles
            )

            vQAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item)

            videoQualitySpinner.adapter = vQAdapter

            videoQualitySpinner.setSelection(
                titles.indexOf(getTitleFor(
                    getHighestQuality()
                ))
            )
        }

        mActivity.settingsIcon.visibility = View.INVISIBLE
        super.show()

        slideDialogDown()
    }
}