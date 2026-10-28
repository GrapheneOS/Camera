package app.grapheneos.camera.di.quicksettings

import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapper
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class QuickSettingsBindsModule {

    @Binds
    @Reusable
    abstract fun bindQuickSettingsUiStateMapper(
        impl: QuickSettingsUiStateMapperImpl,
    ): QuickSettingsUiStateMapper
}
