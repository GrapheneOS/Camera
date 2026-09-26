package app.grapheneos.camera.di.sound

import app.grapheneos.camera.data.sound.player.CameraSoundPlayer
import app.grapheneos.camera.data.sound.player.CameraSoundPlayerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SoundBindsModule {

    @Binds
    @Singleton
    abstract fun bindCameraSoundPlayer(
        impl: CameraSoundPlayerImpl,
    ): CameraSoundPlayer
}
