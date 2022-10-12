package app.grapheneos.camera.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

inline fun <reified T : Parcelable> getParcelableExtra(intent: Intent, name: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(name) as T?
    }
}

inline fun <reified T : Parcelable> getParcelableArrayListExtra(intent: Intent, name: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra(name)
    }
}
