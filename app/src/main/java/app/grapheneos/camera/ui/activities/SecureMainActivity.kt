package app.grapheneos.camera.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

class SecureMainActivity : MainActivity() {

    var openedActivityAt = DEFAULT_OPENED_AT_TIMESTAMP

    val capturedFilePaths = arrayListOf<String>()

    private lateinit var fileSP : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedActivityAt = System.currentTimeMillis()
        fileSP = getSharedPreferences(getSPName(), Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun getSPName() : String {
        return "files-$openedActivityAt"
    }

    override fun openGallery() {
        val intent = Intent(this, InAppGallery::class.java)

        val editor = fileSP.edit()
        editor.putStringSet("filePaths", capturedFilePaths.toSet())

        if(editor.commit()){
            intent.putExtra("folder_path", config.parentDirPath)
            intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
            intent.putExtra("fileSP", getSPName())
        } else {
            Toast.makeText(
                this,
                "An unexpected error occurred while opening the gallery",
                Toast.LENGTH_LONG
            ).show()
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