package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.R
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MorePreferenceFragment(
    private val camConfig: CamConfig,
    private val isSecureScreen: Boolean
) : PreferenceFragmentCompat() {

    private val contentResolver by lazy {
        requireContext().contentResolver
    }
    private val storageLocation by lazy {
        findPreference<Preference>(CamConfig.SettingValues.Key.STORAGE_LOCATION)
    }
    private val snackBar: Snackbar by lazy {
        Snackbar.make(requireView(), "", Snackbar.LENGTH_LONG)
    }

    private val dirPickerHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        val uri = it.data?.data

        if (uri == null || uri.toString().contains(CapturedItems.SAF_TREE_SEPARATOR)) {
            showMessage(getString(R.string.no_directory_selected))
            return@registerForActivityResult
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)

        val uriString = uri.toString()
        camConfig.storageLocation = uriString

        val uiString = storageLocationToUiString(requireContext(), uriString)
        storageLocation?.setSummary(uiString)

        showMessage(getString(R.string.storage_location_updated, uiString))
    }

    fun showMessage(msg: String) {
        snackBar.setText(msg)
        snackBar.show()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = CamConfig.COMMON_SHARED_PREFS_NAME
        addPreferencesFromResource(R.xml.more_settings)

        val storageLocationKey = CamConfig.SettingValues.Key.STORAGE_LOCATION
        findPreference<Preference>(storageLocationKey)?.apply {
            setSummary(storageLocationToUiString(requireContext(), camConfig.storageLocation))
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showStorageDialog()
                return@OnPreferenceClickListener true
            }
        }

        findPreference<SwitchPreferenceCompat>(CamConfig.SettingValues.Key.REMOVE_EXIF_AFTER_CAPTURE)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                if (camConfig.isInCaptureMode) {
                    showMessage(getString(R.string.image_taken_in_this_mode_does_not_contain_extra_data))
                }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(CamConfig.SettingValues.Key.ENABLE_ZSL)?.apply {
            isVisible = camConfig.isZslSupported
        }

        findPreference<PreferenceCategory>("storage_category_key")?.apply {
            isVisible = !isSecureScreen
        }
        findPreference<SeekBarPreference>(CamConfig.SettingValues.Key.PHOTO_QUALITY)?.apply {
            setOnPreferenceChangeListener { _, value ->
                if (value == 0) {
                    showMessage(getString(R.string.photo_quality_was_set_to_auto))
                }
                return@setOnPreferenceChangeListener true
            }
        }
    }

    private fun Preference.showStorageDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_storage_location)
            .setMessage(R.string.custom_storage_title)
            .setNegativeButton(R.string.selection_default) { _, _ ->
                val defaultLocation = CamConfig.SettingValues.Default.STORAGE_LOCATION

                if (camConfig.storageLocation != defaultLocation) {
                    showMessage(getString(R.string.reverted_to_default_directory))
                    camConfig.storageLocation = defaultLocation
                    setSummary(storageLocationToUiString(requireContext(), defaultLocation))
                } else {
                    showMessage(getString(R.string.already_using_default_directory))
                }
            }
            .setPositiveButton(R.string.selection_custom) { _, _ ->
                dirPickerHandler.launch(
                    Intent.createChooser(
                        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                        getString(R.string.choose_storage_location)
                    )
                )
            }
            .show()
    }
}

open class MorePreferenceActivity : AppCompatActivity() {

    lateinit var camConfig: CamConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val camConfig = obtainCamConfig(intent)
        if (camConfig == null) {
            finish()
            return
        }
        this.camConfig = camConfig

        DynamicColors.applyToActivityIfAvailable(this)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = isLightMode()

        setContentView(R.layout.more_settings_container)
        supportFragmentManager.beginTransaction()
            .add(
                R.id.moreSettingsContainer,
                MorePreferenceFragment(camConfig, this is SecureMorePreferenceActivity)
            )
            .commit()
    }

    private fun isLightMode(): Boolean {
        val uiMode = resources.configuration.uiMode
        return uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            staticCamConfig = null
        }
    }

    companion object {
        private var camConfigId = 0L
        private var staticCamConfig: CamConfig? = null

        private const val INTENT_EXTRA_CAM_CONFIG_ID = "camConfig_id"

        fun start(caller: MainActivity) {
            val flavor = if (caller !is SecureActivity) {
                MorePreferenceActivity::class
            } else {
                SecureMorePreferenceActivity::class
            }
            Intent(caller, flavor.java).let {
                camConfigId += 1
                it.putExtra(INTENT_EXTRA_CAM_CONFIG_ID, camConfigId)
                staticCamConfig = caller.camConfig

                caller.startActivity(it)
            }
        }

        fun obtainCamConfig(intent: Intent): CamConfig? {
            val camConfig = staticCamConfig
            if (camConfigId != intent.getLongExtra(INTENT_EXTRA_CAM_CONFIG_ID, -1)) {
                return null
            }
            return camConfig
        }
    }
}

class SecureMorePreferenceActivity : MorePreferenceActivity()
