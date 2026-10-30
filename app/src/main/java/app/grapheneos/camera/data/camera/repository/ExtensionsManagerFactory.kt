package app.grapheneos.camera.data.camera.repository

import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture

fun interface ExtensionsManagerFactory {
    fun create(provider: ProcessCameraProvider): ListenableFuture<ExtensionsManager>
}
