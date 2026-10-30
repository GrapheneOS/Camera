package app.grapheneos.camera.data.camera.session

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapper
import app.grapheneos.camera.data.core.model.ExtensionMode
import javax.inject.Inject

interface ExtensionProbe {

    fun probe(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        extensionMode: ExtensionMode,
    ): Boolean?
}

internal class ExtensionProbeImpl @Inject constructor(
    private val cameraXConstantsMapper: CameraXConstantsMapper,
) : ExtensionProbe {

    // ExtensionsManager.isExtensionAvailable() answers from CameraExtensionCharacteristics'
    // static advertisement data. Actually *binding* an advertised extension additionally makes
    // CameraX initialize a Camera2ExtensionsVendorExtender, which calls into the vendor's
    // advanced extender over binder. Some vendors advertise a mode there and then throw from
    // that init - Pixels raise "Framework size list map not supported in pixel path"
    // - which used to kill the process from inside bindToLifecycle(). CameraX 1.6 removed the
    // legacy OEM extender path, so there is no longer a working fallback on those devices and the
    // only safe option is to stop offering the mode.
    //
    // getCameraInfo() performs exactly the same vendor init that bindToLifecycle() does, so it is
    // a faithful probe. Verdicts are cached (ExtensionAvailabilityRepository) because every step of
    // the probe -- including the availability query, see probe -- costs a binder round
    // trip. A negative verdict is only cached when the failure is known to be persistent: caching
    // a transient probe failure would make the mode's tab vanish for the rest of the process
    // lifetime over a condition that clears seconds later.
    //
    // The cache is read and written on the main thread only, which is also what keeps the two
    // activities that can share it (a secure session over a running one) from racing each other.
    // probe() itself also runs on the probe thread that ExtensionUsability.probeUnknown() spawns,
    // but its results come back through the main executor.
    //
    // true/false is a verdict that is safe to cache; null is a failure that may be transient and
    // must not be (see ExtensionAvailabilityRepository). The provider and manager are parameters
    // rather than the fields because this also runs off the main thread, where the fields could
    // be swapped out mid-probe.
    @Suppress("TooGenericExceptionCaught")
    override fun probe(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        extensionMode: ExtensionMode,
    ): Boolean? {
        // What the vendor advertises is as static as the vendor init verdict below, so a
        // negative answer is cached the same way -- notably, isExtensionAvailable() is *not*
        // the cheap in-process lookup it appears to be: on Android 17 every call costs a round
        // trip to the vendor's extensions proxy service, which is exactly the kind of work this
        // probe exists to keep off the main thread (see ExtensionUsability.probeUnknown). It sits
        // inside the try below so that a transient failure of that round trip is treated like any
        // other -- returning null to retry later -- rather than propagating out: on the background
        // probe thread an escaping exception would strand the round with probesInFlight still
        // set, blocking every later tab refresh.
        val cameraXExtensionMode = cameraXConstantsMapper.map(extensionMode)

        return try {
            when {
                !em.isExtensionAvailable(selector, cameraXExtensionMode) -> false
                else -> {
                    provider.getCameraInfo(
                        em.getExtensionEnabledCameraSelector(selector, cameraXExtensionMode)
                    )
                    true
                }
            }
        } catch (exception: UnsupportedOperationException) {
            // The signature of a vendor extender that advertises the mode and then throws from
            // its own init (Pixels: "Framework size list map not supported in pixel path").
            // Nothing about it changes within a process lifetime, so this verdict is safe to
            // remember.
            Log.w(TAG, "Extension mode $extensionMode is advertised but unusable here", exception)
            false
        } catch (exception: Exception) {
            // Anything else may be transient — the camera service restarting, the camera briefly
            // held by another process. Fail this probe but leave the cache alone so the mode is
            // offered again once the underlying condition clears.
            Log.w(TAG, "Probing extension mode $extensionMode failed, will retry later", exception)
            null
        }
    }

    private companion object {
        const val TAG = "ExtensionProbe"
    }
}
