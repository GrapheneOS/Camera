package app.grapheneos.camera.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraModeLabelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cameraModeLabel_everyMode_resolvesToANonBlankString() {
        CameraMode.entries.forEach { mode ->
            assertTrue(
                "$mode has a blank label",
                context.getString(cameraModeLabel(mode)).isNotBlank(),
            )
        }
    }

    @Test
    fun cameraModeLabel_everyMode_isLabelledDistinctly() {
        val labels = CameraMode.entries.map { context.getString(cameraModeLabel(it)) }

        assertEquals(CameraMode.entries.size, labels.toSet().size)
    }
}
