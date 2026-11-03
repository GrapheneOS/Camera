package app.grapheneos.camera.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction

class VideoCaptureActivity : CaptureActivity() {

    private lateinit var whiteOptionCircle: ImageView
    private lateinit var playPreview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        whiteOptionCircle = findViewById(R.id.white_option_circle)
        playPreview = findViewById(R.id.play_preview)

        playPreview.setOnClickListener {
            viewfinder.onAction(CaptureAction.PlayResultClicked)
        }

        imagePreview.visibility = View.GONE
        whiteOptionCircle.visibility = View.GONE
        playPreview.visibility = View.VISIBLE
    }
}
