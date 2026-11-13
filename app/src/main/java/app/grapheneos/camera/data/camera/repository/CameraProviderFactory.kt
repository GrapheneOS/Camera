package app.grapheneos.camera.data.camera.repository

import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture

fun interface CameraProviderFactory {
    fun create(): ListenableFuture<ProcessCameraProvider>
}
