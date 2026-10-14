package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextZoomStopMapperTest {

    private val mapper = NextZoomStopMapperImpl()

    @Test
    fun zoomingIn_walksTheWideAnglePrimaryDoubleAndMaximumStops() {
        val stops = generateSequence(0.5f) { ratio ->
            mapper.map(zoom = zoom(ratio), direction = ZoomDirection.IN)
        }.toList()

        assertEquals(listOf(0.5f, 1f, 2f, 8f), stops)
    }

    @Test
    fun zoomingOut_walksTheSameStopsBack() {
        val stops = generateSequence(8f) { ratio ->
            mapper.map(zoom = zoom(ratio), direction = ZoomDirection.OUT)
        }.toList()

        assertEquals(listOf(8f, 2f, 1f, 0.5f), stops)
    }

    @Test
    fun betweenStops_goesToTheNearestOneInThatDirection() {
        assertEquals(2f, mapper.map(zoom = zoom(1.4f), direction = ZoomDirection.IN))
        assertEquals(1f, mapper.map(zoom = zoom(1.4f), direction = ZoomDirection.OUT))
    }

    @Test
    fun withoutAWideAngleCamera_startsAtThePrimaryOne() {
        val noWideAngle = zoom(ratio = 1f).copy(minZoomRatio = 1f)

        assertNull(mapper.map(zoom = noWideAngle, direction = ZoomDirection.OUT))
        assertEquals(2f, mapper.map(zoom = noWideAngle, direction = ZoomDirection.IN))
    }

    @Test
    fun aMaximumBelowTheDoubleStop_leavesItOut() {
        val shortZoom = zoom(ratio = 1f).copy(maxZoomRatio = 1.5f)

        assertEquals(1.5f, mapper.map(zoom = shortZoom, direction = ZoomDirection.IN))
    }

    private fun zoom(ratio: Float): CameraZoom {
        return CameraZoom(
            zoomRatio = ratio,
            linearZoom = 0f,
            minZoomRatio = 0.5f,
            maxZoomRatio = 8f,
        )
    }
}
