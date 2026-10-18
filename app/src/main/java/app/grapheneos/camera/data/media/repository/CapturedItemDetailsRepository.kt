package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.data.media.mapper.CaptureTimeMapper
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.di.core.IoDispatcher
import app.grapheneos.camera.util.getLongOrNull
import app.grapheneos.camera.util.getStringOrNull
import app.grapheneos.camera.util.queryFirstRow
import app.grapheneos.camera.util.queryFirstRowOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal interface CapturedItemDetailsRepository {

    suspend fun details(item: CapturedItem): CapturedItemDetails?
}

internal class CapturedItemDetailsRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val captureTimeMapper: CaptureTimeMapper,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapturedItemDetailsRepository {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun details(item: CapturedItem): CapturedItemDetails? {
        return withContext(ioDispatcher) {
            try {
                readDetails(item)
            } catch (e: Exception) {
                Log.d(TAG, "unable to obtain the details of ${item.uiName()}", e)
                null
            }
        }
    }

    private fun readDetails(item: CapturedItem): CapturedItemDetails? {
        val file = contentResolver.queryFirstRow(
            uri = item.uri,
            projection = FILE_PROJECTION,
            read = ::readFileColumns,
        ) ?: return null
        val media = when (item.type) {
            CapturedItemType.IMAGE -> readImage(item.uri)
            CapturedItemType.VIDEO -> readVideo(item.uri)
        }
        val stored by lazy { queryStoredTimestamps(item.uri) }

        return CapturedItemDetails(
            fileName = file.name,
            relativePath = file.relativePath,
            sizeBytes = file.sizeBytes,
            resolution = media.resolution,
            duration = media.duration,
            created = media.created
                ?: stored.created
                ?: captureTimeMapper.map(item)?.let(MediaTimestamp::WallClock),
            modified = media.modified ?: stored.modified,
        )
    }

    private fun readFileColumns(cursor: Cursor): FileColumns? {
        val name = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME) ?: return null

        return FileColumns(
            name = name,
            relativePath = cursor.getStringOrNull(MediaColumns.RELATIVE_PATH),
            sizeBytes = cursor.getLongOrNull(OpenableColumns.SIZE),
        )
    }

    private fun readImage(uri: Uri): MediaMetadata {
        val exif = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )

        return MediaMetadata(
            resolution = readImageResolution(uri, orientation),
            duration = null,
            created = exif?.timestamp(
                dateTimeTag = ExifInterface.TAG_DATETIME_ORIGINAL,
                offsetTag = ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ),
            modified = exif?.timestamp(
                dateTimeTag = ExifInterface.TAG_DATETIME,
                offsetTag = ExifInterface.TAG_OFFSET_TIME,
            ),
        )
    }

    private fun readImageResolution(
        uri: Uri,
        orientation: Int?,
    ): Size? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight

        return when {
            width <= 0 || height <= 0 -> null
            orientation in QUARTER_TURNED_ORIENTATIONS -> Size(height, width)
            else -> Size(width, height)
        }
    }

    private fun ExifInterface.timestamp(
        dateTimeTag: String,
        offsetTag: String,
    ): MediaTimestamp? {
        val dateTime = getAttribute(dateTimeTag) ?: return null
        val offset = getAttribute(offsetTag)

        return parseTimestamp(dateTime) {
            val local = LocalDateTime.parse(dateTime, EXIF_DATE_TIME_FORMAT)
            when (offset) {
                null -> MediaTimestamp.WallClock(local)
                else -> MediaTimestamp.Absolute(local.toInstant(ZoneOffset.of(offset)))
            }
        }
    }

    private fun parseTimestamp(
        text: String,
        parse: () -> MediaTimestamp,
    ): MediaTimestamp? {
        return try {
            parse()
        } catch (e: DateTimeException) {
            Log.d(TAG, "unparseable date: $text", e)
            null
        }
    }

    private fun readVideo(uri: Uri): MediaMetadata {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)

            val recorded = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?.let { date ->
                    parseTimestamp(date) {
                        val utc = LocalDateTime.parse(date, VIDEO_DATE_FORMAT)
                        MediaTimestamp.Absolute(utc.toInstant(ZoneOffset.UTC))
                    }
                }

            MediaMetadata(
                resolution = readVideoResolution(retriever),
                duration = retriever
                    .extractPositiveLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.milliseconds,
                created = recorded,
                modified = recorded,
            )
        }
    }

    private fun readVideoResolution(retriever: MediaMetadataRetriever): Size? {
        val width = retriever.extractPositiveLong(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
        )
        val height = retriever.extractPositiveLong(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
        )
        val rotation = retriever.extractPositiveLong(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
        )

        // A track holds its frames as encoded plus the rotation a player has to apply, so a
        // recording taken in portrait is stored landscape
        return when {
            width == null || height == null -> null
            rotation in QUARTER_TURN_DEGREES -> Size(height.toInt(), width.toInt())
            else -> Size(width.toInt(), height.toInt())
        }
    }

    private fun MediaMetadataRetriever.extractPositiveLong(key: Int): Long? {
        return extractMetadata(key)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
    }

    /**
     * Best-effort timestamps for media whose Exif has been stripped, which is the default.
     * MediaStore and the Storage Access Framework disagree both on the column names and on the
     * unit, and each provider rejects the columns it does not know, so the two are queried
     * separately and every failure just leaves the value unknown.
     */
    private fun queryStoredTimestamps(uri: Uri): StoredTimestamps {
        val mediaStore = contentResolver.queryFirstRowOrNull(
            uri = uri,
            projection = MEDIA_STORE_TIMESTAMP_PROJECTION,
        ) { cursor ->
            StoredTimestamps(
                created = cursor.getTimestamp(
                    column = MediaColumns.DATE_ADDED,
                    toInstant = Instant::ofEpochSecond,
                ),
                modified = cursor.getTimestamp(
                    column = MediaColumns.DATE_MODIFIED,
                    toInstant = Instant::ofEpochSecond,
                ),
            )
        }
        val documentModified by lazy {
            contentResolver.queryFirstRowOrNull(uri, DOCUMENT_TIMESTAMP_PROJECTION) { cursor ->
                cursor.getTimestamp(
                    column = DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    toInstant = Instant::ofEpochMilli,
                )
            }
        }

        return StoredTimestamps(
            created = mediaStore?.created,
            modified = mediaStore?.modified ?: documentModified,
        )
    }

    // 0 stands for "not set"
    private fun Cursor.getTimestamp(
        column: String,
        toInstant: (Long) -> Instant,
    ): MediaTimestamp? {
        return getLongOrNull(column)
            ?.takeIf { it > 0 }
            ?.let { MediaTimestamp.Absolute(toInstant(it)) }
    }

    private data class FileColumns(
        val name: String,
        val relativePath: String?,
        val sizeBytes: Long?,
    )

    private data class MediaMetadata(
        val resolution: Size?,
        val duration: Duration?,
        val created: MediaTimestamp?,
        val modified: MediaTimestamp?,
    )

    private data class StoredTimestamps(
        val created: MediaTimestamp?,
        val modified: MediaTimestamp?,
    )

    private companion object {
        const val TAG = "CapturedItemDetails"

        val EXIF_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
        val VIDEO_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US)

        val QUARTER_TURN_DEGREES = setOf(90L, 270L)
        val QUARTER_TURNED_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )

        val FILE_PROJECTION = arrayOf(
            MediaColumns.RELATIVE_PATH,
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )
        val MEDIA_STORE_TIMESTAMP_PROJECTION = arrayOf(
            MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED,
        )
        val DOCUMENT_TIMESTAMP_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
