package app.grapheneos.camera.data.camera.repository

import android.content.Context
import android.util.Log
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.concurrent.thread

interface CameraProviderSource {

    fun acquireProvider(context: Context, onResult: (ProcessCameraProvider?) -> Unit)

    fun acquireExtensionsManager(
        context: Context,
        provider: ProcessCameraProvider,
        onResult: (ExtensionsManager?) -> Unit,
    )
}

internal class CameraProviderSourceImpl @Inject constructor() : CameraProviderSource {

    override fun acquireProvider(
        context: Context,
        onResult: (ProcessCameraProvider?) -> Unit,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener(
            {
                val provider = try {
                    providerFuture.get()
                } catch (exception: ExecutionException) {
                    Log.e(TAG, "Camera provider initialization failed", exception)
                    null
                }

                onResult(provider)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    override fun acquireExtensionsManager(
        context: Context,
        provider: ProcessCameraProvider,
        onResult: (ExtensionsManager?) -> Unit,
    ) {
        // Despite the name, getInstanceAsync() runs its one-time initialization body
        // synchronously on the calling thread, and that body asks the vendor's extensions
        // proxy service for each camera's advertised extensions until it finds one that has
        // any -- measured at a handful of binder round trips on the main thread during
        // startup on a Pixel 7 Pro. Call it from a short-lived thread instead; only the
        // listener has to run on the main thread, for the field write and startCamera().
        thread {
            try {
                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, provider)

                extensionsManagerFuture.addListener(
                    {
                        val extensionsManager = try {
                            extensionsManagerFuture.get()
                        } catch (exception: ExecutionException) {
                            Log.e(TAG, "Extensions manager future failed", exception)
                            null
                        }

                        onResult(extensionsManager)
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (exception: Exception) {
                // getInstanceAsync() runs its initialization synchronously (see above), so it
                // -- or addListener -- can throw right here on this background thread, where an
                // escaping exception would crash the process and, worse, leave the camera never
                // started because the listener never runs. Recover exactly as the future-failure
                // path does: report it and start the camera without extensions, on the main
                // thread.
                Log.e(TAG, "Extensions manager initialization failed", exception)
                ContextCompat.getMainExecutor(context).execute { onResult(null) }
            }
        }
    }

    private companion object {
        const val TAG = "CameraProviderSource"
    }
}
