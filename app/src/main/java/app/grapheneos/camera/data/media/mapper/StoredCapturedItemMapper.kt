package app.grapheneos.camera.data.media.mapper

import androidx.core.net.toUri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.StoredCapturedItem
import javax.inject.Inject

internal interface StoredCapturedItemMapper {

    fun map(item: CapturedItem): StoredCapturedItem

    fun map(stored: StoredCapturedItem): CapturedItem?
}

internal class StoredCapturedItemMapperImpl @Inject constructor() : StoredCapturedItemMapper {

    override fun map(item: CapturedItem): StoredCapturedItem {
        return StoredCapturedItem(
            type = storedTypeOf(item.type),
            dateString = item.dateString,
            uri = item.uri.toString(),
        )
    }

    override fun map(stored: StoredCapturedItem): CapturedItem? {
        return typeOf(stored.type)?.let { type ->
            CapturedItem(
                type = type,
                dateString = stored.dateString,
                uri = stored.uri.toUri(),
            )
        }
    }

    private fun storedTypeOf(type: CapturedItemType): Int {
        return when (type) {
            CapturedItemType.IMAGE -> StoredCapturedItem.TYPE_IMAGE
            CapturedItemType.VIDEO -> StoredCapturedItem.TYPE_VIDEO
        }
    }

    private fun typeOf(storedType: Int): CapturedItemType? {
        return when (storedType) {
            StoredCapturedItem.TYPE_IMAGE -> CapturedItemType.IMAGE
            StoredCapturedItem.TYPE_VIDEO -> CapturedItemType.VIDEO
            else -> null
        }
    }
}
