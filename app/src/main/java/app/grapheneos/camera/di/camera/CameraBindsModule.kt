package app.grapheneos.camera.di.camera

import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapper
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapperImpl
import app.grapheneos.camera.domain.camera.usecase.ResolveInVideoSnapshotSupport
import app.grapheneos.camera.domain.camera.usecase.ResolveInVideoSnapshotSupportImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraBindsModule {

    @Binds
    @Reusable
    abstract fun bindVideoQualityFeatureMapper(
        impl: VideoQualityFeatureMapperImpl,
    ): VideoQualityFeatureMapper

    @Binds
    @Reusable
    abstract fun bindResolveInVideoSnapshotSupport(
        impl: ResolveInVideoSnapshotSupportImpl,
    ): ResolveInVideoSnapshotSupport
}
