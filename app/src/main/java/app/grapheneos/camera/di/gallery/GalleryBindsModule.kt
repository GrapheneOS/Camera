package app.grapheneos.camera.di.gallery

import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItem
import app.grapheneos.camera.domain.gallery.usecase.DeleteCapturedItemImpl
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
}
