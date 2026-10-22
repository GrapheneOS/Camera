package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.data.media.mapper.CaptureTimeMapperImpl
import app.grapheneos.camera.data.media.model.CameraParameters
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.data.media.model.MediaTimestamp
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturedItemDetailsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val contentResolver = mockk<ContentResolver>()

    @After
    fun resetRetriever() {
        ShadowMediaMetadataRetriever.reset()
    }

    @Test
    fun details_ofAPhotoWithExifOffsets_readsAbsoluteTimes() {
        runTest {
            serve(
                columns = MEDIA_STORE_COLUMNS,
                file = jpeg {
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2023:10:15 12:34:56")
                    setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+02:00")
                    setAttribute(ExifInterface.TAG_DATETIME, "2023:10:16 08:00:00")
                    setAttribute(ExifInterface.TAG_OFFSET_TIME, "-05:00")
                },
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertEquals(FILE_NAME, details.fileName)
            assertEquals(RELATIVE_PATH, details.relativePath)
            assertEquals(SIZE_BYTES, details.sizeBytes)
            assertEquals(Size(PHOTO_WIDTH, PHOTO_HEIGHT), details.resolution)
            assertEquals(absolute("2023-10-15T10:34:56Z"), details.created)
            assertEquals(absolute("2023-10-16T13:00:00Z"), details.modified)
        }
    }

    @Test
    fun details_ofAPhotoWithoutExifOffsets_readsWallClockTimes() {
        runTest {
            serve(
                columns = MEDIA_STORE_COLUMNS,
                file = jpeg {
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2023:10:15 12:34:56")
                    setAttribute(ExifInterface.TAG_DATETIME, "2023:10:15 12:34:56")
                },
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertEquals(wallClock("2023-10-15T12:34:56"), details.created)
            assertEquals(wallClock("2023-10-15T12:34:56"), details.modified)
        }
    }

    @Test
    fun details_ofAQuarterTurnedPhoto_swapsItsResolution() {
        runTest {
            serve(
                columns = MEDIA_STORE_COLUMNS,
                file = jpeg {
                    setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_ROTATE_90.toString(),
                    )
                },
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertEquals(Size(PHOTO_HEIGHT, PHOTO_WIDTH), details.resolution)
        }
    }

    @Test
    fun details_ofAStrippedMediaStorePhoto_fallBackToTheStoredSeconds() {
        runTest {
            serve(
                columns = MEDIA_STORE_COLUMNS + mapOf(
                    MediaColumns.DATE_ADDED to 1_697_366_096L,
                    MediaColumns.DATE_MODIFIED to 1_697_366_100L,
                ),
                file = jpeg(),
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertEquals(absolute("2023-10-15T10:34:56Z"), details.created)
            assertEquals(absolute("2023-10-15T10:35:00Z"), details.modified)
        }
    }

    @Test
    fun details_ofAStrippedDocument_takeCreationFromTheNameAndModificationFromTheDocument() {
        runTest {
            serve(
                columns = mapOf(
                    OpenableColumns.DISPLAY_NAME to FILE_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED to 1_697_366_100_000L,
                ),
                file = jpeg(),
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertNull(details.relativePath)
            assertEquals(wallClock("2023-10-15T12:34:56.789"), details.created)
            assertEquals(absolute("2023-10-15T10:35:00Z"), details.modified)
        }
    }

    @Test
    fun details_ofAPortraitVideo_swapItsResolutionAndReadItsDateAndDuration() {
        runTest {
            serve(columns = MEDIA_STORE_COLUMNS)
            videoMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to "1920",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to "1080",
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to "90",
                MediaMetadataRetriever.METADATA_KEY_DURATION to "12345",
                MediaMetadataRetriever.METADATA_KEY_DATE to "20231015T103456.000Z",
            )

            val details = detailsOf(CapturedItemType.VIDEO)

            assertEquals(Size(1080, 1920), details.resolution)
            assertEquals(12_345L, details.duration?.inWholeMilliseconds)
            assertEquals(absolute("2023-10-15T10:34:56Z"), details.created)
            assertEquals(absolute("2023-10-15T10:34:56Z"), details.modified)
        }
    }

    @Test
    fun details_ofAVideoWithAMalformedDate_keepTheRestOfItsDetails() {
        runTest {
            serve(columns = MEDIA_STORE_COLUMNS)
            videoMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION to "12345",
                MediaMetadataRetriever.METADATA_KEY_DATE to "19040101T000000",
            )

            val details = detailsOf(CapturedItemType.VIDEO)

            assertEquals(12_345L, details.duration?.inWholeMilliseconds)
            assertNull(details.modified)
        }
    }

    @Test
    fun details_ofAPhotoWithCameraExif_readTheCameraAndItsLocation() {
        runTest {
            serve(
                columns = MEDIA_STORE_COLUMNS,
                file = jpeg {
                    setAttribute(ExifInterface.TAG_MAKE, "Google")
                    setAttribute(ExifInterface.TAG_MODEL, "Pixel 8 Pro")
                    setAttribute(ExifInterface.TAG_F_NUMBER, "168/100")
                    setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/120")
                    setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "690/100")
                    setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "52")
                    setLatLong(47.5, -122.25)
                },
            )

            val details = detailsOf(CapturedItemType.IMAGE)

            assertEquals(
                CameraParameters(
                    make = "Google",
                    model = "Pixel 8 Pro",
                    fNumber = 1.68,
                    exposureSeconds = 1.0 / 120,
                    focalLengthMillimeters = 6.9,
                    iso = 52,
                ),
                details.camera,
            )
            assertEquals(47.5, requireNotNull(details.location).latitude, COORDINATE_TOLERANCE)
            assertEquals(-122.25, requireNotNull(details.location).longitude, COORDINATE_TOLERANCE)
        }
    }

    @Test
    fun details_ofAStrippedPhoto_haveNoCameraOrLocation() {
        runTest {
            serve(columns = MEDIA_STORE_COLUMNS, file = jpeg())

            val details = detailsOf(CapturedItemType.IMAGE)

            assertNull(details.camera)
            assertNull(details.location)
        }
    }

    @Test
    fun details_ofAVideoWithALocation_readItsCoordinates() {
        runTest {
            serve(columns = MEDIA_STORE_COLUMNS)
            videoMetadata(
                MediaMetadataRetriever.METADATA_KEY_LOCATION to "+47.5000-122.2500/",
            )

            val details = detailsOf(CapturedItemType.VIDEO)

            assertEquals(GeoCoordinates(latitude = 47.5, longitude = -122.25), details.location)
        }
    }

    @Test
    fun details_ofAnItemItsProviderDoesNotName_areNull() {
        runTest {
            serve(columns = mapOf(OpenableColumns.SIZE to SIZE_BYTES), file = jpeg())

            assertNull(repository().details(item(CapturedItemType.IMAGE)))
        }
    }

    @Test
    fun details_ofAnUnreadableItem_areNull() {
        runTest {
            serve(columns = MEDIA_STORE_COLUMNS)

            assertNull(repository().details(item(CapturedItemType.IMAGE)))
        }
    }

    private fun serve(columns: Map<String, Any?>, file: File? = null) {
        every {
            contentResolver.query(any(), any(), any<Bundle>(), any<CancellationSignal>())
        } answers {
            val known = secondArg<Array<String>>().filter { column -> column in columns }
            MatrixCursor(known.toTypedArray()).apply { addRow(known.map(columns::get)) }
        }
        every { contentResolver.openInputStream(any()) } answers {
            file?.inputStream() ?: throw FileNotFoundException()
        }
    }

    private fun jpeg(exif: ExifInterface.() -> Unit = {}): File {
        val file = temporaryFolder.newFile()
        val bitmap = Bitmap.createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }

        ExifInterface(file.absolutePath).apply {
            exif()
            saveAttributes()
        }

        return file
    }

    private fun videoMetadata(vararg metadata: Pair<Int, String>) {
        metadata.forEach { (key, value) ->
            ShadowMediaMetadataRetriever.addMetadata(
                DataSource.toDataSource(context, ITEM_URI),
                key,
                value,
            )
        }
    }

    private suspend fun TestScope.detailsOf(type: CapturedItemType): CapturedItemDetails {
        return requireNotNull(repository().details(item(type)))
    }

    private fun TestScope.repository(): CapturedItemDetailsRepositoryImpl {
        return CapturedItemDetailsRepositoryImpl(
            context = context,
            contentResolver = contentResolver,
            captureTimeMapper = CaptureTimeMapperImpl(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private fun item(type: CapturedItemType): CapturedItem {
        return CapturedItem(
            type = type,
            dateString = DATE_STRING,
            uri = ITEM_URI,
        )
    }

    private fun absolute(instant: String): MediaTimestamp {
        return MediaTimestamp.Absolute(Instant.parse(instant))
    }

    private fun wallClock(dateTime: String): MediaTimestamp {
        return MediaTimestamp.WallClock(LocalDateTime.parse(dateTime))
    }

    private companion object {
        const val COORDINATE_TOLERANCE = 0.000_01
        const val DATE_STRING = "20231015_123456_789"
        const val FILE_NAME = "IMG_$DATE_STRING.jpg"
        const val RELATIVE_PATH = "DCIM/Camera/"
        const val SIZE_BYTES = 2_000_000L
        const val PHOTO_WIDTH = 4
        const val PHOTO_HEIGHT = 2
        const val JPEG_QUALITY = 90

        val ITEM_URI: Uri = Uri.parse("content://media/external/images/media/1")

        val MEDIA_STORE_COLUMNS = mapOf(
            OpenableColumns.DISPLAY_NAME to FILE_NAME,
            OpenableColumns.SIZE to SIZE_BYTES,
            MediaColumns.RELATIVE_PATH to RELATIVE_PATH,
        )
    }
}
