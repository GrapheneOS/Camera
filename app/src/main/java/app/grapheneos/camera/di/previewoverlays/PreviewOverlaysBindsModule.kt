package app.grapheneos.camera.di.previewoverlays

import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PreviewOverlaysBindsModule {

    @Binds
    @Reusable
    abstract fun bindPreviewOverlaysUiStateMapper(
        impl: PreviewOverlaysUiStateMapperImpl,
    ): PreviewOverlaysUiStateMapper
}
