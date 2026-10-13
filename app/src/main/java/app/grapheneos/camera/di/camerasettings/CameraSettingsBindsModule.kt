package app.grapheneos.camera.di.camerasettings

import app.grapheneos.camera.ui.camerasettings.licenses.mapper.LicensesUiStateMapper
import app.grapheneos.camera.ui.camerasettings.licenses.mapper.LicensesUiStateMapperImpl
import app.grapheneos.camera.ui.camerasettings.screen.mapper.CameraSettingsUiStateMapper
import app.grapheneos.camera.ui.camerasettings.screen.mapper.CameraSettingsUiStateMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraSettingsBindsModule {

    @Binds
    @Reusable
    abstract fun bindCameraSettingsUiStateMapper(
        impl: CameraSettingsUiStateMapperImpl,
    ): CameraSettingsUiStateMapper

    @Binds
    @Reusable
    abstract fun bindLicensesUiStateMapper(
        impl: LicensesUiStateMapperImpl,
    ): LicensesUiStateMapper
}
