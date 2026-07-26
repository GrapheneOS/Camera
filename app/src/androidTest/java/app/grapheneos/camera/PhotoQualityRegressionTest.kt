package app.grapheneos.camera

import android.Manifest
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoQualityRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** More Settings is started from a resumed camera, which a dozing device cannot provide. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * NumInputFilter cannot veto a deletion: the empty replacement it returns to reject an edit is
     * the very edit a deletion asks for. Deleting the leading digit of "10" therefore leaves "0"
     * behind, and committing that stored a quality ImageCapture refuses, crashing the next bind
     * with "jpegQuality is out of range of [1, 100]".
     */
    @Test
    fun photoQualityOutOfRange_isNotCommitted() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            // The same instance More Settings edits, so it can be read while that screen is up
            val camConfig = camConfigOf(scenario)
            val stored = camConfig.photoQuality

            val settings = openMoreSettings(scenario)
            val field = settings.findViewById<EditText>(R.id.photo_quality)

            instrumentation.runOnMainSync {
                field.setText("10")
                field.text.delete(0, 1)
                assertEquals("0", field.text.toString())

                instrumentation.callActivityOnPause(settings)

                assertEquals(stored, camConfig.photoQuality)
                assertEquals(stored.toString(), field.text.toString())
            }
        }
    }

    /** The range check may not reject the values it exists to let through. */
    @Test
    fun photoQualityInRange_isCommitted() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            val camConfig = camConfigOf(scenario)
            val stored = camConfig.photoQuality

            val settings = openMoreSettings(scenario)
            val field = settings.findViewById<EditText>(R.id.photo_quality)

            try {
                instrumentation.runOnMainSync {
                    field.setText("77")
                    instrumentation.callActivityOnPause(settings)

                    assertEquals(77, camConfig.photoQuality)
                }
            } finally {
                camConfig.photoQuality = stored
            }
        }
    }

    private fun camConfigOf(scenario: ActivityScenario<MainActivity>): CamConfig {
        lateinit var camConfig: CamConfig
        scenario.onActivity { camConfig = it.camConfig }
        return camConfig
    }

    private fun openMoreSettings(scenario: ActivityScenario<MainActivity>): MoreSettings {
        val monitor = instrumentation.addMonitor(MoreSettings::class.java.name, null, false)
        scenario.onActivity { MoreSettings.start(it) }
        val settings = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
        assertNotNull("More Settings did not open", settings)
        return settings as MoreSettings
    }
}
