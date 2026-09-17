package app.grapheneos.camera.di.camera

import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQualityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class CameraViewModelBindsModule {

    @Binds
    @ViewModelScoped
    abstract fun bindResolveDroppedVideoQuality(
        impl: ResolveDroppedVideoQualityImpl,
    ): ResolveDroppedVideoQuality
}
