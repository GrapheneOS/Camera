package app.grapheneos.camera.data.camera.repository

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.analyzer.QRAnalyzer
import java.util.concurrent.Executor

interface CameraSessionEnvironment {

    val sessionContext: Context

    val sessionLifecycleOwner: LifecycleOwner

    val sessionMainExecutor: Executor

    val displayRotation: Int

    val isSessionActive: Boolean

    val previewSurfaceProvider: Preview.SurfaceProvider

    fun shouldAskForLocationPermission(): Boolean

    fun createTunePlayer(): TunePlayer

    fun createQrAnalyzer(): QRAnalyzer
}
