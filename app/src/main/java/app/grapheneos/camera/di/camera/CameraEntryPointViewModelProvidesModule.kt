package app.grapheneos.camera.di.camera

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal class CameraEntryPointViewModelProvidesModule {

    @Provides
    @ViewModelScoped
    fun provideCameraEntryPoint(arguments: SavedStateHandle): CameraEntryPoint {
        return CameraEntryPoint(
            isSecureSession = arguments.requireFlag(IS_SECURE_SESSION),
            isCaptureSession = arguments.requireFlag(IS_CAPTURE_SESSION),
            isVideoOnlySession = arguments.requireFlag(IS_VIDEO_ONLY_SESSION),
            requiresVideoModeOnly = arguments.requireFlag(REQUIRES_VIDEO_MODE_ONLY),
            allowsQrScanning = arguments.requireFlag(ALLOWS_QR_SCANNING),
            showsCameraModeTabs = arguments.requireFlag(SHOWS_CAMERA_MODE_TABS),
        )
    }

    private fun SavedStateHandle.requireFlag(key: String): Boolean {
        return requireNotNull(get<Boolean>(key)) {
            "The ViewModel was created without its entry point ($key)"
        }
    }

    companion object {
        private const val IS_SECURE_SESSION = "entry_point_is_secure_session"
        private const val IS_CAPTURE_SESSION = "entry_point_is_capture_session"
        private const val IS_VIDEO_ONLY_SESSION = "entry_point_is_video_only_session"
        private const val REQUIRES_VIDEO_MODE_ONLY = "entry_point_requires_video_mode_only"
        private const val ALLOWS_QR_SCANNING = "entry_point_allows_qr_scanning"
        private const val SHOWS_CAMERA_MODE_TABS = "entry_point_shows_camera_mode_tabs"

        fun arguments(entryPoint: CameraEntryPoint): Bundle {
            return Bundle().apply {
                putBoolean(IS_SECURE_SESSION, entryPoint.isSecureSession)
                putBoolean(IS_CAPTURE_SESSION, entryPoint.isCaptureSession)
                putBoolean(IS_VIDEO_ONLY_SESSION, entryPoint.isVideoOnlySession)
                putBoolean(REQUIRES_VIDEO_MODE_ONLY, entryPoint.requiresVideoModeOnly)
                putBoolean(ALLOWS_QR_SCANNING, entryPoint.allowsQrScanning)
                putBoolean(SHOWS_CAMERA_MODE_TABS, entryPoint.showsCameraModeTabs)
            }
        }
    }
}
