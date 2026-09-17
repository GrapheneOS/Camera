package app.grapheneos.camera.di.settings

import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapperImpl
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.di.core.DurablePreferences
import dagger.Binds
import dagger.Module
import dagger.Reusable
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

    @Binds
    @Reusable
    abstract fun bindCameraSettingsMapper(
        impl: CameraSettingsMapperImpl,
    ): CameraSettingsMapper

    @Binds
    @Reusable
    abstract fun bindModeSettingsMapper(
        impl: ModeSettingsMapperImpl,
    ): ModeSettingsMapper

    @Binds
    @Reusable
    abstract fun bindStoredVideoQualityMapper(
        impl: StoredVideoQualityMapperImpl,
    ): StoredVideoQualityMapper
}
