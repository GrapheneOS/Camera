package app.grapheneos.camera.di.settings

import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsMapperBindsModule {

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
