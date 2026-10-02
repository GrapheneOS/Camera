package app.grapheneos.camera.ui.components.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStopListTest {

    private val stopList = ZoomStopList(stops = listOf(0.5f, 1f, 2f))

    @Test
    fun selectedIndex_betweenStops_isTheStopBelow() {
        assertEquals(1, stopList.selectedIndex(value = 1.9f))
        assertEquals(2, stopList.selectedIndex(value = 4.5f))
    }

    @Test
    fun selectedIndex_onAStop_isThatStop() {
        assertEquals(1, stopList.selectedIndex(value = 1f))
    }

    @Test
    fun selectedIndex_belowEveryStop_isTheFirst() {
        assertEquals(0, stopList.selectedIndex(value = 0.3f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun stopList_withoutStops_isRejected() {
        ZoomStopList(stops = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun stopList_withStopsOutOfOrder_isRejected() {
        ZoomStopList(stops = listOf(2f, 1f))
    }
}
