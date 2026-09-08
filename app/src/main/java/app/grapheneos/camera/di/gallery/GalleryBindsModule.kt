package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.CapturedItemSession
import app.grapheneos.camera.domain.gallery.CapturedItemSessionImpl
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapperImpl
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocationImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal abstract class GalleryBindsModule {

    @Binds
    @ActivityScoped
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
