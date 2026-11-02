package app.grapheneos.camera.portrait

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

internal object PortraitProgressNotifier {
    private const val PERMISSION_REQUEST_CODE = 9103

    fun requestPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return
        val prefs = activity.getSharedPreferences("portrait_notifications", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_once", false)) return
        prefs.edit().putBoolean("asked_once", true).apply()
        try {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE)
        } catch (error: Exception) {
            Log.w("FoldPortraitAI", "Unable to request notification permission", error)
        }
    }
}
