package app.grapheneos.camera.ui.activities

import android.content.SharedPreferences
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.util.EphemeralSharedPrefsNamespace
import app.grapheneos.camera.util.getPrefs

class SecureMainActivity : MainActivity(), SecureActivity {
    val capturedItems = ArrayList<CapturedItem>()
    val ephemeralPrefsNamespace = EphemeralSharedPrefsNamespace()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return ephemeralPrefsNamespace.getPrefs(this, name, mode, cloneOriginal = true)
    }
}
