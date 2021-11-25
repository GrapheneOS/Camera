package app.grapheneos.camera.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.R

class MoreSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.more_settings)
        setTitle(R.string.more_settings)
    }

}