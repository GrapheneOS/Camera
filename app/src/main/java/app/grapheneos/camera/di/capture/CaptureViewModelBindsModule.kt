package app.grapheneos.camera.di.capture

import app.grapheneos.camera.domain.capture.VideoRecorder
import app.grapheneos.camera.domain.capture.VideoRecorderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class CaptureViewModelBindsModule {

    @Binds
    @ViewModelScoped
    abstract fun bindVideoRecorder(
        impl: VideoRecorderImpl,
    ): VideoRecorder
}
