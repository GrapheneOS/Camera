package app.grapheneos.camera.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [EphemeralSharedPrefs] is what stops a lockscreen session from changing the settings the owner
 * sees after unlocking: a secure entry point is handed one of these instead of the file, cloned from
 * it but backed by memory.
 *
 * The clone being one-way is the entire security property, and nothing asserted it.
 */
@RunWith(RobolectricTestRunner::class)
class EphemeralSharedPrefsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun persistentPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ephemeralPrefs(): SharedPreferences {
        return EphemeralSharedPrefs.copyOf(context = context, name = PREFS_NAME)
    }

    @Before
    fun resetPersistentPrefs() {
        persistentPrefs().edit().clear().commit()
    }

    @Test
    fun clonesExistingValuesFromThePersistentPrefs() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        assertEquals(85, ephemeralPrefs().getInt("photoQuality", -1))
    }

    @Test
    fun writesNeverReachThePersistentPrefs() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().putInt("photoQuality", 20).commit()

        assertEquals(20, ephemeral.getInt("photoQuality", -1))
        assertEquals(85, persistentPrefs().getInt("photoQuality", -1))
    }

    @Test
    fun removalsNeverReachThePersistentPrefs() {
        persistentPrefs().edit().putBoolean("includeAudio", true).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().remove("includeAudio").commit()

        assertFalse(ephemeral.contains("includeAudio"))
        assertTrue(persistentPrefs().contains("includeAudio"))
    }

    @Test
    fun clearNeverReachesThePersistentPrefs() {
        persistentPrefs().edit().putBoolean("includeAudio", true).commit()

        val ephemeral = ephemeralPrefs()
        ephemeral.edit().clear().commit()

        assertFalse(ephemeral.contains("includeAudio"))
        assertTrue(persistentPrefs().contains("includeAudio"))
    }

    /** Each copy starts from the file again, so the session has to be handed one and keep it. */
    @Test
    fun eachCopyStartsFromWhatIsStored() {
        persistentPrefs().edit().putInt("photoQuality", 85).commit()

        ephemeralPrefs().edit().putInt("photoQuality", 42).commit()

        assertEquals(85, ephemeralPrefs().getInt("photoQuality", -1))
    }

    private companion object {
        // COMMON_PREFS_NAME
        const val PREFS_NAME = "commons"
    }
}
