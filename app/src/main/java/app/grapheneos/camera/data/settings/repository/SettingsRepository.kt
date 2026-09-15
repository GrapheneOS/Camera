package app.grapheneos.camera.data.settings.repository

import androidx.camera.video.Quality
import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.di.core.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * One instance per storage, shared by every screen reading it, and the only writer of that storage:
 * [settings] is settled before [update] returns, so a write made on one screen is readable on
 * another without waiting for a dispatch, while the storage itself catches up in the background.
 * A screen holding its own copy would never see the write.
 */
interface SettingsRepository {
    val settings: StateFlow<CameraSettings>

    fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings
    fun modeSettings(slot: ModeSlot): ModeSettings
    fun setFlashMode(slot: ModeSlot, value: Int): ModeSettings
    fun setGeoTagging(slot: ModeSlot, value: Boolean): ModeSettings
    fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings
    fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings
    fun sessionCopy(): SettingsRepository
    suspend fun awaitPersisted()
}

internal class SettingsRepositoryImpl @Inject constructor(
    @DurablePreferences private val dataStore: DataStore<SettingsPrefs>,
    private val cameraSettingsMapper: CameraSettingsMapper,
    private val modeSettingsMapper: ModeSettingsMapper,
    private val storedVideoQualityMapper: StoredVideoQualityMapper,
    @ApplicationScope private val writeScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    private val prefs = MutableStateFlow(runBlocking { dataStore.data.first() })

    private val storedSettings = MutableStateFlow(cameraSettingsMapper.map(prefs.value.common))
    override val settings: StateFlow<CameraSettings> = storedSettings.asStateFlow()

    override fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        val updated = write { prefs ->
            val settings = transform(cameraSettingsMapper.map(prefs.common))
            prefs.copy(common = cameraSettingsMapper.map(settings))
        }

        return cameraSettingsMapper.map(updated.common)
    }

    override fun modeSettings(slot: ModeSlot): ModeSettings {
        return modeSettingsMapper.map(
            stored = prefs.value.mode(slot.mode),
            isFrontFacing = slot.isFrontFacing,
        )
    }

    override fun setFlashMode(slot: ModeSlot, value: Int): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(flashMode = value) },
            asRequested = { it.copy(flashMode = value) },
        )
    }

    override fun setGeoTagging(slot: ModeSlot, value: Boolean): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(geoTagging = value) },
            asRequested = { it.copy(geoTagging = value) },
        )
    }

    override fun setSelfIllumination(slot: ModeSlot, value: Boolean): ModeSettings {
        return writeMode(
            slot = slot,
            store = { stored -> stored.copy(selfIllumination = value) },
            asRequested = { it.copy(selfIllumination = value) },
        )
    }

    override fun setVideoQuality(slot: ModeSlot, value: Quality): ModeSettings {
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

    override fun sessionCopy(): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = InMemoryDataStore(prefs.value),
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = modeSettingsMapper,
            storedVideoQualityMapper = storedVideoQualityMapper,
            writeScope = writeScope,
            ioDispatcher = ioDispatcher,
        )
    }

    override suspend fun awaitPersisted() {
        dataStore.data.first { it == prefs.value }
    }

    private fun writeMode(
        slot: ModeSlot,
        store: (StoredModeSettings) -> StoredModeSettings,
        asRequested: (ModeSettings) -> ModeSettings,
    ): ModeSettings {
        val updated = write { prefs ->
            prefs.withMode(
                mode = slot.mode,
                settings = store(prefs.mode(slot.mode)),
            )
        }

        return asRequested(
            modeSettingsMapper.map(
                stored = updated.mode(slot.mode),
                isFrontFacing = slot.isFrontFacing,
            ),
        )
    }

    private fun write(transform: (SettingsPrefs) -> SettingsPrefs): SettingsPrefs {
        val updated = prefs.updateAndGet(transform)

        storedSettings.value = cameraSettingsMapper.map(prefs.value.common)

        writeScope.launch(ioDispatcher) {
            dataStore.updateData { prefs.value }
        }

        return updated
    }
}
