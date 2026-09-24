package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSessionImpl
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapperImpl
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocationImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class GalleryViewModelBindsModule {

    @Binds
    @ViewModelScoped
    abstract fun bindCapturedItemSession(
        impl: CapturedItemSessionImpl,
    ): CapturedItemSession

    @Binds
    @Reusable
    abstract fun bindVisibleCaptureMapper(
        impl: VisibleCaptureMapperImpl,
    ): VisibleCaptureMapper

    @Binds
    @Reusable
    abstract fun bindRevertToMediaStoreLocation(
        impl: RevertToMediaStoreLocationImpl,
    ): RevertToMediaStoreLocation
}
