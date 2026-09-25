package app.grapheneos.camera.di.core

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
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
    fun provideContentResolver(
        @ApplicationContext context: Context,
    ): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Reusable
    fun provideClipboardManager(
        @ApplicationContext context: Context,
    ): ClipboardManager {
        return context.getSystemService(ClipboardManager::class.java)
    }

    @Provides
    @Reusable
    fun provideLocationManager(
        @ApplicationContext context: Context,
    ): LocationManager {
        return context.getSystemService(LocationManager::class.java)
    }

    @Provides
    @Reusable
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager {
        return context.getSystemService(NotificationManager::class.java)
    }

    @Provides
    @Reusable
    fun provideSensorManager(
        @ApplicationContext context: Context,
    ): SensorManager {
        return context.getSystemService(SensorManager::class.java)
    }

    @Provides
    @Reusable
    fun provideVibrator(
        @ApplicationContext context: Context,
    ): Vibrator {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            }

            else -> {
                context.getSystemService(Vibrator::class.java)
            }
        }
    }
}
