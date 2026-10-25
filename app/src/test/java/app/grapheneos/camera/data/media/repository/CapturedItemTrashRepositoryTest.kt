package app.grapheneos.camera.data.media.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.store.MediaPrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CapturedItemTrashRepositoryTest {

    private val contentResolver = mockk<ContentResolver> {
        every { update(any(), any(), any(), any()) } returns 1
        every { delete(any(), any(), any()) } returns 1
    }

    @Test
    fun restore_whenTheCallerIsCancelled_stillRestoresAndForgetsTheItem() {
        runTest {
            val mediaPrefs = mediaPrefsOf(FIRST)
            val repository = repository(mediaPrefs)

            cancelTheCallerOf { repository.restore(item(FIRST)) }

            verify { contentResolver.update(FIRST, match { trashFlagOf(it) == 0 }, null, null) }
            assertEquals(emptyList<String>(), mediaPrefs.data.first().trashedItems)
        }
    }

    @Test
    fun trash_whenTheCallerIsCancelled_stillTrashesAndRecordsTheItem() {
        runTest {
            val mediaPrefs = mediaPrefsOf()
            val repository = repository(mediaPrefs)

            cancelTheCallerOf { repository.trash(item(FIRST)) }

            verify { contentResolver.update(FIRST, match { trashFlagOf(it) == 1 }, null, null) }
            assertEquals(listOf(FIRST.toString()), mediaPrefs.data.first().trashedItems)
        }
    }

    @Test
    fun deleteLeftovers_deletesOnlyWhatIsStillTrashed() {
        runTest {
            val mediaPrefs = mediaPrefsOf(FIRST, SECOND)

            repository(mediaPrefs).deleteLeftovers()

            verify { contentResolver.delete(FIRST, STILL_TRASHED, null) }
            verify { contentResolver.delete(SECOND, STILL_TRASHED, null) }
            assertEquals(emptyList<String>(), mediaPrefs.data.first().trashedItems)
        }
    }

    private fun TestScope.cancelTheCallerOf(change: suspend () -> Unit) {
        val caller = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            change()
        }

        caller.cancel()
        runCurrent()
    }

    private fun TestScope.repository(
        mediaPrefs: DataStore<MediaPrefs>,
    ): CapturedItemTrashRepository {
        return CapturedItemTrashRepositoryImpl(
            contentResolver = contentResolver,
            mediaPrefs = mediaPrefs,
            applicationScope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun trashFlagOf(values: ContentValues): Int? {
        return values.getAsInteger(MediaStore.MediaColumns.IS_TRASHED)
    }

    private fun mediaPrefsOf(vararg trashed: Uri): DataStore<MediaPrefs> {
        return InMemoryDataStore(MediaPrefs(trashedItems = trashed.map(Uri::toString)))
    }

    private fun item(uri: Uri): CapturedItem {
        return CapturedItem(
            type = CapturedItemType.IMAGE,
            dateString = "20231015_123456_789",
            uri = uri,
        )
    }

    private companion object {
        const val STILL_TRASHED = "${MediaStore.MediaColumns.IS_TRASHED} = 1"
        val FIRST: Uri = Uri.parse("content://media/external/images/media/1")
        val SECOND: Uri = Uri.parse("content://media/external/images/media/2")
    }
}
