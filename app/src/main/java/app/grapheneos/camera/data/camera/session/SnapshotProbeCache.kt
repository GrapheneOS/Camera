package app.grapheneos.camera.data.camera.session

import androidx.annotation.VisibleForTesting
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import app.grapheneos.camera.data.camera.model.SnapshotProbeKey
import javax.inject.Inject

interface SnapshotProbeCache {

    val probeCount: Int

    fun isProbedThrough(provider: ProcessCameraProvider): Boolean

    fun adopt(provider: ProcessCameraProvider)

    operator fun get(key: SnapshotProbeKey): InVideoSnapshotSupport?

    operator fun set(key: SnapshotProbeKey, support: InVideoSnapshotSupport)

    fun recordProbe()

    @VisibleForTesting
    fun clear()
}

internal class SnapshotProbeCacheImpl @Inject constructor() : SnapshotProbeCache {

    // A cache hit and a repeated probe reach the same verdict, so this is the only thing that
    // tells them apart from the outside.
    override var probeCount: Int = 0
        private set

    private val verdicts = HashMap<SnapshotProbeKey, InVideoSnapshotSupport>()

    // The provider the verdicts above were probed through. A different instance means the
    // camera stack was reinitialized and none of them describe it any more.
    private var probedProvider: ProcessCameraProvider? = null

    override fun isProbedThrough(provider: ProcessCameraProvider): Boolean {
        return probedProvider === provider
    }

    override fun adopt(provider: ProcessCameraProvider) {
        verdicts.clear()
        probedProvider = provider
    }

    override fun get(key: SnapshotProbeKey): InVideoSnapshotSupport? {
        return verdicts[key]
    }

    override fun set(key: SnapshotProbeKey, support: InVideoSnapshotSupport) {
        verdicts[key] = support
    }

    override fun recordProbe() {
        probeCount++
    }

    override fun clear() {
        verdicts.clear()
        probeCount = 0
    }
}
