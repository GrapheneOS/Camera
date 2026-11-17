package app.grapheneos.camera.data.media.store

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.DocumentsContract
import app.grapheneos.camera.data.media.model.StorageFolder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafFolderSourceTest {

    private val contentResolver = mockk<ContentResolver>()

    @Test
    fun folder_ofAPrimaryVolumeTree_isAMainStorageFolder() {
        val folder = source().folder(externalStorageTree("primary%3ADCIM%2FCamera"))

        assertEquals(StorageFolder.MainStorage(path = "DCIM/Camera"), folder)
    }

    @Test
    fun folder_whosePathHoldsAColon_splitsOffOnlyTheVolume() {
        val folder = source().folder(externalStorageTree("primary%3ADCIM%2Fa%3Ab"))

        assertEquals(StorageFolder.MainStorage(path = "DCIM/a:b"), folder)
    }

    @Test
    fun folder_ofARemovableVolume_isNamedAfterTheVolume() {
        val folder = source(volumeNames = mapOf(VOLUME_ID to "SD card"))
            .folder(externalStorageTree("$VOLUME_ID%3ADCIM"))

        assertEquals(
            StorageFolder.Volume(
                name = "SD card",
                path = "DCIM",
            ),
            folder,
        )
    }

    @Test
    fun folder_ofAVolumeThatIsGone_isNamedAfterItsId() {
        val folder = source().folder(externalStorageTree("$VOLUME_ID%3ADCIM"))

        assertEquals(
            StorageFolder.Volume(
                name = VOLUME_ID,
                path = "DCIM",
            ),
            folder,
        )
    }

    @Test
    fun folder_ofAnExternalStorageIdWithoutAVolume_isTheId() {
        val folder = source().folder(externalStorageTree("DCIM"))

        assertEquals(StorageFolder.Document(name = "DCIM"), folder)
    }

    @Test
    fun folder_ofADocumentInATree_isTheTree() {
        val document = Uri.parse(
            "$EXTERNAL_STORAGE/tree/primary%3ADCIM/document/primary%3ADCIM%2FIMG.jpg",
        )

        assertEquals(StorageFolder.MainStorage(path = "DCIM"), source().folder(document))
    }

    @Test
    fun folder_ofAnotherProvider_isTheTreesDisplayName() {
        every {
            contentResolver.query(any(), any(), any<Bundle>(), any<CancellationSignal>())
        } answers {
            val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
            MatrixCursor(arrayOf(column)).apply { addRow(arrayOf("Camera")) }
        }

        val folder = source().folder(OTHER_PROVIDER_TREE)

        assertEquals(StorageFolder.Document(name = "Camera"), folder)
    }

    @Test
    fun folder_ofAnotherProviderThatCannotBeRead_isTheTreeId() {
        every {
            contentResolver.query(any(), any(), any<Bundle>(), any<CancellationSignal>())
        } throws SecurityException()

        val folder = source().folder(OTHER_PROVIDER_TREE)

        assertEquals(StorageFolder.Document(name = "root"), folder)
    }

    @Test
    fun folder_ofSomethingOtherThanATree_isNull() {
        assertNull(source().folder(Uri.parse("content://media/external/images/media/1")))
    }

    private fun source(
        volumeNames: Map<String, String> = emptyMap(),
    ): SafFolderSource {
        return SafFolderSourceImpl(
            contentResolver = contentResolver,
            storageVolumeNames = { uuid -> volumeNames[uuid] },
        )
    }

    private fun externalStorageTree(treeId: String): Uri {
        return Uri.parse("$EXTERNAL_STORAGE/tree/$treeId")
    }

    private companion object {
        const val EXTERNAL_STORAGE = "content://com.android.externalstorage.documents"
        const val VOLUME_ID = "1234-ABCD"

        val OTHER_PROVIDER_TREE: Uri = Uri.parse("content://com.example.documents/tree/root")
    }
}
