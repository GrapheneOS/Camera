package app.grapheneos.camera.di.permission

import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.data.permission.repository.PermissionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionBindsModule {

    @Binds
    @Reusable
    abstract fun bindPermissionRepository(
        impl: PermissionRepositoryImpl,
    ): PermissionRepository
}
