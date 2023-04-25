package app.grapheneos.camera.ktx

import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

fun WindowManager.getSizeCompat() : Rect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        currentWindowMetrics.bounds
    } else {
        val size = Point()
        @Suppress("DEPRECATION")
        defaultDisplay.getRealSize(size)
        Rect(0, 0, size.x, size.y)
    }
}