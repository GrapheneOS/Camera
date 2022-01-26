package app.grapheneos.camera.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle

class SecureMainActivity : MainActivity() {

    var openedActivityAt = DEFAULT_OPENED_AT_TIMESTAMP

    val capturedFilePaths = arrayListOf<String>()

    private lateinit var fileSP: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedActivityAt = System.currentTimeMillis()
        fileSP = getSharedPreferences(getSPName(), Context.MODE_PRIVATE)
    }

    private fun getSPName(): String {
        return "files-$openedActivityAt"
    }

    override fun openGallery() {

        if (capturedFilePaths.isEmpty()) {
            showMessage(
                "Please capture a photo/video before trying to view" +
                        " them."
            )
            return
        }

        val intent = Intent(this, InAppGallery::class.java)

        val editor = fileSP.edit()
        editor.putString(
            "filePaths", if (capturedFilePaths.isEmpty()) {
                ""
            } else {
                capturedFilePaths.joinToString(",")
            }
        )

        if (editor.commit()) {
            intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
            intent.putExtra("fileSP", getSPName())
            intent.putExtra("is_secure_mode", true)
            startActivity(intent)
        } else {
            showMessage(
                "An unexpected error occurred while opening the gallery",
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileSP.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_OPENED_AT_TIMESTAMP = 0L
    }
}