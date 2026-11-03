package app.grapheneos.camera.di.mediaactions

import app.grapheneos.camera.ui.mediaactions.CapturedItemIntents
import app.grapheneos.camera.ui.mediaactions.CapturedItemIntentsImpl
import app.grapheneos.camera.ui.mediaactions.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaactions.SystemGalleryLauncherImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaActionsBindsModule {

    @Binds
    @Reusable
    abstract fun bindCapturedItemIntents(
        impl: CapturedItemIntentsImpl,
    ): CapturedItemIntents

    @Binds
    @Reusable
    abstract fun bindSystemGalleryLauncher(
        impl: SystemGalleryLauncherImpl,
    ): SystemGalleryLauncher
}
