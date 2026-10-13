package app.grapheneos.camera.data.licenses.repository

import android.content.res.AssetManager
import app.grapheneos.camera.data.licenses.model.OpenSourceLicenses
import app.grapheneos.camera.di.core.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface LicensesRepository {

    suspend fun licenses(): OpenSourceLicenses
}

internal class LicensesRepositoryImpl @Inject constructor(
    private val assets: AssetManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LicensesRepository {

    override suspend fun licenses(): OpenSourceLicenses {
        return withContext(ioDispatcher) {
            val json = assets.open(LICENSES_ASSET).use {
                it.readBytes().decodeToString()
            }

            Json.decodeFromString<OpenSourceLicenses>(json)
        }
    }

    private companion object {
        const val LICENSES_ASSET = "licenses.json"
    }
}
