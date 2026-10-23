package app.grapheneos.camera.ui.gallery

import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.gallery.screen.GalleryScreen
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class GalleryActivity : ComponentActivity() {

    @Inject
    internal lateinit var systemGalleryLauncher: SystemGalleryLauncher

    @Inject
    internal lateinit var clipboardManager: ClipboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CameraTheme {
                GalleryScreen(
                    systemGalleryLauncher = systemGalleryLauncher,
                    clipboardManager = clipboardManager,
                    onNavigateBack = ::finish,
                )
            }
        }
    }
}
