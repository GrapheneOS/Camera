package app.grapheneos.camera.ui.camerasettings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.camerasettings.licenses.LicensesScreen
import app.grapheneos.camera.ui.core.CameraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LicensesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                LicensesScreen(
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}
