package app.grapheneos.camera.ui.core.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

private const val SCREEN_WIDTH_DP = 360
private const val SCREEN_HEIGHT_DP = 720

@Preview(
    name = "Light",
    widthDp = SCREEN_WIDTH_DP,
    heightDp = SCREEN_HEIGHT_DP,
)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    widthDp = SCREEN_WIDTH_DP,
    heightDp = SCREEN_HEIGHT_DP,
)
internal annotation class PreviewScreenLightDark
