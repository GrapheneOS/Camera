package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSessionImpl
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapper
import app.grapheneos.camera.domain.gallery.mapper.VisibleCaptureMapperImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryControlsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryControlsDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDeletionDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDeletionDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDetailsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryDetailsDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPlaybackDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPlaybackDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPreviewsDelegate
import app.grapheneos.camera.ui.gallery.screen.delegate.GalleryPreviewsDelegateImpl
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapper
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapperImpl
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
    @ViewModelScoped
    abstract fun bindGalleryPreviewsDelegate(
        impl: GalleryPreviewsDelegateImpl,
    ): GalleryPreviewsDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindGalleryDetailsDelegate(
        impl: GalleryDetailsDelegateImpl,
    ): GalleryDetailsDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindGalleryPlaybackDelegate(
        impl: GalleryPlaybackDelegateImpl,
    ): GalleryPlaybackDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindGalleryControlsDelegate(
        impl: GalleryControlsDelegateImpl,
    ): GalleryControlsDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindGalleryDeletionDelegate(
        impl: GalleryDeletionDelegateImpl,
    ): GalleryDeletionDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindGalleryUiStateMapper(
        impl: GalleryUiStateMapperImpl,
    ): GalleryUiStateMapper

    @Binds
    @Reusable
    abstract fun bindVisibleCaptureMapper(
        impl: VisibleCaptureMapperImpl,
    ): VisibleCaptureMapper
}
