package app.grapheneos.camera.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {

    private val backStack = mutableListOf<NavKey>()

    private val navigator = NavigatorImpl(backStack = backStack)

    @Test
    fun push_doesNotStackTheDestinationAlreadyOnTop() {
        navigator.push(First)
        navigator.push(First)
        navigator.push(Second)

        assertEquals(listOf(First, Second), backStack)
    }

    @Test
    fun back_popsTheTopAndIgnoresAnEmptyStack() {
        navigator.push(First)
        navigator.push(Second)

        navigator.back()
        assertEquals(listOf(First), backStack)

        navigator.back()
        navigator.back()
        assertEquals(emptyList<NavKey>(), backStack)
    }

    @Test
    fun removeDestination_leavesTheRestOfTheStack() {
        navigator.push(First)
        navigator.push(Second)

        navigator.removeDestination(First)
        navigator.removeDestination(First)

        assertEquals(listOf(Second), backStack)
    }

    private data object First : NavKey

    private data object Second : NavKey
}
