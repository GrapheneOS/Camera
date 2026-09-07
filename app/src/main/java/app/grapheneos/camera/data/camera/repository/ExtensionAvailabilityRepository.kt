package app.grapheneos.camera.data.camera.repository

import androidx.camera.extensions.ExtensionMode
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.core.model.CameraMode
import javax.inject.Inject

interface ExtensionAvailabilityRepository {

    fun verdict(key: ExtensionKey): Boolean?

    fun record(key: ExtensionKey, usable: Boolean)

    fun recordProbeRound(verdicts: Map<ExtensionKey, Boolean?>)

    // The cache keys availableModes() needs a verdict for that only a vendor probe can answer.
    // Deliberately a pure cache check: even asking whether a mode is advertised costs a vendor
    // proxy round trip (see probeExtension), so the probe round has to answer that too.
    fun unprobed(): List<ExtensionKey>

    fun clear()
}

internal class ExtensionAvailabilityRepositoryImpl @Inject constructor() :
    ExtensionAvailabilityRepository {

    // Whether a vendor extension is usable, keyed by lens facing and extension mode (see
    // probeExtension). A verdict describes the device rather than any one activity and costs
    // binder round trips to reach, so it is kept for the process: every lock screen launch used
    // to re-probe what the session underneath it had already answered.
    private val usability = HashMap<ExtensionKey, Boolean>()

    override fun verdict(key: ExtensionKey): Boolean? {
        return usability[key]
    }

    override fun record(key: ExtensionKey, usable: Boolean) {
        usability[key] = usable
    }

    override fun recordProbeRound(verdicts: Map<ExtensionKey, Boolean?>) {
        for ((key, verdict) in verdicts) {
            // Only fill in keys that are still unprobed. A bind failure that ran while this round
            // was in flight may have blacklisted the mode (record(key, usable = false)); that
            // verdict is fresher than this probe's and must not be overwritten -- otherwise a mode
            // that just failed to bind would be offered again.
            if (verdict != null && usability[key] == null) {
                usability[key] = verdict
            }
        }
    }

    override fun unprobed(): List<ExtensionKey> {
        val result = arrayListOf<ExtensionKey>()

        for (mode in CameraMode.entries) {
            if (mode.extensionMode == ExtensionMode.NONE) continue

            for (key in ExtensionKey.onBothLenses(mode.extensionMode)) {
                if (usability[key] == null) {
                    result.add(key)
                }
            }
        }

        return result
    }

    override fun clear() {
        usability.clear()
    }
}
