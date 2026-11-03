package app.grapheneos.camera.ui.camerasettings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.camerasettings.navigation.CameraSettingsIntents
import app.grapheneos.camera.ui.camerasettings.screen.CameraSettingsScreen
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsNavEvent
import app.grapheneos.camera.ui.core.theme.CameraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class CameraSettingsActivity : ComponentActivity() {

    @Inject
    internal lateinit var cameraSettingsIntents: CameraSettingsIntents

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                CameraSettingsScreen(
                    onNavigateBack = ::finish,
                    onNavigate = ::onNavigate,
                )
            }
        }
    }

    private fun onNavigate(event: CameraSettingsNavEvent) {
        when (event) {
            is CameraSettingsNavEvent.OpenLicenses -> cameraSettingsIntents.openLicenses(this)
        }
    }
}
