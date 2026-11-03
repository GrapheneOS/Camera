package app.grapheneos.camera.di.camera

import android.content.Context
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.repository.CameraProviderFactory
import app.grapheneos.camera.data.camera.repository.ExtensionsManagerFactory
import app.grapheneos.camera.data.camera.session.RecordingPreparer
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class CameraXProvidesModule {

    @Provides
    @Reusable
    fun provideCameraProviderFactory(
        @ApplicationContext context: Context,
    ): CameraProviderFactory {
        return CameraProviderFactory { ProcessCameraProvider.getInstance(context) }
    }

    @Provides
    @Reusable
    fun provideExtensionsManagerFactory(
        @ApplicationContext context: Context,
    ): ExtensionsManagerFactory {
        return ExtensionsManagerFactory { provider ->
            ExtensionsManager.getInstanceAsync(context, provider)
        }
    }

    @Provides
    @Reusable
    fun provideRecordingPreparer(
        @ApplicationContext context: Context,
    ): RecordingPreparer {
        return RecordingPreparer { recorder, outputOptions ->
            recorder.prepareRecording(context, outputOptions)
        }
    }
}
