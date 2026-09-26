package app.grapheneos.camera.di.location

import app.grapheneos.camera.data.location.mapper.OptimalLocationMapper
import app.grapheneos.camera.data.location.mapper.OptimalLocationMapperImpl
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.location.repository.LocationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationBindsModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository

    @Binds
    @Reusable
    abstract fun bindOptimalLocationMapper(
        impl: OptimalLocationMapperImpl,
    ): OptimalLocationMapper
}
