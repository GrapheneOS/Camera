package app.grapheneos.camera.domain.gallery.mapper

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryItemsMapperTest {

    private val mapper = GalleryItemsMapperImpl()

    private val olderImage = item(CapturedItemType.IMAGE, "20231015_100000_000")
    private val video = item(CapturedItemType.VIDEO, "20231015_110000")
    private val newerImage = item(CapturedItemType.IMAGE, "20231015_120000_000")

    @Test
    fun map_putsTheNewestFirst() {
        val items = mapper.map(
            items = listOf(olderImage, newerImage, video),
            videoOnly = false,
            sessionItems = null,
        )

        assertEquals(listOf(newerImage, video, olderImage), items)
    }

    @Test
    fun map_videoOnly_leavesOutPhotos() {
        val items = mapper.map(
            items = listOf(olderImage, video, newerImage),
            videoOnly = true,
            sessionItems = null,
        )

        assertEquals(listOf(video), items)
    }

    @Test
    fun map_withSessionItems_keepsOnlyWhatTheSessionCaptured() {
        val items = mapper.map(
            items = listOf(olderImage, video, newerImage),
            videoOnly = false,
            sessionItems = listOf(olderImage, video),
        )

        assertEquals(listOf(video, olderImage), items)
    }

    @Test
    fun map_withNoSessionItems_showsNothing() {
        val items = mapper.map(
            items = listOf(olderImage, video, newerImage),
            videoOnly = false,
            sessionItems = emptyList(),
        )

        assertEquals(emptyList<CapturedItem>(), items)
    }

    private fun item(
        type: CapturedItemType,
        dateString: String,
    ): CapturedItem {
        return CapturedItem(
            type = type,
            dateString = dateString,
            uri = Uri.parse("content://media/external/images/media/$dateString"),
        )
    }
}
