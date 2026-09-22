package app.grapheneos.camera.di.capture

import app.grapheneos.camera.domain.capture.usecase.CaptureImage
import app.grapheneos.camera.domain.capture.usecase.CaptureImageImpl
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImage
import app.grapheneos.camera.domain.capture.usecase.CapturePreviewImageImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class CaptureRetainedBindsModule {

    @Binds
    @Reusable
    abstract fun bindCaptureImage(
        impl: CaptureImageImpl,
    ): CaptureImage

    @Binds
    @Reusable
    abstract fun bindCapturePreviewImage(
        impl: CapturePreviewImageImpl,
    ): CapturePreviewImage
}
