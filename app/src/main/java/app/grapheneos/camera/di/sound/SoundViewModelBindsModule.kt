package app.grapheneos.camera.di.sound

import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSoundImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class SoundViewModelBindsModule {

    @Binds
    @Reusable
    abstract fun bindPlayCameraSound(
        impl: PlayCameraSoundImpl,
    ): PlayCameraSound
}
