package app.grapheneos.camera.ui.components.modeselector

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeSelectorGeometryTest {

    private val geometry = geometry(highlightInset = 0f)

    @Test
    fun start_addsUpTheWidthsBefore() {
        assertEquals(0f, geometry.start(index = 0))
        assertEquals(100f, geometry.start(index = 1))
        assertEquals(160f, geometry.start(index = 2))
    }

    @Test
    fun center_isTheItemsCenterOnAnItem() {
        assertEquals(50f, geometry.center(position = 0f))
        assertEquals(130f, geometry.center(position = 1f))
        assertEquals(230f, geometry.center(position = 2f))
    }

    @Test
    fun centerAndWidth_moveLinearlyBetweenTwoItems() {
        assertEquals(90f, geometry.center(position = 0.5f))
        assertEquals(100f, geometry.width(position = 1.5f))
    }

    @Test
    fun position_isTheInverseOfCenter() {
        val center = geometry.center(position = 1.25f)

        assertEquals(0.5f, geometry.position(center = 90f))
        assertEquals(1.25f, geometry.position(center = center))
    }

    @Test
    fun position_clampsToTheFirstAndLastItem() {
        assertEquals(0f, geometry.position(center = -500f))
        assertEquals(2f, geometry.position(center = 900f))
    }

    @Test
    fun nearestIndex_roundsAndClamps() {
        assertEquals(1, geometry.nearestIndex(position = 1.4f))
        assertEquals(2, geometry.nearestIndex(position = 1.6f))
        assertEquals(2, geometry.nearestIndex(position = 7f))
    }

    @Test
    fun highlightStartIn_isMeasuredFromEachItemsLeftEdge() {
        assertEquals(0f, highlightStartIn(index = 1))
        assertEquals(100f, highlightStartIn(index = 0))
        assertEquals(3f, highlightStartIn(index = 1, highlightInset = 3f))
    }

    @Test
    fun highlightStartIn_mirrorsTheItemsInRtl() {
        assertEquals(0f, highlightStartIn(index = 1, isRtl = true))
        assertEquals(-60f, highlightStartIn(index = 0, isRtl = true))
    }

    @Test
    fun position_withOneItem_isAlwaysThatItem() {
        val single = ModeSelectorGeometry(
            itemWidths = listOf(80f),
            containerWidth = 400f,
            highlightInset = 0f,
        )

        assertEquals(0f, single.position(center = 300f))
    }

    @Test
    fun nearestIndex_beforeTheFirstLayout_isTheFirst() {
        val empty = ModeSelectorGeometry(
            itemWidths = emptyList(),
            containerWidth = 0f,
            highlightInset = 0f,
        )

        assertEquals(0, empty.nearestIndex(position = 2f))
    }

    private fun highlightStartIn(
        index: Int,
        highlightInset: Float = 0f,
        isRtl: Boolean = false,
    ): Float {
        return geometry(highlightInset = highlightInset).highlightStartIn(
            index = index,
            position = 1f,
            isRtl = isRtl,
        )
    }

    private fun geometry(highlightInset: Float): ModeSelectorGeometry {
        return ModeSelectorGeometry(
            itemWidths = listOf(100f, 60f, 140f),
            containerWidth = 400f,
            highlightInset = highlightInset,
        )
    }
}
