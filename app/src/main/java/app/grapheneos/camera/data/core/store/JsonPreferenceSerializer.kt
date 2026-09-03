package app.grapheneos.camera.data.core.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class JsonPreferenceSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        return try {
            json.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("unreadable preferences", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        // Unknown keys are dropped, not kept: refusing them would wipe every setting.
        val json = Json {
            ignoreUnknownKeys = true
        }
    }
}
