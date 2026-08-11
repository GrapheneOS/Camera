package app.grapheneos.camera.di.preferences

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.store.commonPreferences
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.activities.MoreSettingsSecure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A screen marked secure is one the lockscreen can reach, and marking it is the whole of what stops
 * it writing the owner's files — nothing below this module reads the entry point again.
 */
@RunWith(RobolectricTestRunner::class)
class PreferencesProvidesModuleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val module = PreferencesProvidesModule()

    @Before
    fun clearOwnerPreferences() {
        commonPreferences(context, ephemeral = false).edit().clear().commit()
    }

    @Test
    fun provideSessionPreferences_secureActivity_keepsWritesOutOfTheOwnersFile() {
        val preferences = module.provideSessionPreferences(activity(MoreSettingsSecure::class.java))

        preferences.edit().putBoolean(CHANGED_SETTING, true).commit()

        assertTrue(preferences.getBoolean(CHANGED_SETTING, false))
        assertFalse(commonPreferences(context, ephemeral = false).contains(CHANGED_SETTING))
    }

    @Test
    fun provideSessionPreferences_regularActivity_writesTheOwnersFile() {
        val preferences = module.provideSessionPreferences(activity(MoreSettings::class.java))

        preferences.edit().putBoolean(CHANGED_SETTING, true).commit()

        assertTrue(commonPreferences(context, ephemeral = false).getBoolean(CHANGED_SETTING, false))
    }

    @Test
    fun provideModePreferences_secureActivity_keepsEveryModesWritesOutOfTheOwnersFiles() {
        val preferences = module.provideModePreferences(activity(MoreSettingsSecure::class.java))

        preferences.values.forEach {
            it.value.edit().putBoolean(CHANGED_SETTING, true).commit()
        }

        preferences.keys.forEach {
            assertFalse(
                it.name,
                context.getSharedPreferences(it.name, Context.MODE_PRIVATE)
                    .contains(CHANGED_SETTING),
            )
        }
    }

    private fun <T : Activity> activity(type: Class<T>): T {
        return Robolectric.buildActivity(type).get()
    }

    companion object {
        private const val CHANGED_SETTING = "a_setting_the_session_changed"
    }
}
