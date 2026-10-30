package app.grapheneos.camera.ui.quicksettings.navigation

import androidx.compose.runtime.Stable
import app.grapheneos.camera.ui.navigation.Navigator

@Stable
internal interface QuickSettingsNavigator {

    fun quickSettingsNavKey(): QuickSettingsNavKey?

    fun navigateToQuickSettings(highlight: QuickSetting?)

    fun closeQuickSettings()
}

internal class QuickSettingsNavigatorImpl(
    private val navigator: Navigator,
) : QuickSettingsNavigator {

    override fun quickSettingsNavKey(): QuickSettingsNavKey? {
        return navigator.backStack.filterIsInstance<QuickSettingsNavKey>().lastOrNull()
    }

    override fun navigateToQuickSettings(highlight: QuickSetting?) {
        if (quickSettingsNavKey() == null) {
            navigator.push(QuickSettingsNavKey(highlight = highlight))
        }
    }

    override fun closeQuickSettings() {
        quickSettingsNavKey()?.let(navigator::removeDestination)
    }
}
