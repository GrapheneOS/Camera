package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionImpl
import app.grapheneos.camera.data.camera.session.ExtensionUsability
import app.grapheneos.camera.data.camera.session.ExtensionUsabilityImpl
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.camera.session.VideoRecordingSessionImpl
import app.grapheneos.camera.data.camera.session.ZoomStateTracker
import app.grapheneos.camera.data.camera.session.ZoomStateTrackerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class CameraSessionBindsModule {

    @Binds
    @ActivityRetainedScoped
    abstract fun bindCameraSession(
        impl: CameraSessionImpl,
    ): CameraSession

    @Binds
    @ActivityRetainedScoped
    abstract fun bindExtensionUsability(
        impl: ExtensionUsabilityImpl,
    ): ExtensionUsability

    @Binds
    @ActivityRetainedScoped
    abstract fun bindVideoRecordingSession(
        impl: VideoRecordingSessionImpl,
    ): VideoRecordingSession

    @Binds
    @ActivityRetainedScoped
    abstract fun bindZoomStateTracker(
        impl: ZoomStateTrackerImpl,
    ): ZoomStateTracker
}
