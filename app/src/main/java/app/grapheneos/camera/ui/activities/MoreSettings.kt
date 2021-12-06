package app.grapheneos.camera.ui.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.grapheneos.camera.R
import com.google.android.material.snackbar.Snackbar
import java.net.URLDecoder


class MoreSettings : AppCompatActivity() {

    private lateinit var snackBar: Snackbar

    private lateinit var sLField: EditText

    private lateinit var rSLocation: ImageView

    private lateinit var rootView: View

    private val dirPickerHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.data != null) {

            val uri = it.data?.data!!

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

            val path = URLDecoder.decode(uri.toString(), "UTF-8")
            MainActivity.camConfig.storageLocation = uri.toString()

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
        fun cleanPath(path: String) : String {

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

        val showStorageSettings = intent.extras?.
                        getBoolean("show_storage_settings") == true

        val sIAPToggle = findViewById<SwitchCompat>(
            R.id.save_image_as_preview_toggle
        )

        sIAPToggle.isChecked = MainActivity.camConfig.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            MainActivity.camConfig.saveImageAsPreviewed =
                sIAPToggle.isChecked
        }

        rootView = findViewById(R.id.root_view)

        sLField = findViewById<EditText>(
            R.id.storage_location_field
        )

        sLField.setText(cleanPath(MainActivity.camConfig.storageLocation))

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

                if (MainActivity.camConfig.storageLocation.isNotEmpty()) {
                    showMessage(
                        "Switched back to the default storage location"
                    )

                    MainActivity.camConfig.storageLocation = ""
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
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    fun showMessage(msg: String) {
        snackBar.setText(msg)
        snackBar.show()
    }
}