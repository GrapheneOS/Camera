package app.grapheneos.camera.di.media

import app.grapheneos.camera.domain.media.usecase.ChangeStorageLocation
import app.grapheneos.camera.domain.media.usecase.ChangeStorageLocationImpl
import app.grapheneos.camera.domain.media.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.domain.media.usecase.RevertToMediaStoreLocationImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class MediaViewModelBindsModule {

    @Binds
    @Reusable
    abstract fun bindChangeStorageLocation(
        impl: ChangeStorageLocationImpl,
    ): ChangeStorageLocation

    @Binds
    @Reusable
    abstract fun bindRevertToMediaStoreLocation(
        impl: RevertToMediaStoreLocationImpl,
    ): RevertToMediaStoreLocation
}
