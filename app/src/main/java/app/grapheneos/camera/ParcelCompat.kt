package app.grapheneos.camera

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

inline internal fun <reified T : Parcelable> Parcel.readParcelableCompat(loader: ClassLoader?): T? {
    val clazz = T::class
    return if (clazz == null || clazz.java == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        this.readParcelable<T>(loader)
    } else {
        this.readParcelable(loader, T::class.java)
    }
}
