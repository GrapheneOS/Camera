package app.grapheneos.camera.di.camera

import app.grapheneos.camera.di.core.CameraSessionScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.di.core.MainLooperDispatcher
import app.grapheneos.camera.di.core.QrAnalysisDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@Module
@InstallIn(ActivityRetainedComponent::class)
internal class CameraSessionProvidesModule {

    @Provides
    @ActivityRetainedScoped
    @CameraSessionScope
    fun provideCameraSessionScope(
        @MainLooperDispatcher dispatcher: CoroutineDispatcher,
        lifecycle: ActivityRetainedLifecycle,
    ): CoroutineScope {
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        lifecycle.addOnClearedListener { scope.cancel() }

        return scope
    }

    @Provides
    @ActivityRetainedScoped
    @QrAnalysisDispatcher
    fun provideQrAnalysisDispatcher(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineDispatcher {
        return dispatcher.limitedParallelism(
            parallelism = 1,
            name = "QR analysis",
        )
    }
}
