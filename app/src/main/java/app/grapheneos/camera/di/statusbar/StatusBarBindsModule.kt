package app.grapheneos.camera.di.statusbar

import app.grapheneos.camera.ui.statusbar.screen.mapper.StatusBarUiStateMapper
import app.grapheneos.camera.ui.statusbar.screen.mapper.StatusBarUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StatusBarBindsModule {

    @Binds
    @Reusable
    abstract fun bindStatusBarUiStateMapper(
        impl: StatusBarUiStateMapperImpl,
    ): StatusBarUiStateMapper
}
