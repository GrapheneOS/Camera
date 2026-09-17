package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocationImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class GalleryViewModelBindsModule {

    @Binds
    @ViewModelScoped
    abstract fun bindRevertToMediaStoreLocation(
        impl: RevertToMediaStoreLocationImpl,
    ): RevertToMediaStoreLocation
}
