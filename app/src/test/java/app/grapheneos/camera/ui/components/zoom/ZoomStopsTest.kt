package app.grapheneos.camera.ui.components.zoom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.grapheneos.camera.ui.core.CameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZoomStopsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableFloatStateOf(1f)
    private var enabled by mutableStateOf(true)
    private val clicks = mutableListOf<Float>()

    @Test
    fun zoomStops_betweenStops_selectTheStopBelowWithTheValue() {
        value = 4.5f
        setContent()

        stop(description = "4.5×").assertIsSelected()
        stop(description = ".5×").assertIsNotSelected()
        stop(description = "1×").assertIsNotSelected()
    }

    @Test
    fun zoomStops_onAStop_selectIt() {
        value = 2f
        setContent()

        stop(description = "2×").assertIsSelected()
        stop(description = "1×").assertIsNotSelected()
    }

    @Test
    fun zoomStops_belowEveryStop_selectTheFirst() {
        value = 0.7f
        setContent(stops = listOf(1f, 2f, 5f))

        stop(description = ".7×").assertIsSelected()
        stop(description = "2×").assertIsNotSelected()
    }

    @Test
    fun zoomStops_valueCrossesAStop_moveTheSelection() {
        value = 1.9f
        setContent()

        value = 2.3f

        stop(description = "2.3×").assertIsSelected()
        stop(description = "1×").assertIsNotSelected()
    }

    @Test
    fun zoomStops_stopTapped_reportsItWithoutChangingTheValue() {
        setContent()

        stop(description = "2×").performClick()

        assertEquals(listOf(2f), clicks)
        stop(description = "1×").assertIsSelected()
    }

    @Test
    fun zoomStops_disabled_ignoreTaps() {
        enabled = false
        setContent()

        stop(description = "2×").performClick()

        stop(description = "2×").assertIsNotEnabled()
        assertEquals(emptyList<Float>(), clicks)
    }

    private fun setContent(stops: List<Float> = listOf(0.5f, 1f, 2f)) {
        composeRule.setContent {
            CameraTheme {
                ZoomStops(
                    value = value,
                    stops = stops,
                    valueSuffix = "×",
                    onStopClick = { stop -> clicks += stop },
                    enabled = enabled,
                )
            }
        }
    }

    private fun stop(description: String): SemanticsNodeInteraction {
        return composeRule.onNodeWithContentDescription(description)
    }
}
