package app.grapheneos.camera.di.preferences

import android.content.Context
import android.content.SharedPreferences
import app.grapheneos.camera.data.core.store.commonPreferences
import app.grapheneos.camera.data.core.store.mediaPreferences
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.di.core.SessionPreferences
import app.grapheneos.camera.ui.activities.SecureActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal class PreferencesProvidesModule {

    @Provides
    @ActivityScoped
    @SessionPreferences
    fun provideSessionPreferences(
        @ActivityContext context: Context,
    ): SharedPreferences {
        return commonPreferences(
            context = context,
            ephemeral = context is SecureActivity,
        )
    }

    @Provides
    @ActivityScoped
    @DurablePreferences
    fun provideDurablePreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        return mediaPreferences(context)
    }
}
