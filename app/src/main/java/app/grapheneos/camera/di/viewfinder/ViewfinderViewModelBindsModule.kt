package app.grapheneos.camera.di.viewfinder

import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegateImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
internal abstract class ViewfinderViewModelBindsModule {

    @Binds
    abstract fun bindViewfinderSettingsDelegate(
        impl: ViewfinderSettingsDelegateImpl,
    ): ViewfinderSettingsDelegate
}
