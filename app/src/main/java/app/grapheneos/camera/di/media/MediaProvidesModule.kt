package app.grapheneos.camera.di.media

import android.content.Context
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepositoryImpl
import app.grapheneos.camera.data.media.repository.LockscreenCapturedItemRepository
import app.grapheneos.camera.ui.activities.SecureActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal class MediaProvidesModule {

    @Provides
    @ActivityScoped
    fun provideCapturedItemRepository(
        @ActivityContext context: Context,
        repository: CapturedItemRepositoryImpl,
    ): CapturedItemRepository {
        return when (context) {
            is SecureActivity -> LockscreenCapturedItemRepository(repository)
            else -> repository
        }
    }
}
