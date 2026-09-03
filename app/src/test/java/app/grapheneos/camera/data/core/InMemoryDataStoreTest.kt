package app.grapheneos.camera.data.core

import app.grapheneos.camera.data.core.store.InMemoryDataStore
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InMemoryDataStoreTest {

    private val dataStore = InMemoryDataStore(StoredValue())

    private fun stored(): StoredValue {
        return runBlocking { dataStore.data.first() }
    }

    @Test
    fun updateData_transform_runsAgainstWhatIsStored() {
        runBlocking { dataStore.updateData { it.copy(name = SOME_NAME) } }

        val returned = runBlocking { dataStore.updateData { it.copy(written = listOf(it.name)) } }

        assertEquals(listOf(SOME_NAME), returned.written)
        assertEquals(listOf(SOME_NAME), stored().written)
    }

    @Test
    fun updateData_transformThrows_leavesTheStoredValueAlone() {
        runBlocking { dataStore.updateData { it.copy(name = SOME_NAME) } }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { dataStore.updateData { error("the transform failed") } }
        }

        assertEquals(SOME_NAME, stored().name)
    }

    @Test
    fun updateData_concurrentWrites_eachSeeTheOnesBeforeThem() {
        val writers = (1..WRITER_COUNT).map { index ->
            thread {
                runBlocking { dataStore.updateData { it.copy(written = it.written + "$index") } }
            }
        }

        writers.forEach { it.join() }

        assertEquals((1..WRITER_COUNT).map { "$it" }.toSet(), stored().written.toSet())
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
