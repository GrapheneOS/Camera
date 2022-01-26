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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.grapheneos.camera.NumInputFilter
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import com.google.android.material.snackbar.Snackbar
import java.net.URLDecoder


class MoreSettings : AppCompatActivity(), TextView.OnEditorActionListener {

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
        if (it.data != null) {

            val uri = it.data?.data!!

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )

            val path = URLDecoder.decode(uri.toString(), "UTF-8")
            camConfig.storageLocation = uri.toString()

            val dPath = cleanPath(path)
            sLField.setText(dPath)

            showMessage("Storage location successfully updated to $dPath")

        } else {
            showMessage(
                "No directory was selected by" +
                        " the picker"
            )
        }
    }

    companion object {
        fun cleanPath(path: String): String {

            if (path.isEmpty()) {
                return "DCIM/Camera"
            }

            val s = URLDecoder.decode(path, "UTF-8")
            return s.substring(s.lastIndexOf(":") + 1)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.more_settings)
        setTitle(R.string.more_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val showStorageSettings = intent.extras?.getBoolean("show_storage_settings") == true

        val sIAPToggle = findViewById<SwitchCompat>(
            R.id.save_image_as_preview_toggle
        )

        sIAPToggle.isChecked = camConfig.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            camConfig.saveImageAsPreviewed =
                sIAPToggle.isChecked
        }

        rootView = findViewById(R.id.root_view)

        sLField = findViewById(
            R.id.storage_location_field
        )

        sLField.setText(cleanPath(camConfig.storageLocation))

        sLField.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            dirPickerHandler.launch(Intent.createChooser(i, "Choose storage location"))
        }

        snackBar = Snackbar.make(
            rootView,
            "Hello World",
            Snackbar.LENGTH_LONG
        )

        rSLocation = findViewById(R.id.refresh_storage_location)
        rSLocation.setOnClickListener {

            val dialog = AlertDialog.Builder(this)

            dialog.setTitle("Are you sure?")

            dialog.setMessage("Do you want to revert back to the default directory?")

            dialog.setPositiveButton("Yes") { _, _ ->
                val path = "DCIM/Camera"
                sLField.setText(path)

                if (camConfig.storageLocation.isNotEmpty()) {
                    showMessage(
                        "Switched back to the default storage location"
                    )

                    camConfig.storageLocation = ""
                } else {
                    showMessage(
                        "Already using the default storage location"
                    )
                }
            }

            dialog.setNegativeButton("No", null)
            dialog.show()
        }

        val sLS = findViewById<View>(R.id.storage_location_setting)
        sLS.visibility = if (showStorageSettings) {
            View.VISIBLE
        } else {
            View.GONE
        }

        pQField = findViewById(R.id.photo_quality)

        if (camConfig.photoQuality != 0) {
            pQField.setText(camConfig.photoQuality.toString())
        }

        pQField.filters = arrayOf(NumInputFilter(this))
        pQField.setOnEditorActionListener(this)

        iFField = findViewById(R.id.image_format_setting_field)
        iFField.setOnEditorActionListener(this)

        vFField = findViewById(R.id.video_format_setting_field)
        vFField.setOnEditorActionListener(this)

        val iPQButton = findViewById<View>(R.id.increase_photo_quality)
        iPQButton.setOnClickListener {

            if (camConfig.photoQuality != NumInputFilter.max) {
                ++camConfig.photoQuality
                pQField.setText(camConfig.photoQuality.toString())
            } else {
                showMessage(
                    "Photo quality can only be between ${NumInputFilter.min} and ${NumInputFilter.max}"
                )
            }
        }

        val dPQButton = findViewById<View>(R.id.decrease_photo_quality)
        dPQButton.setOnClickListener {
            if (camConfig.photoQuality >= NumInputFilter.min) {
                --camConfig.photoQuality

                if (camConfig.photoQuality >= NumInputFilter.min) {
                    pQField.setText(camConfig.photoQuality.toString())
                } else {
                    pQField.setText("")
                    showMessage(
                        "Photo quality was set to auto mode"
                    )
                }
            }
        }

        val exifToggle = findViewById<SwitchCompat>(R.id.remove_exif_toggle)
        val exifToggleSetting =
            findViewById<LinearLayout>(R.id.remove_exif_setting)

        exifToggleSetting.setOnClickListener {
            if (camConfig.isInCaptureMode) {
                showMessage(
                    "Images taken in this mode don't contain" +
                            "extra EXIF data"
                )
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

        val gSwitch = findViewById<SwitchCompat>(R.id.gyroscope_setting_switch)
        gSwitch.isChecked = camConfig.gSuggestions
        gSwitch.setOnClickListener {
            camConfig.gSuggestions = gSwitch.isChecked
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
                "Photo quality was set to auto mode"
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
}