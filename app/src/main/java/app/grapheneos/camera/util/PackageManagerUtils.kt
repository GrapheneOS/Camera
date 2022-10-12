package app.grapheneos.camera.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

fun PackageManager.resolveActivity(intent: Intent, flags: Long): ResolveInfo? {
    return if (Build.VERSION.SDK_INT >= 33) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, flags.toInt())
    }
}
