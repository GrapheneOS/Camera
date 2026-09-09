package app.grapheneos.camera.data.settings.repository

import androidx.camera.video.Quality
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * One instance per storage, shared by every screen reading it: [settings] is settled before
 * [update] returns, so a write made on one screen is readable on another without waiting for a
 * dispatch. A screen holding its own copy would only see the write a main-thread turn later.
 */
interface SettingsRepository {

    val settings: StateFlow<CameraSettings>

    suspend fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings

    suspend fun modeSettings(slot: ModeSlot): ModeSettings

    suspend fun setFlashMode(slot: ModeSlot, value: Int): ModeSettings

    suspend fun setGeoTagging(slot: ModeSlot, value: Boolean): ModeSettings

    suspend fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings

    suspend fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings
}

internal class SettingsRepositoryImpl(
    private val dataStore: DataStore<SettingsPrefs>,
    private val cameraSettingsMapper: CameraSettingsMapper,
    private val modeSettingsMapper: ModeSettingsMapper,
    private val storedVideoQualityMapper: StoredVideoQualityMapper,
) : SettingsRepository {

    private val storedSettings = MutableStateFlow(
        runBlocking { cameraSettingsMapper.map(dataStore.data.first().common) },
    )

    override val settings: StateFlow<CameraSettings> = storedSettings.asStateFlow()

    override suspend fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        val updated = dataStore
            .updateData { prefs ->
                val settings = transform(cameraSettingsMapper.map(prefs.common))

                prefs.copy(common = cameraSettingsMapper.map(settings))
            }
            .let { cameraSettingsMapper.map(it.common) }

        storedSettings.value = updated

        return updated
    }

    override suspend fun modeSettings(slot: ModeSlot): ModeSettings {
        return modeSettingsMapper.map(
            stored = dataStore.data.first().mode(slot.mode),
            isFrontFacing = slot.isFrontFacing,
        )
    }

    override suspend fun setFlashMode(slot: ModeSlot, value: Int): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(flashMode = value) },
            asRequested = { it.copy(flashMode = value) },
        )
    }

    override suspend fun setGeoTagging(slot: ModeSlot, value: Boolean): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(geoTagging = value) },
            asRequested = { it.copy(geoTagging = value) },
        )
    }

    override suspend fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(selfIllumination = value) },
            asRequested = { it.copy(selfIllumination = value) },
        )
    }

    override suspend fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored ->
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
        slot: ModeSlot,
        store: (StoredModeSettings) -> StoredModeSettings,
        asRequested: (ModeSettings) -> ModeSettings,
    ): ModeSettings {
        val prefs = dataStore.updateData { prefs ->
            val stored = store(prefs.mode(slot.mode))

            prefs.withMode(mode = slot.mode, settings = stored)
        }

        return asRequested(
            modeSettingsMapper.map(
                stored = prefs.mode(slot.mode),
                isFrontFacing = slot.isFrontFacing,
            ),
        )
    }
}
