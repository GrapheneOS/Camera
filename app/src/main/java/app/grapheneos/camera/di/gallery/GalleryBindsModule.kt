package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapper
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapperImpl
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.DeleteTrashedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteTrashedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.FinishInterruptedDeletions
import app.grapheneos.camera.domain.gallery.usecase.FinishInterruptedDeletionsImpl
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItems
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItemsImpl
import app.grapheneos.camera.domain.gallery.usecase.RestoreCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.RestoreCapturedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.TrashCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.TrashCapturedItemImpl
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapper
import app.grapheneos.camera.ui.gallery.screen.mapper.GalleryUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GalleryBindsModule {

    @Binds
    @Reusable
    abstract fun bindDeleteCapturedItem(
        impl: DeleteCapturedItemImpl,
    ): DeleteCapturedItem

    @Binds
    @Reusable
    abstract fun bindFinishInterruptedDeletions(
        impl: FinishInterruptedDeletionsImpl,
    ): FinishInterruptedDeletions

    @Binds
    @Reusable
    abstract fun bindTrashCapturedItem(
        impl: TrashCapturedItemImpl,
    ): TrashCapturedItem

    @Binds
    @Reusable
    abstract fun bindRestoreCapturedItem(
        impl: RestoreCapturedItemImpl,
    ): RestoreCapturedItem

    @Binds
    @Reusable
    abstract fun bindDeleteTrashedItem(
        impl: DeleteTrashedItemImpl,
    ): DeleteTrashedItem

    @Binds
    @Reusable
    abstract fun bindLoadGalleryItems(
        impl: LoadGalleryItemsImpl,
    ): LoadGalleryItems

    @Binds
    @Reusable
    abstract fun bindGalleryItemsMapper(
        impl: GalleryItemsMapperImpl,
    ): GalleryItemsMapper

    @Binds
    @Reusable
    abstract fun bindGalleryUiStateMapper(
        impl: GalleryUiStateMapperImpl,
    ): GalleryUiStateMapper
}
