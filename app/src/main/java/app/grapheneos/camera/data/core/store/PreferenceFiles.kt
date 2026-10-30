package app.grapheneos.camera.data.core.store

import android.content.SharedPreferences

interface PreferenceFiles {

    fun open(name: String): SharedPreferences

    fun delete(name: String)
}
