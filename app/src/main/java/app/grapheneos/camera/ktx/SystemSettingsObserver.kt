package app.grapheneos.camera.ktx

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

class SystemSettingsObserver(
    lifecycle: Lifecycle,
    private val key: String,
    private val context: Context,
    private val notifyForDescendants: Boolean = true,
    private val callback: () -> Unit
) : DefaultLifecycleObserver, ContentObserver(Handler(context.mainLooper)) {

    private val contentResolver by lazy { context.applicationContext.contentResolver }

    init {
        lifecycle.addObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        callback.invoke()
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(key), notifyForDescendants, this
        )
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        contentResolver.unregisterContentObserver(this)
    }

}