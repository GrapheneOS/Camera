package app.grapheneos.camera.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.NumInputFilter
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.MoreSettingsBinding
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.snackbar.Snackbar

open class MoreSettings : AppCompatActivity(), TextView.OnEditorActionListener {
    private lateinit var camConfig: CamConfig

    private lateinit var binding: MoreSettingsBinding
    private lateinit var snackBar: Snackbar

    private lateinit var sLField: EditText

    private lateinit var rSLocation: ImageView

    private lateinit var rootView: View

    private lateinit var pQField: EditText
    private lateinit var iFField: EditText
    private lateinit var vFField: EditText

    private val dirPickerHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val intent = it.data
        val uri = intent?.data?.let {
            if (it.toString().contains(CapturedItems.SAF_TREE_SEPARATOR)) {
                null
            } else {
                it
            }
        }
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val uriString = uri.toString()
            camConfig.storageLocation = uriString

            val uiString = storageLocationToUiString(this, uriString)
            sLField.setText(uiString)

            showMessage(getString(R.string.storage_location_updated, uiString))

        } else {
            showMessage(getString(R.string.no_directory_selected))
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val camConfig = obtainCamConfig(intent)
        if (camConfig == null) {
            finish()
            return
        }
        this.camConfig = camConfig

        binding = MoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.more_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        window.navigationBarColor = Color.TRANSPARENT

        val showStorageSettings = this !is MoreSettingsSecure

        val sIAPToggle = binding.saveImageAsPreviewToggle

        sIAPToggle.isChecked = camConfig.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            camConfig.saveImageAsPreviewed =
                sIAPToggle.isChecked
        }

        rootView = binding.rootView

        sLField = binding.storageLocationField

        sLField.setText(storageLocationToUiString(this, camConfig.storageLocation))

        sLField.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            dirPickerHandler.launch(Intent.createChooser(i, getString(R.string.choose_storage_location)))
        }

        snackBar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)

        rSLocation = binding.refreshStorageLocation
        rSLocation.setOnClickListener {

            val dialog = AlertDialog.Builder(this)

            dialog.setTitle(R.string.are_you_sure)

            dialog.setMessage(R.string.revert_to_default_directory)

            dialog.setPositiveButton(R.string.yes) { _, _ ->
                val defaultLocation = CamConfig.SettingValues.Default.STORAGE_LOCATION

                if (camConfig.storageLocation != defaultLocation) {
                    showMessage(getString(R.string.reverted_to_default_directory))
                    camConfig.storageLocation = defaultLocation
                    sLField.setText(storageLocationToUiString(this, defaultLocation))
                } else {
                    showMessage(getString(R.string.already_using_default_directory))
                }
            }

            dialog.setNegativeButton(R.string.no, null)
            dialog.show()
        }

        pQField = binding.photoQuality

        if (camConfig.photoQuality != 0) {
            pQField.setText(camConfig.photoQuality.toString())
        }

        pQField.filters = arrayOf(NumInputFilter(this))
        pQField.setOnEditorActionListener(this)

        iFField = binding.imageFormatSettingField
        iFField.setOnEditorActionListener(this)

        vFField = binding.videoFormatSettingField
        vFField.setOnEditorActionListener(this)

        val exifToggle = binding.removeExifToggle
        val exifToggleSetting = binding.removeExifSetting

        exifToggleSetting.setOnClickListener {
            if (camConfig.isInCaptureMode) {
                showMessage(
                    getString(R.string.image_taken_in_this_mode_does_not_contain_extra_data)
                )
            } else {
                exifToggle.performClick()
            }
        }

        // Lock toggle in checked state in capture mode
        if (camConfig.isInCaptureMode) {
            exifToggle.isChecked = true
            exifToggle.isEnabled = false
        } else {
            exifToggle.isChecked = camConfig.removeExifAfterCapture
        }

        exifToggle.setOnClickListener {
            camConfig.removeExifAfterCapture = exifToggle.isChecked
        }

        val gSwitch = binding.gyroscopeSettingSwitch
        gSwitch.isChecked = camConfig.gSuggestions
        gSwitch.setOnClickListener {
            camConfig.gSuggestions = gSwitch.isChecked
        }

        val gSetting = binding.gyroscopeSetting
        gSetting.setOnClickListener {
            gSwitch.performClick()
        }

        val csSwitch = binding.cameraSoundsSwitch
        csSwitch.isChecked = camConfig.enableCameraSounds
        csSwitch.setOnClickListener {
            camConfig.enableCameraSounds = csSwitch.isChecked
        }

        val csSetting = binding.cameraSoundsSetting
        csSetting.setOnClickListener {
            csSwitch.performClick()
        }

        val sIAPSetting = binding.saveImageAsPreviewSetting
        sIAPSetting.setOnClickListener {
            sIAPToggle.performClick()
        }

        val sLS = binding.storageLocationSetting
        sLS.setOnClickListener {
            sLField.performClick()
        }

        val zslSetting = binding.zslSetting
        if (camConfig.isZslSupported) {
            zslSetting.visibility = View.VISIBLE

            val zslToggle = binding.zslSettingToggle
            zslToggle.isChecked = camConfig.enableZsl
            zslToggle.setOnClickListener {
                camConfig.enableZsl = !camConfig.enableZsl
            }

            zslSetting.setOnClickListener {
                zslToggle.performClick()
            }
        }

        if (!showStorageSettings) {
            binding.storageLocationSettings.visibility = View.GONE
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if (event.action == MotionEvent.ACTION_UP) {
            val v: View? = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    clearFocus()
                    dumpData()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun clearFocus() {
        val view = currentFocus
        if (view != null) {
            view.clearFocus()
            val imm: InputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun dumpData() {

        // Dump state of photo quality
        if (pQField.text.isEmpty()) {
            camConfig.photoQuality = 0

            showMessage(
                getString(R.string.photo_quality_was_set_to_auto)
            )
        } else {
            try {

                camConfig.photoQuality =
                    Integer.parseInt(pQField.text.toString())

            } catch (exception: Exception) {

                camConfig.photoQuality = 0

            }
        }

//        // Dump state of image format
//        camConfig.imageFormat = iFField.text.toString()
//
//        // Dump state of video format
//        camConfig.videoFormat = vFField.text.toString()
    }

    override fun onEditorAction(p0: TextView?, id: Int, p2: KeyEvent?): Boolean {
        return if (id == EditorInfo.IME_ACTION_DONE) {
            clearFocus()
            dumpData()
            true
        } else false
    }

    fun showMessage(msg: String) {
        snackBar.setText(msg)
        snackBar.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private var camConfigId = 0L
        private var staticCamConfig: CamConfig? = null

        private const val INTENT_EXTRA_CAM_CONFIG_ID = "camConfig_id"

        fun start(caller: MainActivity) {
            val flavor = if (caller is SecureActivity) MoreSettingsSecure::class else MoreSettings::class
            Intent(caller, flavor.java).let {
                camConfigId += 1
                it.putExtra(INTENT_EXTRA_CAM_CONFIG_ID, camConfigId)
                staticCamConfig = caller.camConfig

                caller.startActivity(it)
            }
        }

        private fun obtainCamConfig(intent: Intent): CamConfig? {
            val camConfig = staticCamConfig
            if (camConfigId != intent.getLongExtra(INTENT_EXTRA_CAM_CONFIG_ID, -1)) {
                return null
            }
            return camConfig
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            staticCamConfig = null
        }
    }
}
