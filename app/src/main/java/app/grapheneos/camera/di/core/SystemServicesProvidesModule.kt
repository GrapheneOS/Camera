package app.grapheneos.camera.di.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class SystemServicesProvidesModule {

    @Provides
    @Reusable
    fun provideDisplayManager(
        @ApplicationContext context: Context,
    ): DisplayManager {
        return context.getSystemService(DisplayManager::class.java)
    }

    @Provides
    @Reusable
    fun provideLocationManager(
        @ApplicationContext context: Context,
    ): LocationManager {
        return context.getSystemService(LocationManager::class.java)
    }
}
