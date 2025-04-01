package app.grapheneos.camera.ktx

import android.content.pm.ApplicationInfo

fun ApplicationInfo.isSystemApp() : Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0
}