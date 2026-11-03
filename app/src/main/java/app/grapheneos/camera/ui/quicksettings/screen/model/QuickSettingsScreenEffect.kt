package app.grapheneos.camera.ui.quicksettings.screen.model

internal sealed interface QuickSettingsScreenEffect {
    data object TextCopied : QuickSettingsScreenEffect
}
