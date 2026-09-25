package app.grapheneos.camera.data.core.store

import app.grapheneos.camera.data.core.store.InMemoryDataStore
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryDataStoreTest {

    private val dataStore = InMemoryDataStore(StoredValue())

    @Test
    fun updateData_transform_runsAgainstWhatIsStored() {
        runTest {
            dataStore.updateData { it.copy(name = SOME_NAME) }

            val returned = dataStore.updateData { it.copy(written = listOf(it.name)) }

            assertEquals(listOf(SOME_NAME), returned.written)
            assertEquals(listOf(SOME_NAME), stored().written)
        }
    }

    @Test
    fun updateData_transformThrows_leavesTheStoredValueAlone() {
        runTest {
            dataStore.updateData { it.copy(name = SOME_NAME) }

            val failure = runCatching { dataStore.updateData { error("the transform failed") } }

            assertTrue(failure.exceptionOrNull() is IllegalStateException)
            assertEquals(SOME_NAME, stored().name)
        }
    }

    @Test
    fun updateData_concurrentWrites_eachSeeTheOnesBeforeThem() {
        // Real threads, not runTest: a test dispatcher would run the writers one after another and
        // never exercise the store's lock.
        val writers = (1..WRITER_COUNT).map { index ->
            thread {
                runBlocking { dataStore.updateData { it.copy(written = it.written + "$index") } }
            }
        }

        writers.forEach { it.join() }

        val written = runBlocking { stored() }.written
        assertEquals((1..WRITER_COUNT).map { "$it" }.toSet(), written.toSet())
    }

    private suspend fun stored(): StoredValue {
        return dataStore.data.first()
    }

    private data class StoredValue(
        val name: String = "",
        val written: List<String> = emptyList(),
    )

    private companion object {
        const val SOME_NAME = "stored before the transform ran"
        const val WRITER_COUNT = 16
    }
}
