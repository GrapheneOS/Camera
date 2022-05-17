package app.grapheneos.camera.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.ArrayMap
import java.util.WeakHashMap

import android.content.SharedPreferences.OnSharedPreferenceChangeListener as ChangeListener

typealias EphemeralSharedPrefsNamespace = ArrayMap<String, EphemeralSharedPrefs>

fun EphemeralSharedPrefsNamespace.getPrefs(ctx: Context, name: String, mode: Int, cloneOriginal: Boolean): SharedPreferences {
    require(mode == Context.MODE_PRIVATE)
    synchronized(this) {
        return getOrElse(name) {
            val prefs = EphemeralSharedPrefs(ctx.applicationInfo.targetSdkVersion)

            if (cloneOriginal) {
                val orig = ctx.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                orig.all.forEach { k, v ->
                    prefs.map[k] = v
                }
            }

            this[name] = prefs

            prefs
        }
    }
}

class EphemeralSharedPrefs(val targetSdk: Int) : SharedPreferences {

    internal val map = HashMap<String, Any?>()
    // match the "weakly referenced listeners" behavior of the regular SharedPreferences,
    // there's no WeakSet, approximate it by using a dummy value
    internal val listeners = WeakHashMap<ChangeListener, Any>()

    override fun getAll(): MutableMap<String, *>  = map

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(key: String?, defValue: T?): T? {
        synchronized(this) {
            return map[key!!] as T ?: defValue
        }
    }

    override fun getString(key: String?, defValue: String?): String? = get(key, defValue)
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = get(key, defValues)
    override fun getInt(key: String?, defValue: Int): Int = get(key, defValue) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = get(key, defValue) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = get(key, defValue) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key, defValue) ?: defValue

    override fun contains(key: String?): Boolean = map.contains(key)

    override fun edit(): SharedPreferences.Editor = Editor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(this) {
            listeners[listener] = this
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(this) {
            listeners.remove(listener)
        }
    }

    private class Editor(val prefs: EphemeralSharedPrefs) : SharedPreferences.Editor {
        val map = HashMap<String, Any?>()
        private val removedKeys = arrayListOf<String>()
        val thread = Thread.currentThread()

        private var cleared = false

        private fun checkThread() {
            check(Thread.currentThread() === thread)
        }

        private fun <T> put(key: String?, value: T?): Editor {
            checkThread()
            map[key!!] = value
            return this
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            checkThread()
            removedKeys.add(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            checkThread()
            cleared = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            checkThread()
            val listeners: Set<ChangeListener>

            synchronized(prefs) {
                listeners = prefs.listeners.keys

                if (cleared) {
                    prefs.map.clear()
                }
                removedKeys.forEach { key ->
                    prefs.map.remove(key)
                }
                map.forEach { k, v ->
                    prefs.map[k] = v
                }
            }

            // notify listeners outside the critical section

            if (cleared) {
                // see onSharedPreferenceChanged() doc
                if (prefs.targetSdk >= Build.VERSION_CODES.R) {
                    listeners.forEach {
                        it.onSharedPreferenceChanged(prefs, null)
                    }
                }
            }
            removedKeys.forEach { key ->
                listeners.forEach {
                    it.onSharedPreferenceChanged(prefs, key)
                }
            }
            map.forEach { k, _ ->
                listeners.forEach {
                    it.onSharedPreferenceChanged(prefs, k)
                }
            }
        }
    }
}

@SuppressLint("ApplySharedPref")
inline fun SharedPreferences.edit(commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}
