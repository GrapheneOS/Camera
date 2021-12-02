package app.grapheneos.camera.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.grapheneos.camera.R

class MoreSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.more_settings)
        setTitle(R.string.more_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val sIAPToggle = findViewById<SwitchCompat>(
            R.id.save_image_as_preview_toggle
        )

        sIAPToggle.isChecked = MainActivity.camConfig.saveImageAsPreviewed

        sIAPToggle.setOnClickListener {
            MainActivity.camConfig.saveImageAsPreviewed = sIAPToggle.isChecked
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}