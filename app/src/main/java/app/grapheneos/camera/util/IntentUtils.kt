package app.grapheneos.camera.util

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat

inline fun <reified T : Parcelable> getParcelableExtra(intent: Intent, name: String): T? {
    return IntentCompat.getParcelableExtra(intent, name, T::class.java)
}
