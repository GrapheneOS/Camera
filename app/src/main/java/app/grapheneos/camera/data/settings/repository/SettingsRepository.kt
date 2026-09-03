package app.grapheneos.camera.data.settings.repository

import androidx.camera.video.Quality
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface SettingsRepository {

    val settings: Flow<CameraSettings>

    suspend fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings

    suspend fun selectMode(mode: CameraMode, isFrontFacing: Boolean): ModeSettings

    suspend fun setFlashMode(value: Int): ModeSettings?

    suspend fun setGeoTagging(value: Boolean): ModeSettings?

    suspend fun setSelfIllumination(value: Boolean): ModeSettings?

    suspend fun setVideoQuality(value: Quality): ModeSettings?
}

internal class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<SettingsPrefs>,
    private val cameraSettingsMapper: CameraSettingsMapper,
    private val modeSettingsMapper: ModeSettingsMapper,
    private val storedVideoQualityMapper: StoredVideoQualityMapper,
) : SettingsRepository {

    override val settings: Flow<CameraSettings> = dataStore.data.map {
        cameraSettingsMapper.map(it.common)
    }

    private var slotted: SlottedMode? = null

    override suspend fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        return dataStore
            .updateData { prefs ->
                val settings = transform(cameraSettingsMapper.map(prefs.common))

                prefs.copy(common = cameraSettingsMapper.map(settings))
            }
            .let { cameraSettingsMapper.map(it.common) }
    }

    override suspend fun selectMode(mode: CameraMode, isFrontFacing: Boolean): ModeSettings {
        slotted = SlottedMode(mode = mode, isFrontFacing = isFrontFacing)

        return modeSettingsMapper.map(
            stored = dataStore.data.first().mode(mode),
            isFrontFacing = isFrontFacing,
        )
    }

    override suspend fun setFlashMode(value: Int): ModeSettings? {
        return writeMode(
            store = { stored, _ -> stored.copy(flashMode = value) },
            asRequested = { it.copy(flashMode = value) },
        )
    }

    override suspend fun setGeoTagging(value: Boolean): ModeSettings? {
        return writeMode(
            store = { stored, _ -> stored.copy(geoTagging = value) },
            asRequested = { it.copy(geoTagging = value) },
        )
    }

    override suspend fun setSelfIllumination(value: Boolean): ModeSettings? {
        return writeMode(
            store = { stored, _ -> stored.copy(selfIllumination = value) },
            asRequested = { it.copy(selfIllumination = value) },
        )
    }

    override suspend fun setVideoQuality(value: Quality): ModeSettings? {
        return writeMode(
            store = { stored, slot ->
                val quality = storedVideoQualityMapper.map(value)

                when {
                    slot.isFrontFacing -> stored.copy(videoQualityFront = quality)
                    else -> stored.copy(videoQualityBack = quality)
                }
            },
            asRequested = { it.copy(videoQuality = value) },
        )
    }

    private suspend fun writeMode(
        store: (StoredModeSettings, SlottedMode) -> StoredModeSettings,
        asRequested: (ModeSettings) -> ModeSettings,
    ): ModeSettings? {
        val slot = slotted ?: return null

        val prefs = dataStore.updateData { prefs ->
            val stored = store(prefs.mode(slot.mode), slot)

            prefs.withMode(mode = slot.mode, settings = stored)
        }

        return asRequested(
            modeSettingsMapper.map(
                stored = prefs.mode(slot.mode),
                isFrontFacing = slot.isFrontFacing,
            ),
        )
    }

    private data class SlottedMode(
        val mode: CameraMode,
        val isFrontFacing: Boolean,
    )
}
