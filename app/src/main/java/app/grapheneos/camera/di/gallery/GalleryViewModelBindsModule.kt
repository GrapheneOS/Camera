package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocationImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class GalleryViewModelBindsModule {

    @Binds
    @Reusable
    abstract fun bindRevertToMediaStoreLocation(
        impl: RevertToMediaStoreLocationImpl,
    ): RevertToMediaStoreLocation
}
