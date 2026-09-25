package app.grapheneos.camera.data.camera.session

import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.GroupableFeatures
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InVideoSnapshotSupportResolverTest {

    private val resolver = InVideoSnapshotSupportResolverImpl()

    private val probe = mockk<SessionProbe>()

    @Before
    fun setUp() {
        every { probe.isSupported(withSnapshots = any(), features = any()) } returns false
    }

    @Test
    fun resolve_noQualityAskedForAndTheStreamsBind_keepsSnapshots() {
        supports(withSnapshots = true, features = emptySet())

        val support = resolver.resolve(videoQualityFeature = null, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
    }

    @Test
    fun resolve_noQualityAskedForAndTheStreamsDoNotBind_dropsSnapshots() {
        val support = resolver.resolve(videoQualityFeature = null, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.STREAM_COMBINATION, support)
    }

    @Test
    fun resolve_theQualityBindsWithSnapshots_keepsSnapshotsWithoutAskingAgain() {
        supports(withSnapshots = true, features = setOf(QUALITY))

        val support = resolver.resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
        verify(exactly = 1) { probe.isSupported(withSnapshots = true, features = setOf(QUALITY)) }
        confirmVerified(probe)
    }

    @Test
    fun resolve_theQualityBindsOnlyWithoutSnapshots_givesUpTheSnapshots() {
        supports(withSnapshots = false, features = setOf(QUALITY))

        val support = resolver.resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.SELECTED_VIDEO_QUALITY, support)
    }

    @Test
    fun resolve_theQualityBindsNeitherWay_keepsSnapshotsAndLeavesTheQualityToTheResolver() {
        supports(withSnapshots = true, features = emptySet())

        val support = resolver.resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Supported, support)
    }

    @Test
    fun resolve_nothingBinds_dropsSnapshots() {
        val support = resolver.resolve(videoQualityFeature = QUALITY, probe = probe)

        assertEquals(InVideoSnapshotSupport.Unsupported.STREAM_COMBINATION, support)
    }

    private fun supports(withSnapshots: Boolean, features: Set<GroupableFeature>) {
        every { probe.isSupported(withSnapshots = withSnapshots, features = features) } returns true
    }

    private companion object {
        val QUALITY: GroupableFeature = GroupableFeatures.UHD_RECORDING
    }
}
