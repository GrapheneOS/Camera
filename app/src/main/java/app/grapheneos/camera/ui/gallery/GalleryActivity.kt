package app.grapheneos.camera.ui.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.core.CameraTheme
import app.grapheneos.camera.ui.gallery.screen.GalleryScreen
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class GalleryActivity : ComponentActivity() {

    @Inject
    internal lateinit var videoPlayerLauncher: VideoPlayerLauncher

    @Inject
    internal lateinit var systemGalleryLauncher: SystemGalleryLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                GalleryScreen(
                    videoPlayerLauncher = videoPlayerLauncher,
                    systemGalleryLauncher = systemGalleryLauncher,
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}
