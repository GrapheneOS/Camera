package app.grapheneos.camera.ui.gallery.screen.mapper

import android.net.Uri
import app.grapheneos.camera.data.media.mapper.CaptureTimeMapperImpl
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryUiStateMapperTest {

    private val mapper: GalleryUiStateMapper = GalleryUiStateMapperImpl(CaptureTimeMapperImpl())
    private val state = GalleryState(items = listOf(NEWER, OLDER))

    @Test
    fun anUpdateThatKeepsTheItems_keepsThePages() {
        val pages = mapper.map(state).pager.pages
        val loaded = mapper.map(state.copy(previews = mapOf(NEWER to GalleryPreview.Unavailable)))

        assertSame(pages, loaded.pager.pages)
    }

    @Test
    fun newItems_rebuildThePages() {
        val pages = mapper.map(state).pager.pages
        val reloaded = mapper.map(state.copy(items = listOf(OLDER)))

        assertNotSame(pages, reloaded.pager.pages)
        assertEquals(listOf(OLDER.uri.toString()), reloaded.pager.pages.map { it.key })
    }

    @Test
    fun previews_areKeyedByPage() {
        val previews = mapOf(NEWER to GalleryPreview.Unavailable)
        val pager = mapper.map(state.copy(previews = previews)).pager

        assertEquals(mapOf(NEWER.uri.toString() to GalleryPreview.Unavailable), pager.previews)
    }

    @Test
    fun anHdrPhoto_isShownInHdrOnlyOnItsPage() {
        val hdr = GalleryPreview.Loaded(
            image = mockk(),
            isHdr = true,
        )
        val previews = mapOf(NEWER to hdr)

        assertTrue(mapper.map(state.copy(previews = previews)).showsHdr)
        assertFalse(mapper.map(state.copy(previews = previews, currentIndex = 1)).showsHdr)
    }

    private companion object {
        val NEWER = image("20231016_120000_000")
        val OLDER = image("20231015_120000_000")

        fun image(dateString: String): CapturedItem {
            return CapturedItem(
                type = CapturedItemType.IMAGE,
                dateString = dateString,
                uri = Uri.parse("content://media/external/images/media/$dateString"),
            )
        }
    }
}
