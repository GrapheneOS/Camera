package app.grapheneos.camera.data.camera.session

import android.content.Context
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

interface CameraSessionEnvironment {

    val sessionContext: Context

    val sessionLifecycleOwner: LifecycleOwner

    val sessionMainExecutor: Executor

    val displayRotation: Int

    val isSessionActive: Boolean

    val previewSurfaceProvider: Preview.SurfaceProvider

    val previewMeteringPointFactory: MeteringPointFactory

    fun shouldAskForLocationPermission(): Boolean

    fun createQrAnalyzer(): QrCodeAnalyzer
}
