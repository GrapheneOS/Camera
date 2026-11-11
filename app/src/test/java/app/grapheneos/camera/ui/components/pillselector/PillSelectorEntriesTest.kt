package app.grapheneos.camera.ui.components.pillselector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PillSelectorEntriesTest {

    private val entries = PillSelectorEntries<String, String>()

    @Test
    fun firstItems_areShownWithoutEntering() {
        show("a", "b")

        assertEquals(listOf("a", "b"), entries.keys)
        assertTrue(entries.renderedEntries.all { entry -> entry.visibility.currentState })
    }

    @Test
    fun aNewItem_entersInItsPlace() {
        show("a", "c")
        show("a", "b", "c")

        val entered = entries.renderedEntries[1]
        assertEquals(listOf("a", "b", "c"), entries.keys)
        assertFalse(entered.visibility.currentState)
        assertTrue(entered.isShown)
    }

    @Test
    fun aLeavingItem_keepsItsPlaceUntilItIsGone() {
        show("a", "b", "c")
        show("a", "c")

        assertEquals(listOf("a", "b", "c"), entries.keys)
        val visibility = entries.renderedEntries.map { entry -> entry.isShown }
        assertEquals(listOf(true, false, true), visibility)
    }

    @Test
    fun leavingItemsAtEitherEnd_keepTheirPlaces() {
        show("a", "b", "c")
        show("b")

        assertEquals(listOf("a", "b", "c"), entries.keys)
        val visibility = entries.renderedEntries.map { entry -> entry.isShown }
        assertEquals(listOf(false, true, false), visibility)
    }

    @Test
    fun consecutiveLeavingItems_followTheirSurvivingNeighborWhenItMoves() {
        show("a", "b", "c", "d", "e")
        show("d", "c")

        assertEquals(listOf("d", "a", "b", "c", "e"), entries.keys)
    }

    @Test
    fun aLeavingItemThatComesBack_isShownAgainInItsEntry() {
        show("a", "b")
        val left = entries.renderedEntries[1]
        show("a")
        show("a", "b")

        assertSame(left, entries.renderedEntries[1])
        assertTrue(left.isShown)
    }

    @Test
    fun anItemKeepingItsKey_keepsItsEntryWithTheNewValue() {
        entries.update(
            items = listOf("a1", "b1"),
            keyOf = { it.take(1) },
        )
        val kept = entries.renderedEntries.first()
        entries.update(
            items = listOf("a2", "b1"),
            keyOf = { it.take(1) },
        )

        assertSame(kept, entries.renderedEntries.first())
        assertEquals("a2", kept.item)
    }

    @Test
    fun itemsSharingAKey_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            show("a", "a")
        }
    }

    private fun show(vararg items: String) {
        entries.update(
            items = items.toList(),
            keyOf = { it },
        )
    }
}
