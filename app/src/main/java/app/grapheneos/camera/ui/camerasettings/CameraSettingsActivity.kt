package app.grapheneos.camera.ui.camerasettings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.camerasettings.screen.CameraSettingsScreen
import app.grapheneos.camera.ui.core.theme.CameraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class CameraSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                CameraSettingsScreen(
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}
