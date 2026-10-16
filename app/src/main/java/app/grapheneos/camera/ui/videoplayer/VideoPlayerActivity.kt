package app.grapheneos.camera.ui.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.videoplayer.screen.VideoPlayerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class VideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                VideoPlayerScreen(
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}
