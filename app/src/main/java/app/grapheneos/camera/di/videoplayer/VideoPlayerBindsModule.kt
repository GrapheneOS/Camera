package app.grapheneos.camera.di.videoplayer

import app.grapheneos.camera.ui.videoplayer.navigation.VideoPlayerLauncher
import app.grapheneos.camera.ui.videoplayer.navigation.VideoPlayerLauncherImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoPlayerBindsModule {

    @Binds
    @Reusable
    abstract fun bindVideoPlayerLauncher(
        impl: VideoPlayerLauncherImpl,
    ): VideoPlayerLauncher
}
