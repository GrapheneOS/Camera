package app.grapheneos.camera.data.camera.session

import androidx.camera.core.featuregroup.GroupableFeature
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import javax.inject.Inject

fun interface SessionProbe {
    fun isSupported(withSnapshots: Boolean, features: Set<GroupableFeature>): Boolean
}

interface InVideoSnapshotSupportResolver {

    /**
     * Decides whether in-video snapshots can stay in a video session.
     *
     * [featuresByPriority] are the features the session will request, highest priority first:
     * the video quality, then HDR. They are checked together first, then without the last one,
     * and so on: the order in which the feature group gives them up. For each set:
     * - it binds with snapshots: the snapshots stay;
     * - it binds only without snapshots: the snapshots are dropped, because the user chose the
     *   quality and HDR, and the snapshots are just a default;
     * - it binds neither way: the next, smaller set is checked.
     *
     * When no set binds, the snapshots stay if the plain streams bind with them: the camera
     * can't record the selection either way, so dropping them would not help.
     *
     * Without these checks the snapshots would stay on a camera that can't keep them at the
     * chosen quality, and CameraX would drop the quality instead, telling the user that the
     * camera doesn't support it.
     */
    fun resolve(
        featuresByPriority: List<GroupableFeature>,
        probe: SessionProbe,
    ): InVideoSnapshotSupport
}

internal class InVideoSnapshotSupportResolverImpl @Inject constructor() :
    InVideoSnapshotSupportResolver {

    override fun resolve(
        featuresByPriority: List<GroupableFeature>,
        probe: SessionProbe,
    ): InVideoSnapshotSupport {
        val featureSets = (featuresByPriority.size downTo 1).map { count ->
            featuresByPriority.take(count).toSet()
        }

        return featureSets.firstNotNullOfOrNull {
            support(it, probe)
        } ?: plainStreamSupport(probe)
    }

    private fun support(
        features: Set<GroupableFeature>,
        probe: SessionProbe,
    ): InVideoSnapshotSupport? {
        return when {
            probe.isSupported(
                withSnapshots = true,
                features = features,
            ) -> InVideoSnapshotSupport.Supported

            probe.isSupported(
                withSnapshots = false,
                features = features,
            ) -> InVideoSnapshotSupport.Unsupported.SELECTED_VIDEO_SETTINGS

            else -> null
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
