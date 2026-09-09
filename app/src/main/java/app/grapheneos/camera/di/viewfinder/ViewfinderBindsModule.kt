package app.grapheneos.camera.di.viewfinder

import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ViewfinderBindsModule {

    @Binds
    @Reusable
    abstract fun bindViewfinderUiStateMapper(
        impl: ViewfinderUiStateMapperImpl,
    ): ViewfinderUiStateMapper
}
