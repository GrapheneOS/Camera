package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSessionImpl
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapperImpl
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
}
