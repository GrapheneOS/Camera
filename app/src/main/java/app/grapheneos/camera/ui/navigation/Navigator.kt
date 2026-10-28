package app.grapheneos.camera.ui.navigation

internal interface Navigator {

    val backStack: MutableList<NavKey>

    fun push(destination: NavKey)

    fun back()

    fun removeDestination(destination: NavKey)
}

internal class NavigatorImpl(
    override val backStack: MutableList<NavKey>,
) : Navigator {

    override fun push(destination: NavKey) {
        destination
            .takeIf { it != backStack.lastOrNull() }
            ?.let(backStack::add)
    }

    override fun back() {
        backStack.removeLastOrNull()
    }

    override fun removeDestination(destination: NavKey) {
        backStack.removeAll { navKey -> navKey == destination }
    }
}
