package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.session.TorchControl
import app.grapheneos.camera.data.camera.session.TorchControlImpl
import app.grapheneos.camera.domain.camera.usecase.BlinkTimerLight
import app.grapheneos.camera.domain.camera.usecase.BlinkTimerLightImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
internal abstract class CameraRetainedBindsModule {

    @Binds
    @Reusable
    abstract fun bindTorchControl(
        impl: TorchControlImpl,
    ): TorchControl

    @Binds
    @Reusable
    abstract fun bindBlinkTimerLight(
        impl: BlinkTimerLightImpl,
    ): BlinkTimerLight
}
