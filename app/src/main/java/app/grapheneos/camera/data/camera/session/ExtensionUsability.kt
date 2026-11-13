package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.repository.ExtensionAvailabilityRepository
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ExtensionUsability {

    fun isUsable(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        key: ExtensionKey,
    ): Boolean

    fun probeUnknown(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        isActive: () -> Boolean,
        onRestart: () -> Unit,
        onSettled: () -> Unit,
    )
}

internal class ExtensionUsabilityImpl @Inject constructor(
    private val extensionAvailabilityRepository: ExtensionAvailabilityRepository,
    private val extensionProbe: ExtensionProbe,
    private val snapshotProbeCache: SnapshotProbeCache,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ExtensionUsability {

    private var probesInFlight = false

    override fun isUsable(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        key: ExtensionKey,
    ): Boolean {
        val cached = extensionAvailabilityRepository.verdict(key)

        // A background probe round (probeUnknown) may already be asking the vendor about
        // this very key. Probing inline here would run a second synchronous vendor round trip on
        // the main thread and race that round's verdict write. While a round is in flight, treat
        // the miss as "unusable for now"; the round fills the cache and the next rebind/tab refresh
        // picks up the real verdict. The inline probe stays for the not-in-flight cold case, where
        // it is the only path to a verdict.
        return cached ?: (!probesInFlight && probeAndRecord(provider, em, selector, key))
    }

    private fun probeAndRecord(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        key: ExtensionKey,
    ): Boolean {
        val verdict = extensionProbe.probe(
            provider = provider,
            em = em,
            selector = selector,
            extensionMode = key.extensionMode,
        ) ?: return false

        extensionAvailabilityRepository.record(key, usable = verdict)

        return verdict
    }

    // Refreshing the tabs must not run extension probes on the calling (main) thread: the
    // first refresh after process start needs one vendor-extender init round trip over
    // binder per advertised mode per lens, which measures at over half a second of blocked
    // main thread -- more than a hundred dropped frames -- during startup on a Pixel 7 Pro.
    // The probes run on the IO dispatcher instead (not cameraExecutor, which the
    // QR analyzer may be draining) and the tabs are built once every verdict is in, so the
    // tab bar still appears exactly once, fully formed, at the same time it used to; until
    // then swipes and taps resolve to no tab and the app simply stays in the current mode.
    // Later refreshes find the cache warm and rebuild synchronously, exactly as before.
    override fun probeUnknown(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        isActive: () -> Boolean,
        onRestart: () -> Unit,
        onSettled: () -> Unit,
    ) {
        val pending = extensionAvailabilityRepository.unprobed()
        if (pending.isEmpty()) {
            onSettled()
            return
        }

        if (probesInFlight) return
        probesInFlight = true

        applicationScope.launch(mainDispatcher) {
            val verdicts = withContext(ioDispatcher) {
                pending.associateWith { key ->
                    extensionProbe.probe(
                        provider = provider,
                        em = em,
                        selector = cameraSelectorFor(key.lensFacing),
                        extensionMode = key.extensionMode,
                    )
                }
            }

            probesInFlight = false
            if (!isActive()) return@launch

            if (!snapshotProbeCache.isProbedThrough(provider)) {
                // The camera stack was reinitialized while probing: these verdicts describe
                // vendor state that no longer exists (the same reasoning as the cache clear
                // in onCameraProviderReady). Any refresh that ran for the new provider found
                // this round still in flight and skipped scheduling, so start over for it.
                onRestart()
                return@launch
            }

            extensionAvailabilityRepository.recordProbeRound(verdicts)
            onSettled()
        }
    }
}
