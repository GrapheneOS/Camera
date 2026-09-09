package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.session.SnapshotProbeCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SnapshotProbeCacheEntryPoint {
    fun snapshotProbeCache(): SnapshotProbeCache
}
