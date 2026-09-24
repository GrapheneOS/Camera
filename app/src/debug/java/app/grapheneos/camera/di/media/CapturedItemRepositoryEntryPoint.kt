package app.grapheneos.camera.di.media

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface CapturedItemRepositoryEntryPoint {
    fun capturedItemRepository(): CapturedItemRepository
}
