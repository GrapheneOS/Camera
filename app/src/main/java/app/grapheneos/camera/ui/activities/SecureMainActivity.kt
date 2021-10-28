package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.os.Bundle

class SecureMainActivity : MainActivity() {

    var openedActivityAt = DEFAULT_OPENED_AT_TIMESTAMP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedActivityAt = System.currentTimeMillis()
    }

    override fun openGallery() {

//        val intent = Intent(this, InAppGallery::class.java)
//
//        intent.putExtra("folder_path", config.parentDirPath)
//        intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
//        intent.putExtra("activity_opened_at", openedActivityAt)
//
//        startActivity(intent)
    }

    companion object {
        const val DEFAULT_OPENED_AT_TIMESTAMP = 0L
    }
}