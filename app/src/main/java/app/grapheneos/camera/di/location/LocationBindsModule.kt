package app.grapheneos.camera.di.location

import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.location.repository.LocationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationBindsModule {

    @Binds
    @Reusable
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository
}
