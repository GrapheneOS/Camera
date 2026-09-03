package app.grapheneos.camera.data.core

import androidx.datastore.core.CorruptionException
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonPreferenceSerializerTest {

    private val serializer = JsonPreferenceSerializer(
        serializer = StoredValue.serializer(),
        defaultValue = StoredValue(),
    )

    private fun readFrom(stored: String): StoredValue {
        return runBlocking { serializer.readFrom(stored.encodeToByteArray().inputStream()) }
    }

    private fun assertReportedAsCorruption(stored: String) {
        assertThrows(CorruptionException::class.java) { readFrom(stored) }
    }

    @Test
    fun readFrom_whatWasWritten_readsBackUnchanged() {
        val output = ByteArrayOutputStream()

        runBlocking { serializer.writeTo(StoredValue(name = SOME_NAME), output) }

        assertEquals(SOME_NAME, readFrom(output.toByteArray().decodeToString()).name)
    }

    @Test
    fun readFrom_anEmptyFile_isReportedAsCorruption() {
        assertReportedAsCorruption("")
    }

    @Test
    fun readFrom_bytesThatAreNotJsonAtAll_isReportedAsCorruption() {
        assertReportedAsCorruption("\u0000\u0001 not preferences")
    }

    @Test
    fun readFrom_jsonLeftHalfWrittenByAnInterruptedWrite_isReportedAsCorruption() {
        assertReportedAsCorruption("""{"name":"half of a wr""")
    }

    @Test
    fun readFrom_aFieldHoldingTheWrongType_isReportedAsCorruption() {
        assertReportedAsCorruption("""{"name":42}""")
    }

    @Serializable
    private data class StoredValue(
        val name: String = "",
    )

    private companion object {
        const val SOME_NAME = "written before the file was damaged"
    }
}
