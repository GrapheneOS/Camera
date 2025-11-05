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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.NumInputFilter
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.MoreSettingsBinding
import app.grapheneos.camera.util.PQEncryption
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import java.io.OutputStreamWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MoreSettings : AppCompatActivity(), TextView.OnEditorActionListener {
    private lateinit var camConfig: CamConfig

    private lateinit var binding: MoreSettingsBinding
    private lateinit var snackBar: Snackbar

    private lateinit var sLField: EditText

    private lateinit var rSLocation: Button

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
        enableEdgeToEdge()

        val camConfig = obtainCamConfig(intent)
        if (camConfig == null) {
            finish()
            return
        }
        this.camConfig = camConfig

        binding = MoreSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val showStorageSettings = this !is MoreSettingsSecure

        val sIAPToggle = binding.saveImageAsPreviewToggle

        sIAPToggle.isChecked = camConfig.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            camConfig.saveImageAsPreviewed =
                sIAPToggle.isChecked
        }

        val sVAPToggle = binding.saveVideoAsPreviewToggle

        sVAPToggle.isChecked = camConfig.saveVideoAsPreviewed

        sVAPToggle.setOnClickListener {
            camConfig.saveVideoAsPreviewed = sVAPToggle.isChecked
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

            val dialog = MaterialAlertDialogBuilder(this)

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

        pQField.setText(camConfig.photoQuality.toString())

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

        val sVAPSetting = binding.saveVideoAsPreviewSetting
        sVAPSetting.setOnClickListener {
            sVAPToggle.performClick()
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

        val highResSetting = binding.highestResSetting
        val highResToggle = binding.highestResSettingToggle
        highResToggle.isChecked = camConfig.selectHighestResolution

        highResToggle.setOnClickListener {
            camConfig.selectHighestResolution = !camConfig.selectHighestResolution
        }

        highResSetting.setOnClickListener {
            highResToggle.performClick()
        }

        // Post-Quantum Encryption settings
        val pqEncryptionToggle = binding.pqEncryptionToggle
        val pqGenerateKeysButton = binding.pqGenerateKeysButton
        val pqExportKeyButton = binding.pqExportKeyButton
        val pqKeyStatus = binding.pqKeyStatus
        val pqEncryptionSetting = binding.pqEncryptionToggleSetting

        // Update UI based on current state
        fun updatePQUI() {
            pqEncryptionToggle.isChecked = camConfig.pqEncryptionEnabled
            val hasKeys = camConfig.pqPublicKey.isNotEmpty()
            pqKeyStatus.text = if (hasKeys) {
                getString(R.string.pq_keys_generated)
            } else {
                getString(R.string.pq_keys_not_generated)
            }
            pqEncryptionToggle.isEnabled = hasKeys
        }

        updatePQUI()

        // Generate keys button
        pqGenerateKeysButton.setOnClickListener {
            showMessage(getString(R.string.pq_generating_keys))
            pqGenerateKeysButton.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val keyPair = PQEncryption.generateKeyPair()
                    withContext(Dispatchers.Main) {
                        camConfig.pqPublicKey = keyPair.publicKeyBase64()

                        // Store private key temporarily in memory for export
                        // We'll clear it after the user exports it or closes settings
                        getSharedPreferences("pq_temp", MODE_PRIVATE).edit()
                            .putString("temp_private_key", keyPair.privateKeyBase64())
                            .apply()

                        updatePQUI()
                        pqGenerateKeysButton.isEnabled = true
                        showMessage(getString(R.string.pq_keys_generated_success))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        pqGenerateKeysButton.isEnabled = true
                        showMessage("Key generation failed: ${e.message}")
                    }
                }
            }
        }

        // Export private key button
        pqExportKeyButton.setOnClickListener {
            val tempPrefs = getSharedPreferences("pq_temp", MODE_PRIVATE)
            val privateKeyB64 = tempPrefs.getString("temp_private_key", "")

            if (privateKeyB64.isNullOrEmpty()) {
                showMessage(getString(R.string.pq_no_keys_warning))
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pq_export_key_title)
                .setMessage(R.string.pq_export_key_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    try {
                        val filename = getString(R.string.pq_private_key_filename)
                        val cv = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                OutputStreamWriter(outputStream).use { writer ->
                                    writer.write("# GrapheneOS Camera - Post-Quantum Encryption Private Key\n")
                                    writer.write("# KEEP THIS FILE SECURE - Anyone with this key can decrypt your photos\n")
                                    writer.write("# Use the provided decryption tool to decrypt photos\n\n")
                                    writer.write(privateKeyB64)
                                }
                            }
                            showMessage(getString(R.string.pq_export_key_success))
                        } else {
                            showMessage(getString(R.string.pq_export_key_failed))
                        }
                    } catch (e: Exception) {
                        showMessage("${getString(R.string.pq_export_key_failed)}: ${e.message}")
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Encryption toggle
        pqEncryptionToggle.setOnClickListener {
            if (!pqEncryptionToggle.isChecked) {
                // Disabling encryption - just do it
                camConfig.pqEncryptionEnabled = false
                updatePQUI()
            } else {
                // Enabling encryption - show warning
                if (camConfig.pqPublicKey.isEmpty()) {
                    showMessage(getString(R.string.pq_no_keys_warning))
                    pqEncryptionToggle.isChecked = false
                    return@setOnClickListener
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.pq_encryption_warning_title)
                    .setMessage(R.string.pq_encryption_warning_message)
                    .setPositiveButton(R.string.enable) { _, _ ->
                        camConfig.pqEncryptionEnabled = true
                        updatePQUI()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        pqEncryptionToggle.isChecked = false
                    }
                    .show()
            }
        }

        pqEncryptionSetting.setOnClickListener {
            if (pqEncryptionToggle.isEnabled) {
                pqEncryptionToggle.performClick()
            } else {
                showMessage(getString(R.string.pq_no_keys_warning))
            }
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

    private fun dumpData() {

        // Dump state of photo quality
        if (pQField.text.isEmpty()) {
            // Revert back to the original value if invalid number was found
            pQField.setText(camConfig.photoQuality.toString())
            showMessage(getString(R.string.invalid_photo_quality_value))
        } else {
            try {
                camConfig.photoQuality = Integer.parseInt(pQField.text.toString())
            } catch (exception: Exception) {
                // Revert back to the original value if invalid number was found
                pQField.setText(camConfig.photoQuality.toString())
                showMessage(getString(R.string.invalid_photo_quality_value))
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
