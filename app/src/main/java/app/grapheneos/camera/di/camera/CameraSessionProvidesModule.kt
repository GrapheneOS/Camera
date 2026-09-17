package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
internal class CameraSessionProvidesModule {

    @Provides
    @ActivityRetainedScoped
    fun provideCameraSession(
        session: CameraSessionImpl,
        lifecycle: ActivityRetainedLifecycle,
    ): CameraSession {
        lifecycle.addOnClearedListener(session::close)

        return session
    }
}
