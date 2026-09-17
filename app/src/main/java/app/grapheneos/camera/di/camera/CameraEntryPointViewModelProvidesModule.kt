package app.grapheneos.camera.di.camera

import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal class CameraEntryPointViewModelProvidesModule {

    @Provides
    @ViewModelScoped
    fun provideCameraEntryPoint(arguments: SavedStateHandle): CameraEntryPoint {
        return ViewfinderViewModel.entryPoint(arguments)
    }
}
