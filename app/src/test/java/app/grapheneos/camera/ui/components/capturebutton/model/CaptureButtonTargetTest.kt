package app.grapheneos.camera.ui.components.capturebutton.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.ui.core.PREVIEW_LOCK_ICON
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureButtonTargetTest {

    private val density = Density(density = 1f)
    private val start = CaptureButtonTarget(
        direction = CaptureButtonDirection.Start,
        icon = PREVIEW_LOCK_ICON,
        accessibilityLabel = "Lock",
        distance = 100.dp,
    )

    @Test
    fun progress_halfwayTowardsTheTarget_isHalf() {
        val progress = start.progress(
            offset = Offset(x = -50f, y = 0f),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(0.5f, progress)
    }

    @Test
    fun progress_awayFromTheTarget_isZero() {
        val progress = start.progress(
            offset = Offset(x = 50f, y = 0f),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(0f, progress)
    }

    @Test
    fun progress_pastTheTarget_staysAtOne() {
        val progress = start.progress(
            offset = Offset(x = -300f, y = 0f),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(1f, progress)
    }

    @Test
    fun progress_rightToLeft_mirrorsTheStart() {
        val progress = start.progress(
            offset = Offset(x = 50f, y = 0f),
            density = density,
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(0.5f, progress)
    }

    @Test
    fun pull_diagonalDrag_followsOnlyTheTargetsAxis() {
        val pull = start.pull(
            offset = Offset(x = -50f, y = -80f),
            density = density,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(Offset(x = -50f, y = 0f), pull)
    }
}
