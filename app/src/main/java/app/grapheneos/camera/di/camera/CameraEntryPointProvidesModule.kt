package app.grapheneos.camera.di.camera

import android.content.Context
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.QrTile
import app.grapheneos.camera.ui.activities.SecureActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal class CameraEntryPointProvidesModule {

    @Provides
    @ActivityScoped
    fun provideCameraEntryPoint(@ActivityContext context: Context): CameraEntryPoint {
        return CameraEntryPoint(
            isSecureSession = context is SecureActivity,
            isCaptureSession = context is CaptureActivity,
            isVideoOnlySession = context is VideoOnlyActivity,
            requiresVideoModeOnly = context is VideoOnlyActivity ||
                context is VideoCaptureActivity,
            allowsQrScanning = context !is SecureMainActivity,
            // Hiding the strip is not enough on its own: a fling reads the tab model rather than
            // the strip, and a transparent strip still takes taps, since a disabled parent does not
            // disable its children. There is no mode to switch to in these sessions, so build no
            // tabs at all.
            showsCameraModeTabs = context !is VideoOnlyActivity &&
                context !is CaptureActivity &&
                context !is QrTile,
        )
    }
}
