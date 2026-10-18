package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapper
import app.grapheneos.camera.domain.gallery.mapper.GalleryItemsMapperImpl
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItemImpl
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItems
import app.grapheneos.camera.domain.gallery.usecase.LoadGalleryItemsImpl
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
