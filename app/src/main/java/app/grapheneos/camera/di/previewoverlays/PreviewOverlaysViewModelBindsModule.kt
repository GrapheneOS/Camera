package app.grapheneos.camera.di.previewoverlays

import app.grapheneos.camera.ui.previewoverlays.screen.delegate.FocusDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.FocusDelegateImpl
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class PreviewOverlaysViewModelBindsModule {

    @Binds
    @ViewModelScoped
    abstract fun bindFocusDelegate(
        impl: FocusDelegateImpl,
    ): FocusDelegate

    @Binds
    @ViewModelScoped
    abstract fun bindLevelDelegate(
        impl: LevelDelegateImpl,
    ): LevelDelegate
}
