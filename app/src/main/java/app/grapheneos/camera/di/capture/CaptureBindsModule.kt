package app.grapheneos.camera.di.capture

import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapperImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CaptureBindsModule {

    @Binds
    @Reusable
    abstract fun bindCapturedImageExifMapper(
        impl: CapturedImageExifMapperImpl,
    ): CapturedImageExifMapper
}
