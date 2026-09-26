package app.grapheneos.camera.data.media.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class CapturedItem(
    val type: CapturedItemType,
    val dateString: String,
    val uri: Uri,
) : Parcelable {

    fun mimeType(): String {
        return type.mimeType
    }

    fun uiName(): String {
        return type.namePrefix + dateString
    }

    override fun hashCode(): Int {
        return dateString.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CapturedItem && dateString == other.dateString
    }
}
