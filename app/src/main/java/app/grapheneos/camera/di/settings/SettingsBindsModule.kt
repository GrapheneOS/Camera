package app.grapheneos.camera.di.settings

import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.di.core.DurablePreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsBindsModule {

    @Binds
    @Singleton
    @DurablePreferences
    abstract fun bindDurableSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository
}
