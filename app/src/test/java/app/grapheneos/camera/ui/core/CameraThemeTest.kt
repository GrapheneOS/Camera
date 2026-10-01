package app.grapheneos.camera.ui.core

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CameraThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun cameraTheme_darkBelowDynamicColor_usesTheBaselineDarkScheme() {
        val colorScheme = composeColorScheme(darkTheme = true)

        assertEquals(darkColorScheme().primary, colorScheme.primary)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun cameraTheme_lightBelowDynamicColor_usesTheBaselineLightScheme() {
        val colorScheme = composeColorScheme(darkTheme = false)

        assertEquals(lightColorScheme().primary, colorScheme.primary)
    }

    @Test
    fun cameraTheme_darkWithDynamicColor_usesTheDynamicDarkScheme() {
        val colorScheme = composeColorScheme(darkTheme = true)

        assertNotEquals(darkColorScheme().primary, colorScheme.primary)
        assertEquals(dynamicDarkColorScheme(context).primary, colorScheme.primary)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun cameraTheme_light_keepsTheDarkAccentForOverlays() {
        var overlayAccent = Color.Unspecified

        composeRule.setContent {
            CameraTheme(darkTheme = false) {
                overlayAccent = MaterialTheme.cameraColors.overlayAccent
            }
        }

        assertEquals(darkColorScheme().primary, overlayAccent)
    }

    private fun composeColorScheme(darkTheme: Boolean): ColorScheme {
        lateinit var colorScheme: ColorScheme

        composeRule.setContent {
            CameraTheme(darkTheme = darkTheme) {
                colorScheme = MaterialTheme.colorScheme
            }
        }

        return colorScheme
    }
}
