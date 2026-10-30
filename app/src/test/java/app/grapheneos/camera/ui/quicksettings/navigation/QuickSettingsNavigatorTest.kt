package app.grapheneos.camera.ui.quicksettings.navigation

import app.grapheneos.camera.ui.navigation.NavKey
import app.grapheneos.camera.ui.navigation.NavigatorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickSettingsNavigatorTest {

    private val backStack = mutableListOf<NavKey>()
    private val navigator = QuickSettingsNavigatorImpl(
        navigator = NavigatorImpl(backStack = backStack),
    )

    @Test
    fun navigateToQuickSettings_carriesTheSettingToHighlight() {
        navigator.navigateToQuickSettings(highlight = QuickSetting.SELF_TIMER)

        assertEquals(
            QuickSettingsNavKey(highlight = QuickSetting.SELF_TIMER),
            navigator.quickSettingsNavKey(),
        )
    }

    @Test
    fun navigateToQuickSettings_whileTheyAreOpen_keepsTheOpenOne() {
        navigator.navigateToQuickSettings(highlight = QuickSetting.FLASH)
        navigator.navigateToQuickSettings(highlight = null)

        assertEquals(listOf(QuickSettingsNavKey(highlight = QuickSetting.FLASH)), backStack)
    }

    @Test
    fun closeQuickSettings_removesTheOpenOneWhateverItHighlights() {
        navigator.navigateToQuickSettings(highlight = QuickSetting.FLASH)
        navigator.closeQuickSettings()

        assertNull(navigator.quickSettingsNavKey())
    }
}
