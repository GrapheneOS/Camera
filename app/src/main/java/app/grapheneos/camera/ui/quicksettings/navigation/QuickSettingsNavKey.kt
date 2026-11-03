package app.grapheneos.camera.ui.quicksettings.navigation

import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.navigation.NavKey

internal data class QuickSettingsNavKey(
    val highlight: QuickSetting? = null,
) : NavKey
