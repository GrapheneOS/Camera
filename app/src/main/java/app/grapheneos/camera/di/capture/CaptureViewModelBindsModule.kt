package app.grapheneos.camera.di.capture

import app.grapheneos.camera.domain.capture.coordinator.VideoRecorder
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorderImpl
import app.grapheneos.camera.domain.capture.usecase.PlayRecordingStopSound
import app.grapheneos.camera.domain.capture.usecase.PlayRecordingStopSoundImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
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

    @Binds
    @Reusable
    abstract fun bindPlayRecordingStopSound(
        impl: PlayRecordingStopSoundImpl,
    ): PlayRecordingStopSound
}
