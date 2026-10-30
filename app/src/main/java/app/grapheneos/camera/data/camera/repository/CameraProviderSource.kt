package app.grapheneos.camera.data.camera.repository

import android.util.Log
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

interface CameraProviderSource {

    fun acquireProvider(
        onResult: (ProcessCameraProvider?) -> Unit,
    )

    fun acquireExtensionsManager(
        provider: ProcessCameraProvider,
        onResult: (ExtensionsManager?) -> Unit,
    )
}

internal class CameraProviderSourceImpl @Inject constructor(
    private val cameraProviderFactory: CameraProviderFactory,
    private val extensionsManagerFactory: ExtensionsManagerFactory,
    @MainImmediateDispatcher mainDispatcher: CoroutineDispatcher,
) : CameraProviderSource {

    private val mainExecutor: Executor = mainDispatcher.asExecutor()

    override fun acquireProvider(
        onResult: (ProcessCameraProvider?) -> Unit,
    ) {
        val providerFuture = cameraProviderFactory.create()
        val deliverProvider = {
            val provider = try {
                providerFuture.get()
            } catch (exception: ExecutionException) {
                Log.e(TAG, "Camera provider initialization failed", exception)
                null
            }

            onResult(provider)
        }

        providerFuture.addListener(deliverProvider, mainExecutor)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun acquireExtensionsManager(
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
                val extensionsManagerFuture = extensionsManagerFactory.create(provider)
                val deliverExtensionsManager = {
                    val extensionsManager = try {
                        extensionsManagerFuture.get()
                    } catch (exception: ExecutionException) {
                        Log.e(TAG, "Extensions manager future failed", exception)
                        null
                    }

                    onResult(extensionsManager)
                }

                extensionsManagerFuture.addListener(deliverExtensionsManager, mainExecutor)
            } catch (exception: Exception) {
                // getInstanceAsync() runs its initialization synchronously (see above), so it
                // -- or addListener -- can throw right here on this background thread, where an
                // escaping exception would crash the process and, worse, leave the camera never
                // started because the listener never runs. Recover exactly as the future-failure
                // path does: report it and start the camera without extensions, on the main
                // thread.
                Log.e(TAG, "Extensions manager initialization failed", exception)
                mainExecutor.execute { onResult(null) }
            }
        }
    }

    private companion object {
        const val TAG = "CameraProviderSource"
    }
}
