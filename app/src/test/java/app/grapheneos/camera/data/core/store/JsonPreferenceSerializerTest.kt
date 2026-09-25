package app.grapheneos.camera.data.core.store

import androidx.datastore.core.CorruptionException
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPreferenceSerializerTest {

    private val serializer = JsonPreferenceSerializer(
        serializer = StoredValue.serializer(),
        defaultValue = StoredValue(),
    )

    @Test
    fun readFrom_whatWasWritten_readsBackUnchanged() {
        runTest {
            val output = ByteArrayOutputStream()

            serializer.writeTo(StoredValue(name = SOME_NAME), output)

            assertEquals(SOME_NAME, readFrom(output.toByteArray().decodeToString()).name)
        }
    }

    @Test
    fun readFrom_anEmptyFile_isReportedAsCorruption() {
        runTest {
            assertReportedAsCorruption("")
        }
    }

    @Test
    fun readFrom_bytesThatAreNotJsonAtAll_isReportedAsCorruption() {
        runTest {
            assertReportedAsCorruption("\u0000\u0001 not preferences")
        }
    }

    @Test
    fun readFrom_jsonLeftHalfWrittenByAnInterruptedWrite_isReportedAsCorruption() {
        runTest {
            assertReportedAsCorruption("""{"name":"half of a wr""")
        }
    }

    @Test
    fun readFrom_aFieldHoldingTheWrongType_isReportedAsCorruption() {
        runTest {
            assertReportedAsCorruption("""{"name":42}""")
        }
    }

    private suspend fun readFrom(stored: String): StoredValue {
        return serializer.readFrom(stored.encodeToByteArray().inputStream())
    }

    private suspend fun assertReportedAsCorruption(stored: String) {
        val failure = runCatching { readFrom(stored) }.exceptionOrNull()

        assertTrue("expected a CorruptionException, got $failure", failure is CorruptionException)
    }

    @Serializable
    private data class StoredValue(
        val name: String = "",
    )

    private companion object {
        const val SOME_NAME = "written before the file was damaged"
    }
}
