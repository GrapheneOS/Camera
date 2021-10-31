package app.grapheneos.camera.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast

class SecureMainActivity : MainActivity() {

    var openedActivityAt = DEFAULT_OPENED_AT_TIMESTAMP

    val capturedFilePaths = arrayListOf<String>()

    private lateinit var fileSP : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        super.onCreate(savedInstanceState)
        openedActivityAt = System.currentTimeMillis()
        fileSP = getSharedPreferences(getSPName(), Context.MODE_PRIVATE)
    }

    private fun getSPName() : String {
        return "files-$openedActivityAt"
    }

    override fun openGallery() {
        val intent = Intent(this, InAppGallery::class.java)

        val editor = fileSP.edit()
        editor.putStringSet("filePaths", capturedFilePaths.toSet())

        if(editor.commit()){
            intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
            intent.putExtra("fileSP", getSPName())
        } else {
            showMessage(
                "An unexpected error occurred while opening the gallery",
            )
        }

        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        fileSP.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_OPENED_AT_TIMESTAMP = 0L
    }
}