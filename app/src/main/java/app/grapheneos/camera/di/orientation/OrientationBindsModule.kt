package app.grapheneos.camera.di.orientation

import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepository
import app.grapheneos.camera.data.orientation.repository.DeviceOrientationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrientationBindsModule {

    @Binds
    @Singleton
    abstract fun bindDeviceOrientationRepository(
        impl: DeviceOrientationRepositoryImpl,
    ): DeviceOrientationRepository
}
