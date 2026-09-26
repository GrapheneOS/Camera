package app.grapheneos.camera.data.media.mapper

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import javax.inject.Inject

interface CapturedItemNameMapper {
    fun map(fileName: String, uri: Uri): CapturedItem?
}

internal class CapturedItemNameMapperImpl @Inject constructor() : CapturedItemNameMapper {

    override fun map(
        fileName: String,
        uri: Uri,
    ): CapturedItem? {
        val type = CapturedItemType.entries.firstOrNull { fileName.startsWith(it.namePrefix) }
        val dateString = type?.let { dateStringOf(fileName, it) }

        return when {
            type == null || dateString == null -> null

            else -> {
                CapturedItem(
                    type = type,
                    dateString = dateString,
                    uri = uri,
                )
            }
        }
    }

    private fun dateStringOf(
        fileName: String,
        type: CapturedItemType,
    ): String? {
        val start = type.namePrefix.length
        val end = fileName.indexOf('.', start)
        val dateString = fileName.substring(start, end.coerceAtLeast(start))

        return dateString.takeIf {
            it.length >= SHORTEST_DATE_STRING.length && it.all(::isDateStringChar)
        }
    }

    private fun isDateStringChar(char: Char): Boolean {
        return char in '0'..'9' || char == '_'
    }

    private companion object {
        private const val SHORTEST_DATE_STRING = "20220102_030405"
    }
}
