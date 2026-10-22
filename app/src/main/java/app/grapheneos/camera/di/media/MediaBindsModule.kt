package app.grapheneos.camera.di.media

import app.grapheneos.camera.data.media.mapper.CaptureTimeMapper
import app.grapheneos.camera.data.media.mapper.CaptureTimeMapperImpl
import app.grapheneos.camera.data.media.mapper.CapturedItemNameMapper
import app.grapheneos.camera.data.media.mapper.CapturedItemNameMapperImpl
import app.grapheneos.camera.data.media.mapper.SafTreeReleaseFlagsMapper
import app.grapheneos.camera.data.media.mapper.SafTreeReleaseFlagsMapperImpl
import app.grapheneos.camera.data.media.mapper.StoredCapturedItemMapper
import app.grapheneos.camera.data.media.mapper.StoredCapturedItemMapperImpl
import app.grapheneos.camera.data.media.player.VideoPlaybackFactory
import app.grapheneos.camera.data.media.player.VideoPlaybackFactoryImpl
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CaptureOutputRepositoryImpl
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepositoryImpl
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepository
import app.grapheneos.camera.data.media.repository.CapturedItemTrashRepositoryImpl
import app.grapheneos.camera.data.media.repository.StorageSpaceRepository
import app.grapheneos.camera.data.media.repository.StorageSpaceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaBindsModule {

    @Binds
    @Reusable
    abstract fun bindCaptureOutputRepository(
        impl: CaptureOutputRepositoryImpl,
    ): CaptureOutputRepository

    @Binds
    @Reusable
    abstract fun bindCapturedItemDetailsRepository(
        impl: CapturedItemDetailsRepositoryImpl,
    ): CapturedItemDetailsRepository

    @Binds
    @Reusable
    abstract fun bindCapturedItemTrashRepository(
        impl: CapturedItemTrashRepositoryImpl,
    ): CapturedItemTrashRepository

    @Binds
    @Reusable
    abstract fun bindStorageSpaceRepository(
        impl: StorageSpaceRepositoryImpl,
    ): StorageSpaceRepository

    @Binds
    @Reusable
    abstract fun bindVideoPlaybackFactory(
        impl: VideoPlaybackFactoryImpl,
    ): VideoPlaybackFactory

    @Binds
    @Reusable
    abstract fun bindCaptureTimeMapper(
        impl: CaptureTimeMapperImpl,
    ): CaptureTimeMapper

    @Binds
    @Reusable
    abstract fun bindCapturedItemNameMapper(
        impl: CapturedItemNameMapperImpl,
    ): CapturedItemNameMapper

    @Binds
    @Reusable
    abstract fun bindStoredCapturedItemMapper(
        impl: StoredCapturedItemMapperImpl,
    ): StoredCapturedItemMapper

    @Binds
    @Reusable
    abstract fun bindSafTreeReleaseFlagsMapper(
        impl: SafTreeReleaseFlagsMapperImpl,
    ): SafTreeReleaseFlagsMapper
}
