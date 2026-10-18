package app.grapheneos.camera.di.mediaviewer

import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncherImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaViewerBindsModule {

    @Binds
    @Reusable
    abstract fun bindSystemGalleryLauncher(
        impl: SystemGalleryLauncherImpl,
    ): SystemGalleryLauncher
}
