package app.grapheneos.camera.ui.activities

import android.content.SharedPreferences
import android.os.Bundle
import app.grapheneos.camera.AutoFinishOnSleep
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.util.EphemeralSharedPrefsNamespace
import app.grapheneos.camera.util.getPrefs

open class SecureMainActivity : MainActivity(), SecureActivity {
    val capturedItems = ArrayList<CapturedItem>()
    val ephemeralPrefsNamespace = EphemeralSharedPrefsNamespace()

    private val autoFinisher = AutoFinishOnSleep(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoFinisher.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoFinisher.stop()
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return ephemeralPrefsNamespace.getPrefs(this, name, mode, cloneOriginal = true)
    }
}
