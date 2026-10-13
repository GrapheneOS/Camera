package app.grapheneos.camera.di.licenses

import app.grapheneos.camera.data.licenses.repository.LicensesRepository
import app.grapheneos.camera.data.licenses.repository.LicensesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LicensesBindsModule {

    @Binds
    @Reusable
    abstract fun bindLicensesRepository(
        impl: LicensesRepositoryImpl,
    ): LicensesRepository
}
