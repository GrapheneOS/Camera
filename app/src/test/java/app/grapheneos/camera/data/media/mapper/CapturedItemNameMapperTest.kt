package app.grapheneos.camera.data.media.mapper

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CapturedItemNameMapperTest {

    private val mapper = CapturedItemNameMapperImpl()

    @Test
    fun anImageName_readsAsAnImageWithItsDate() {
        val item = mapper.map("IMG_20260724_153012_345.jpg", URI)

        assertEquals(CapturedItemType.IMAGE, item?.type)
        assertEquals("20260724_153012_345", item?.dateString)
        assertEquals(URI, item?.uri)
    }

    @Test
    fun aVideoName_readsAsAVideoWithItsDate() {
        val item = mapper.map("VID_20260724_153012.mp4", URI)

        assertEquals(CapturedItemType.VIDEO, item?.type)
        assertEquals("20260724_153012", item?.dateString)
    }

    @Test
    fun namesThisAppDidNotWrite_readAsNoItem() {
        listOf(
            "PXL_20260724_153012.jpg",
            "IMG_20260724_1530.jpg",
            "IMG_20260724_153012",
            "IMG_2026-07-24_153012.jpg",
            "IMG_٢٠٢٦٠٧٢٤_١٥٣٠١٢.jpg",
        ).forEach { name ->
            assertNull(name, mapper.map(name, URI))
        }
    }

    private companion object {
        val URI: Uri = Uri.parse("content://media/external/images/media/1")
    }
}
