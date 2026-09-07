package app.grapheneos.camera.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.NumInputFilter
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.databinding.MoreSettingsBinding
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
open class MoreSettings :
    AppCompatActivity(),
    TextView.OnEditorActionListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var capturedItemRepository: CapturedItemRepository

    private var isInCaptureMode = false

    private var isZslSupported = false

    private var settings: CameraSettings = CameraSettings()

    private var storageLocation: String = CapturedItemRepository.MEDIA_STORE_LOCATION

    private lateinit var binding: MoreSettingsBinding

    private lateinit var snackBar: Snackbar

    private lateinit var sLField: EditText

    private lateinit var rSLocation: Button

    private lateinit var rootView: View

    private lateinit var pQField: EditText

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
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val uriString = uri.toString()
            setStorageLocation(uriString)

            val uiString = storageLocationToUiString(this, uriString)
            sLField.setText(uiString)

            showMessage(getString(R.string.storage_location_updated, uiString))
        } else {
            showMessage(getString(R.string.no_directory_selected))
        }
    }

    private fun updateSettings(transform: (CameraSettings) -> CameraSettings) {
        settings = runBlocking { settingsRepository.update(transform) }
    }

    private fun setStorageLocation(location: String) {
        runBlocking {
            capturedItemRepository.setStorageLocation(location)
            capturedItemRepository.releaseUntrackedSafTrees()
        }

        storageLocation = location
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isInCaptureMode = intent.getBooleanExtra(INTENT_EXTRA_IN_CAPTURE_MODE, false)
        isZslSupported = intent.getBooleanExtra(INTENT_EXTRA_ZSL_SUPPORTED, false)
        settings = runBlocking { settingsRepository.settings.first() }
        storageLocation = runBlocking { capturedItemRepository.storageLocation.first() }

        binding = MoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val showStorageSettings = this !is MoreSettingsSecure

        val sIAPToggle = binding.saveImageAsPreviewToggle

        sIAPToggle.isChecked = settings.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            updateSettings { it.copy(saveImageAsPreviewed = sIAPToggle.isChecked) }
        }

        val sVAPToggle = binding.saveVideoAsPreviewToggle

        sVAPToggle.isChecked = settings.saveVideoAsPreviewed

        sVAPToggle.setOnClickListener {
            updateSettings { it.copy(saveVideoAsPreviewed = sVAPToggle.isChecked) }
        }

        rootView = binding.rootView

        sLField = binding.storageLocationField

        sLField.setText(storageLocationToUiString(this, storageLocation))

        sLField.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            dirPickerHandler.launch(
                Intent.createChooser(i, getString(R.string.choose_storage_location))
            )
        }

        snackBar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)

        rSLocation = binding.refreshStorageLocation
        rSLocation.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(this)

            dialog.setTitle(R.string.are_you_sure)

            dialog.setMessage(R.string.revert_to_default_directory)

            dialog.setPositiveButton(R.string.yes) { _, _ ->
                val defaultLocation = CapturedItemRepository.MEDIA_STORE_LOCATION

                if (storageLocation != defaultLocation) {
                    showMessage(getString(R.string.reverted_to_default_directory))
                    setStorageLocation(defaultLocation)
                    sLField.setText(storageLocationToUiString(this, defaultLocation))
                } else {
                    showMessage(getString(R.string.already_using_default_directory))
                }
            }

            dialog.setNegativeButton(R.string.no, null)
            dialog.show()
        }

        pQField = binding.photoQuality

        pQField.setText(settings.photoQuality.toString())

        pQField.filters = arrayOf(NumInputFilter(this))
        pQField.setOnEditorActionListener(this)

        val exifToggle = binding.removeExifToggle
        val exifToggleSetting = binding.removeExifSetting

        exifToggleSetting.setOnClickListener {
            if (isInCaptureMode) {
                showMessage(
                    getString(R.string.image_taken_in_this_mode_does_not_contain_extra_data)
                )
            } else {
                exifToggle.performClick()
            }
        }

        // Lock toggle in checked state in capture mode
        if (isInCaptureMode) {
            exifToggle.isChecked = true
            exifToggle.isEnabled = false
        } else {
            exifToggle.isChecked = settings.removeExifAfterCapture
        }

        exifToggle.setOnClickListener {
            updateSettings { it.copy(removeExifAfterCapture = exifToggle.isChecked) }
        }

        val gSwitch = binding.gyroscopeSettingSwitch
        gSwitch.isChecked = settings.gyroscopeSuggestions
        gSwitch.setOnClickListener {
            updateSettings { it.copy(gyroscopeSuggestions = gSwitch.isChecked) }
        }

        val gSetting = binding.gyroscopeSetting
        gSetting.setOnClickListener {
            gSwitch.performClick()
        }

        val csSwitch = binding.cameraSoundsSwitch
        csSwitch.isChecked = settings.enableCameraSounds
        csSwitch.setOnClickListener {
            updateSettings { it.copy(enableCameraSounds = csSwitch.isChecked) }
        }

        val csSetting = binding.cameraSoundsSetting
        csSetting.setOnClickListener {
            csSwitch.performClick()
        }

        val sIAPSetting = binding.saveImageAsPreviewSetting
        sIAPSetting.setOnClickListener {
            sIAPToggle.performClick()
        }

        val sVAPSetting = binding.saveVideoAsPreviewSetting
        sVAPSetting.setOnClickListener {
            sVAPToggle.performClick()
        }

        val sLS = binding.storageLocationSetting
        sLS.setOnClickListener {
            sLField.performClick()
        }

        // Every other row here acts on the control it holds. This one held a 36dp field and did
        // nothing, while still announcing itself as activatable.
        val pQSetting = binding.photoQualitySetting
        pQSetting.setOnClickListener {
            pQField.requestFocus()
            pQField.setSelection(pQField.text.length)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(pQField, 0)
        }

        val zslSetting = binding.zslSetting
        if (isZslSupported) {
            zslSetting.visibility = View.VISIBLE

            val zslToggle = binding.zslSettingToggle
            zslToggle.isChecked = settings.enableZsl
            zslToggle.setOnClickListener {
                updateSettings { it.copy(enableZsl = !settings.enableZsl) }
            }

            zslSetting.setOnClickListener {
                zslToggle.performClick()
            }
        }

        val highResSetting = binding.highestResSetting
        val highResToggle = binding.highestResSettingToggle
        highResToggle.isChecked = settings.selectHighestResolution

        highResToggle.setOnClickListener {
            updateSettings { it.copy(selectHighestResolution = !settings.selectHighestResolution) }
        }

        highResSetting.setOnClickListener {
            highResToggle.performClick()
        }

        if (!showStorageSettings) {
            binding.storageLocationSettings.visibility = View.GONE
        }

        binding.appBar.setNavigationOnClickListener {
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutouts = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(cutouts.left, 0, cutouts.right, systemBars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { v, insets ->
            val cutouts = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(cutouts.left, 0, cutouts.right, 0)
            insets
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

    override fun onPause() {
        // dispatchTouchEvent and onEditorAction only fire when the user taps outside the field
        // or presses the IME action key. Leaving the screen any other way (Back, gesture back,
        // the up arrow, Home, task switch) used to drop whatever had been typed, silently.
        // Commit here so that every exit path persists; a snackbar would be pointless on a
        // screen that is going away, so the invalid-value complaint is suppressed.
        // onCreate() can bail out before the views exist (no CamConfig in the intent) and the
        // lifecycle still runs through onPause, hence the initialization check.
        if (this::pQField.isInitialized) {
            dumpData(notifyOnInvalidValue = false)
        }
        super.onPause()
    }

    private fun dumpData(notifyOnInvalidValue: Boolean = true) {
        // Dump state of photo quality
        val quality = pQField.text.toString().toIntOrNull()
        // NumInputFilter keeps out-of-range values from being typed, but it cannot stop them being
        // deleted into place: the empty replacement it returns to reject an edit is the very edit a
        // deletion asks for, so deleting the leading digit of "10" leaves "0" behind. Committing
        // that made ImageCapture reject the quality and crash the next bind.
        if (quality == null || quality !in NumInputFilter.min..NumInputFilter.max) {
            // Revert back to the original value if invalid number was found
            pQField.setText(settings.photoQuality.toString())
            if (notifyOnInvalidValue) {
                showMessage(getString(R.string.invalid_photo_quality_value))
            }
        } else {
            updateSettings { it.copy(photoQuality = quality) }
        }
    }

    override fun onEditorAction(p0: TextView?, id: Int, p2: KeyEvent?): Boolean {
        return if (id == EditorInfo.IME_ACTION_DONE) {
            clearFocus()
            dumpData()
            true
        } else {
            false
        }
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

        private const val INTENT_EXTRA_IN_CAPTURE_MODE = "in_capture_mode"
        private const val INTENT_EXTRA_ZSL_SUPPORTED = "zsl_supported"

        fun start(caller: MainActivity) {
            val flavor = if (caller is SecureActivity) MoreSettingsSecure::class else MoreSettings::class
            Intent(caller, flavor.java).let {
                it.putExtra(INTENT_EXTRA_IN_CAPTURE_MODE, caller.camConfig.isInCaptureMode)
                it.putExtra(INTENT_EXTRA_ZSL_SUPPORTED, caller.camConfig.session.isZslSupported)

                caller.startActivity(it)
            }
        }
    }
}
