package app.grapheneos.camera.di.capture

import app.grapheneos.camera.domain.capture.CapturedImagePipeline
import app.grapheneos.camera.domain.capture.CapturedImagePipelineImpl
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapperImpl
import app.grapheneos.camera.domain.capture.usecase.DeleteStalePendingRecordings
import app.grapheneos.camera.domain.capture.usecase.DeleteStalePendingRecordingsImpl
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImage
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImageImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CaptureBindsModule {

    @Binds
    @Reusable
    abstract fun bindCapturedImageExifMapper(
        impl: CapturedImageExifMapperImpl,
    ): CapturedImageExifMapper

    @Binds
    @Reusable
    abstract fun bindStoreCapturedImage(
        impl: StoreCapturedImageImpl,
    ): StoreCapturedImage

    @Binds
    @Singleton
    abstract fun bindCapturedImagePipeline(
        impl: CapturedImagePipelineImpl,
    ): CapturedImagePipeline

    @Binds
    @Reusable
    abstract fun bindDeleteStalePendingRecordings(
        impl: DeleteStalePendingRecordingsImpl,
    ): DeleteStalePendingRecordings
}
