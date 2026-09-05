package app.grapheneos.camera.di.camera

import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQualityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal abstract class CameraSessionBindsModule {

    @Binds
    @ActivityScoped
    abstract fun bindResolveDroppedVideoQuality(
        impl: ResolveDroppedVideoQualityImpl,
    ): ResolveDroppedVideoQuality
}
