package app.grapheneos.camera.data.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.modePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceFilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A second in-memory copy would be re-read from the owner's file. */
    @Test
    fun modePreferences_repeatedLookups_returnTheSameFile() {
        val preferences = modePreferences(context, ephemeral = true)
        val first = preferences.getValue(CameraMode.VIDEO).value

        first.edit().putInt("photoQuality", 42).commit()

        assertSame(first, preferences.getValue(CameraMode.VIDEO).value)
        assertEquals(42, preferences.getValue(CameraMode.VIDEO).value.getInt("photoQuality", -1))
    }

    @Test
    fun modePreferences_eachMode_getsItsOwnFile() {
        val preferences = modePreferences(context, ephemeral = true)

        preferences.getValue(CameraMode.VIDEO).value.edit().putBoolean("geo_tagging", true).commit()

        assertFalse(
            preferences.getValue(CameraMode.CAMERA).value.getBoolean("geo_tagging", false),
        )
    }
}
