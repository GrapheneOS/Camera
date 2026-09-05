package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Quality
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapper
import javax.inject.Inject

// CameraX resolves a preferred feature group by dropping features until what is left is a
// combination the camera actually supports, and it does so silently. This app only ever asks
// for the video quality and the stabilization that the user selected, so a dropped feature
// means a setting the UI still displays is not in effect. Say so rather than letting the
// recording quietly disagree with the settings screen.
interface ResolveDroppedVideoQuality {

    operator fun invoke(
        lensFacing: Int,
        requestedQualityFeature: GroupableFeature?,
        selected: Set<GroupableFeature>,
    ): Quality?
}

internal class ResolveDroppedVideoQualityImpl @Inject constructor(
    private val videoQualityFeatureMapper: VideoQualityFeatureMapper,
) : ResolveDroppedVideoQuality {

    // Avoids repeating an unchanged notice: startCamera() runs again on every tab switch,
    // settings change, camera flip and resume, and the outcome is usually the same each time.
    // Keyed by lens facing so that alternating between a fully-supported camera and a limited
    // one doesn't re-announce the limited camera's unchanged hardware fact on every flip: a
    // fully-satisfied bind clears only its own camera's entry, so the next divergence on that
    // camera is genuinely new information while the other camera's stays remembered.
    private val lastReported = HashMap<Int, Quality>()

    override fun invoke(
        lensFacing: Int,
        requestedQualityFeature: GroupableFeature?,
        selected: Set<GroupableFeature>,
    ): Quality? {
        // Only report a dropped quality that can be named: a message that can't say which
        // quality it means would be worse than the caller's log line.
        val dropped = requestedQualityFeature
            ?.takeIf { it !in selected }
            ?.let { videoQualityFeatureMapper.map(it) }

        // A dropped quality is the only outcome worth a toast, and it is a genuine one: CameraX
        // tries the quality on its own before it tries either stabilization, and the preflight in
        // startCamera already gave up in-video snapshots wherever that would let the quality bind,
        // so a quality reported dropped here is one this camera cannot record at in the minimal
        // configuration either. Stabilization losses are deliberately not surfaced -- besides the
        // lead's no-EIS-messaging wish, a "stabilization unsupported" message would misattribute
        // the loss, because stabilization can be crowded out by the in-video snapshot stream
        // rather than by the quality.
        if (dropped == null) {
            lastReported.remove(lensFacing)
            return null
        }

        val alreadyReported = lastReported[lensFacing] == dropped
        lastReported[lensFacing] = dropped

        return when {
            alreadyReported -> null
            else -> dropped
        }
    }
}
