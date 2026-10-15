package app.grapheneos.camera.data.camera.session

import androidx.camera.core.featuregroup.GroupableFeature
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import javax.inject.Inject

fun interface SessionProbe {
    fun isSupported(withSnapshots: Boolean, features: Set<GroupableFeature>): Boolean
}

interface InVideoSnapshotSupportResolver {

    fun resolve(
        selectedFeatures: Set<GroupableFeature>,
        probe: SessionProbe,
    ): InVideoSnapshotSupport
}

internal class InVideoSnapshotSupportResolverImpl @Inject constructor() :
    InVideoSnapshotSupportResolver {

    override fun resolve(
        selectedFeatures: Set<GroupableFeature>,
        probe: SessionProbe,
    ): InVideoSnapshotSupport {
        // When a quality feature or HDR is about to be requested, the probe has to require it
        // too: a camera can be able to run the three plain streams yet not at the
        // chosen quality, and a probe without it would keep the snapshot use case and leave
        // the conflict to the feature-group resolver -- which resolves it by dropping the
        // *quality*, with a notice blaming the camera for a quality it does support. The
        // quality wins the conflict because it is an explicit choice from the settings while
        // in-video snapshots are an implicit capability (the same reasoning as the preferred
        // feature ordering in startCamera), and giving up the snapshots is already the
        // established answer when they can't be bound at all. The plain-stream fallback keeps
        // the snapshots when the quality is unreachable even without them: dropping them would
        // buy nothing, and the resolver's "unsupported quality" notice is genuinely true then.
        return when {
            selectedFeatures.isEmpty() -> plainStreamSupport(probe)

            probe.isSupported(
                withSnapshots = true,
                features = selectedFeatures,
            ) -> InVideoSnapshotSupport.Supported

            probe.isSupported(
                withSnapshots = false,
                features = selectedFeatures,
            ) -> InVideoSnapshotSupport.Unsupported.SELECTED_VIDEO_SETTINGS

            else -> plainStreamSupport(probe)
        }
    }

    private fun plainStreamSupport(probe: SessionProbe): InVideoSnapshotSupport {
        return when {
            probe.isSupported(
                withSnapshots = true,
                features = emptySet(),
            ) -> InVideoSnapshotSupport.Supported

            else -> InVideoSnapshotSupport.Unsupported.STREAM_COMBINATION
        }
    }
}
