package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

inline internal fun <reified T : Parcelable> Intent.getParcelableExtraCompat(name: String): T? {
    val clazz = T::class
    return if (clazz == null || clazz.java == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        this.getParcelableExtra<T>(name)
    } else {
        this.getParcelableExtra(name, T::class.java)
    }
}

inline internal fun <reified T : Parcelable> Intent.getParcelableArrayListExtraCompat(name: String): ArrayList<T>? {
    val clazz = T::class
    return if (clazz == null || clazz.java == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        this.getParcelableArrayListExtra<T>(name)
    } else {
        this.getParcelableArrayListExtra(name, T::class.java)
    }
}
