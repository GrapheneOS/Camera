package app.grapheneos.camera.di.media

import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CaptureOutputRepositoryImpl
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
}
