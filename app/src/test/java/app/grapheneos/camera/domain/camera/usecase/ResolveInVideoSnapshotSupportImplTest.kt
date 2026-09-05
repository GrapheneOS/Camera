package app.grapheneos.camera.domain.camera.usecase

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.domain.camera.model.InVideoSnapshotSupport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResolveInVideoSnapshotSupportImplTest {

    private val resolve = ResolveInVideoSnapshotSupportImpl()

    @Test
    fun invoke_noQualityAskedForAndTheStreamsBind_keepsSnapshots() {
        val probe = FakeSessionProbe(supported = setOf(PLAIN_STREAMS_WITH_SNAPSHOTS))

        val support = resolve(videoQualityFeature = null, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
    }

    @Test
    fun invoke_noQualityAskedForAndTheStreamsDoNotBind_dropsSnapshots() {
        val probe = FakeSessionProbe(supported = emptySet())

        val support = resolve(videoQualityFeature = null, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.STREAM_COMBINATION, support)
    }

    @Test
    fun invoke_theQualityBindsWithSnapshots_keepsSnapshotsWithoutAskingAgain() {
        val probe = FakeSessionProbe(supported = setOf(QUALITY_WITH_SNAPSHOTS))

        val support = resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
        assertEquals(listOf(QUALITY_WITH_SNAPSHOTS), probe.asked)
    }

    @Test
    fun invoke_theQualityBindsOnlyWithoutSnapshots_givesUpTheSnapshots() {
        val probe = FakeSessionProbe(supported = setOf(QUALITY_WITHOUT_SNAPSHOTS))

        val support = resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.SELECTED_VIDEO_QUALITY, support)
    }

    @Test
    fun invoke_theQualityBindsNeitherWay_keepsSnapshotsAndLeavesTheQualityToTheResolver() {
        val probe = FakeSessionProbe(supported = setOf(PLAIN_STREAMS_WITH_SNAPSHOTS))

        val support = resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
    }

    @Test
    fun invoke_nothingBinds_dropsSnapshots() {
        val probe = FakeSessionProbe(supported = emptySet())

        val support = resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.STREAM_COMBINATION, support)
    }

    private class FakeSessionProbe(
        private val supported: Set<Question>,
    ) : SessionProbe {

        val asked = mutableListOf<Question>()

        override fun isSupported(
            withSnapshots: Boolean,
            features: Set<GroupableFeature>,
        ): Boolean {
            val question = Question(withSnapshots = withSnapshots, features = features)
            asked.add(question)

            return question in supported
        }
    }

    private data class Question(
        val withSnapshots: Boolean,
        val features: Set<GroupableFeature>,
    )

    private companion object {
        val QUALITY: GroupableFeature = GroupableFeatures.UHD_RECORDING

        val PLAIN_STREAMS_WITH_SNAPSHOTS = Question(
            withSnapshots = true,
            features = emptySet(),
        )

        val QUALITY_WITH_SNAPSHOTS = Question(
            withSnapshots = true,
            features = setOf(QUALITY),
        )

        val QUALITY_WITHOUT_SNAPSHOTS = Question(
            withSnapshots = false,
            features = setOf(QUALITY),
        )
    }
}
