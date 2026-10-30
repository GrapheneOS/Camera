package app.grapheneos.camera.data.camera.session

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.SessionConfig
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import app.grapheneos.camera.data.camera.model.SnapshotProbeKey
import javax.inject.Inject

interface SnapshotSupportProbe {

    /** Null when the camera could not be asked at all. */
    fun support(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        key: SnapshotProbeKey,
        useCases: List<UseCase>,
        snapshotUseCase: ImageCapture,
        featuresByPriority: List<GroupableFeature>,
    ): InVideoSnapshotSupport?
}

internal class SnapshotSupportProbeImpl @Inject constructor(
    private val inVideoSnapshotSupportResolver: InVideoSnapshotSupportResolver,
    private val snapshotProbeCache: SnapshotProbeCache,
) : SnapshotSupportProbe {

    // Not every camera can run video, photo and preview at once. Ask before binding rather
    // than binding and retrying without the photo use case when it throws: an
    // IllegalArgumentException from bindToLifecycle() carries no indication of which
    // constraint was violated, so the retry could not tell "this camera can't do video plus
    // photo" apart from any other misconfiguration, and would answer both by silently
    // dropping in-video snapshots. A genuine bug then looked like a missing feature. This
    // asks the specific question, and leaves unexpected exceptions to surface as failures.
    //
    // Asking costs 80-140 ms, because the camera service resolves the whole feature group to
    // answer it, so the verdict is cached (SnapshotProbeCache) and every entry into video mode
    // after the first is free.
    override fun support(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        key: SnapshotProbeKey,
        useCases: List<UseCase>,
        snapshotUseCase: ImageCapture,
        featuresByPriority: List<GroupableFeature>,
    ): InVideoSnapshotSupport? {
        return snapshotProbeCache[key] ?: probe(
            provider = provider,
            cameraSelector = cameraSelector,
            useCases = useCases,
            snapshotUseCase = snapshotUseCase,
            featuresByPriority = featuresByPriority,
        )?.also { support -> snapshotProbeCache[key] = support }
    }

    private fun probe(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        useCases: List<UseCase>,
        snapshotUseCase: ImageCapture,
        featuresByPriority: List<GroupableFeature>,
    ): InVideoSnapshotSupport? {
        snapshotProbeCache.recordProbe()

        val cameraInfo = try {
            provider.getCameraInfo(cameraSelector)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Failed to query camera info", exception)
            return null
        }

        return inVideoSnapshotSupportResolver.resolve(
            featuresByPriority = featuresByPriority,
            probe = { withSnapshots, features ->
                val probedUseCases = when {
                    withSnapshots -> useCases
                    else -> useCases - snapshotUseCase
                }

                cameraInfo.isSessionConfigSupported(
                    SessionConfig(
                        useCases = probedUseCases,
                        requiredFeatureGroup = features,
                    )
                )
            },
        )
    }

    private companion object {
        const val TAG = "SnapshotSupportProbe"
    }
}
