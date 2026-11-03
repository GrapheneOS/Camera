package app.grapheneos.camera.di.quicksettings

import android.content.res.Resources
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.quicksettings.screen.AppLabel
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class QuickSettingsProvidesModule {

    @Provides
    @Reusable
    fun provideAppLabel(
        resources: Resources,
    ): AppLabel {
        return AppLabel { resources.getString(R.string.app_name) }
    }
}
