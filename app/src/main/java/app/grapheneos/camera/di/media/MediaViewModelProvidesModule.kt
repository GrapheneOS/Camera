package app.grapheneos.camera.di.media

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepositoryImpl
import app.grapheneos.camera.data.media.repository.LockscreenCapturedItemRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal class MediaViewModelProvidesModule {

    @Provides
    @ViewModelScoped
    fun provideCapturedItemRepository(
        entryPoint: CameraEntryPoint,
        repository: CapturedItemRepositoryImpl,
    ): CapturedItemRepository {
        return when {
            entryPoint.isSecureSession -> LockscreenCapturedItemRepository(repository)
            else -> repository
        }
    }
}
