package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.camera.repository.CameraProviderSourceImpl
import app.grapheneos.camera.data.camera.repository.FeatureCombinationSupport
import app.grapheneos.camera.data.camera.repository.FeatureCombinationSupportImpl
import app.grapheneos.camera.data.camera.store.ExtensionAvailabilityStore
import app.grapheneos.camera.data.camera.store.ExtensionAvailabilityStoreImpl
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapper
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapperImpl
import app.grapheneos.camera.domain.camera.usecase.BuildCameraSessionPlan
import app.grapheneos.camera.domain.camera.usecase.BuildCameraSessionPlanImpl
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModesImpl
import app.grapheneos.camera.domain.camera.usecase.ResolveInVideoSnapshotSupport
import app.grapheneos.camera.domain.camera.usecase.ResolveInVideoSnapshotSupportImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CameraBindsModule {

    @Binds
    @Singleton
    abstract fun bindExtensionAvailabilityStore(
        impl: ExtensionAvailabilityStoreImpl,
    ): ExtensionAvailabilityStore

    @Binds
    @Reusable
    abstract fun bindCameraProviderSource(
        impl: CameraProviderSourceImpl,
    ): CameraProviderSource

    @Binds
    @Reusable
    abstract fun bindFeatureCombinationSupport(
        impl: FeatureCombinationSupportImpl,
    ): FeatureCombinationSupport

    @Binds
    @Reusable
    abstract fun bindVideoQualityFeatureMapper(
        impl: VideoQualityFeatureMapperImpl,
    ): VideoQualityFeatureMapper

    @Binds
    @Reusable
    abstract fun bindBuildCameraSessionPlan(
        impl: BuildCameraSessionPlanImpl,
    ): BuildCameraSessionPlan

    @Binds
    @Reusable
    abstract fun bindResolveAvailableModes(
        impl: ResolveAvailableModesImpl,
    ): ResolveAvailableModes

    @Binds
    @Reusable
    abstract fun bindResolveInVideoSnapshotSupport(
        impl: ResolveInVideoSnapshotSupportImpl,
    ): ResolveInVideoSnapshotSupport
}
