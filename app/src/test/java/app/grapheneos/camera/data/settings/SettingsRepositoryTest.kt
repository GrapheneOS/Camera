package app.grapheneos.camera.data.settings

import androidx.camera.video.Quality
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapperImpl
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import app.grapheneos.camera.data.settings.store.settingsPrefsSerializer
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val fileExecutor = Executors.newSingleThreadExecutor()

    private val fileScope = CoroutineScope(SupervisorJob() + fileExecutor.asCoroutineDispatcher())

    private val dataStore: DataStore<SettingsPrefs> = InMemoryDataStore(SettingsPrefs())

    private val modeSettingsMapper = FakeModeSettingsMapper()

    private val storedVideoQualityMapper = FakeStoredVideoQualityMapper()

    private val cameraSettingsMapper: CameraSettingsMapper = CameraSettingsMapperImpl()

    @After
    fun stopWriting() {
        fileScope.cancel()
        fileExecutor.shutdownNow()
    }

    private fun repository(from: DataStore<SettingsPrefs> = dataStore): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = from,
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = modeSettingsMapper,
            storedVideoQualityMapper = storedVideoQualityMapper,
        )
    }

    private fun settingsOf(repository: SettingsRepository): CameraSettings {
        return runBlocking { repository.settings.first() }
    }

    private fun stored(from: DataStore<SettingsPrefs> = dataStore): SettingsPrefs {
        return runBlocking { from.data.first() }
    }

    @Test
    fun write_returnsWhatItStored() {
        val repository = repository()

        runBlocking {
            repository.update { it.copy(aspectRatio = SOME_ASPECT_RATIO) }
            repository.update { it.copy(gridType = GridType.GOLDEN_RATIO) }
        }

        val written = runBlocking {
            repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }
        }

        assertEquals(SOME_ASPECT_RATIO, written.aspectRatio)
        assertEquals(GridType.GOLDEN_RATIO, written.gridType)
        assertEquals(SOME_PHOTO_QUALITY, written.photoQuality)
    }

    @Test
    fun write_reachesTheStoreBeforeReturning() {
        val repository = repository()

        runBlocking { repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        assertEquals(SOME_PHOTO_QUALITY, stored().common.photoQuality)
    }

    @Test
    fun write_afterAnotherRepositoryWroteADifferentSetting_keepsBoth() {
        val viewfinder = repository()
        val settingsScreen = repository()

        runBlocking { settingsScreen.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        val written = runBlocking { viewfinder.update { it.copy(aspectRatio = SOME_ASPECT_RATIO) } }

        assertEquals(SOME_PHOTO_QUALITY, stored().common.photoQuality)
        assertEquals(SOME_ASPECT_RATIO, stored().common.aspectRatio)
        assertEquals(SOME_PHOTO_QUALITY, written.photoQuality)
    }

    @Test
    fun write_transformReadingTheCurrentValue_isAppliedOnceAgainstWhatIsStored() {
        val viewfinder = repository()
        val settingsScreen = repository()

        runBlocking { settingsScreen.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        val written = runBlocking {
            viewfinder.update { it.copy(photoQuality = it.photoQuality + 1) }
        }

        assertEquals(SOME_PHOTO_QUALITY + 1, stored().common.photoQuality)
        assertEquals(SOME_PHOTO_QUALITY + 1, written.photoQuality)
    }

    @Test
    fun write_onlyEverReachesTheStoreItWasGiven() {
        val owners = InMemoryDataStore(
            SettingsPrefs(
                common = cameraSettingsMapper.map(
                    CameraSettings(photoQuality = SOME_PHOTO_QUALITY),
                ),
            ),
        )
        val session = InMemoryDataStore(stored(from = owners))
        val repository = repository(from = session)

        assertEquals(SOME_PHOTO_QUALITY, settingsOf(repository).photoQuality)

        runBlocking {
            repository.update { it.copy(photoQuality = OTHER_PHOTO_QUALITY) }
            repository.modeSettings(SLOT)
            repository.setGeoTagging(SLOT, true)
        }

        assertEquals(OTHER_PHOTO_QUALITY, settingsOf(repository).photoQuality)
        assertEquals(SOME_PHOTO_QUALITY, stored(from = owners).common.photoQuality)
        assertEquals(emptyMap<String, StoredModeSettings>(), stored(from = owners).modes)
    }

    @Test
    fun write_returns_leavingTheValueOnDisk() {
        val file = File(temporaryFolder.root, "settings_prefs.json")
        val repository = repository(
            from = DataStoreFactory.create(
                serializer = settingsPrefsSerializer,
                scope = fileScope,
            ) {
                file
            },
        )

        runBlocking { repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        val onDisk = runBlocking { settingsPrefsSerializer.readFrom(file.inputStream()) }

        assertEquals(SOME_PHOTO_QUALITY, onDisk.common.photoQuality)
    }

    @Test
    fun settings_afterAWrite_isSettledWithoutWaitingForADispatch() {
        val repository = repository()

        assertEquals(SettingsDefaults.PHOTO_QUALITY, repository.settings.value.photoQuality)

        runBlocking { repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        assertEquals(SOME_PHOTO_QUALITY, repository.settings.value.photoQuality)
    }

    @Test
    fun settings_ofARepositoryOpenedOnAWrittenStore_readsWhatIsThere() {
        runBlocking { repository().update { it.copy(photoQuality = SOME_PHOTO_QUALITY) } }

        assertEquals(SOME_PHOTO_QUALITY, repository().settings.value.photoQuality)
    }

    @Test
    fun writeMode_selectingTheSameModeAgain_keepsTheWritesMadeToIt() {
        val repository = repository()

        runBlocking {
            repository.modeSettings(SLOT)
            repository.setGeoTagging(SLOT, true)
            repository.modeSettings(SLOT)
        }

        assertEquals(
            StoredModeSettings(geoTagging = true),
            modeSettingsMapper.calls.last().stored,
        )
    }

    @Test
    fun modeSettings_exposesWhatTheMapperMadeOfTheStoredMode() {
        val repository = repository()

        modeSettingsMapper.result = MAPPER_RESULT

        val selected = runBlocking { repository.modeSettings(FRONT_SLOT) }

        assertEquals(
            FakeModeSettingsMapper.Call(stored = StoredModeSettings(), isFrontFacing = true),
            modeSettingsMapper.calls.single(),
        )
        assertEquals(MAPPER_RESULT, selected)
    }

    @Test
    fun setVideoQuality_storesTheNameTheMapperGaveIt() {
        val repository = repository()

        runBlocking { repository.modeSettings(SLOT) }
        storedVideoQualityMapper.result = StoredVideoQuality.DEVICE_CHOICE
        runBlocking { repository.setVideoQuality(SLOT, Quality.HIGHEST) }

        assertEquals(listOf(Quality.HIGHEST), storedVideoQualityMapper.calls)
        assertEquals(
            StoredVideoQuality.DEVICE_CHOICE,
            stored().modes[MODE.name]?.videoQualityBack,
        )
    }

    @Test
    fun writeMode_qualityTheStoreCannotName_isStillWhatTheModeReports() {
        val repository = SettingsRepositoryImpl(
            dataStore = dataStore,
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = ModeSettingsMapperImpl(),
            storedVideoQualityMapper = StoredVideoQualityMapperImpl(),
        )

        runBlocking { repository.modeSettings(SLOT) }

        val written = runBlocking { repository.setVideoQuality(SLOT, Quality.LOWEST) }

        assertEquals(Quality.LOWEST, written.videoQuality)
        assertEquals(
            StoredVideoQuality.DEVICE_CHOICE,
            stored().modes[MODE.name]?.videoQualityBack,
        )
    }

    @Test
    fun setVideoQuality_eachLensFacing_isStoredSeparately() {
        val repository = repository()

        runBlocking { repository.modeSettings(SLOT) }
        storedVideoQualityMapper.result = StoredVideoQuality.UHD
        runBlocking { repository.setVideoQuality(SLOT, Quality.UHD) }

        runBlocking { repository.modeSettings(FRONT_SLOT) }
        storedVideoQualityMapper.result = StoredVideoQuality.HD
        runBlocking { repository.setVideoQuality(FRONT_SLOT, Quality.HD) }

        val storedMode = stored().modes.getValue(MODE.name)

        assertEquals(StoredVideoQuality.UHD, storedMode.videoQualityBack)
        assertEquals(StoredVideoQuality.HD, storedMode.videoQualityFront)
    }

    @Test
    fun modeSettings_afterARelaunch_mapsWhatTheStoreHeld() {
        val repository = repository()

        runBlocking { repository.modeSettings(SLOT) }
        storedVideoQualityMapper.result = StoredVideoQuality.FHD
        runBlocking { repository.setVideoQuality(SLOT, Quality.FHD) }

        val relaunched = repository()

        runBlocking { relaunched.modeSettings(SLOT) }

        assertEquals(
            StoredModeSettings(videoQualityBack = StoredVideoQuality.FHD),
            modeSettingsMapper.calls.last().stored,
        )
    }

    @Test
    fun writeMode_eachMode_isStoredSeparately() {
        val repository = repository()

        runBlocking {
            repository.modeSettings(SLOT)
            repository.setGeoTagging(SLOT, true)
            repository.modeSettings(OTHER_SLOT)
        }

        assertEquals(StoredModeSettings(), modeSettingsMapper.calls.last().stored)

        runBlocking { repository.modeSettings(SLOT) }

        assertEquals(StoredModeSettings(geoTagging = true), modeSettingsMapper.calls.last().stored)
    }

    @Test
    fun barcodeFormats_untouched_defaultToQrCodeOnly() {
        assertEquals(setOf(QR_CODE_FORMAT), settingsOf(repository()).enabledBarcodeFormats)
    }

    @Test
    fun barcodeFormats_disabled_staysDisabledAcrossRelaunch() {
        val repository = repository()

        runBlocking {
            repository.update {
                it.withBarcodeFormat(formatName = QR_CODE_FORMAT, enabled = false)
            }
        }

        assertFalse(QR_CODE_FORMAT in settingsOf(repository).enabledBarcodeFormats)
        assertFalse(QR_CODE_FORMAT in settingsOf(repository()).enabledBarcodeFormats)
    }

    @Test
    fun barcodeFormats_anotherEnabled_keepsTheDefaultEnabledToo() {
        val repository = repository()

        runBlocking {
            repository.update {
                it.withBarcodeFormat(formatName = AZTEC_FORMAT, enabled = true)
            }
        }

        assertEquals(
            setOf(QR_CODE_FORMAT, AZTEC_FORMAT),
            settingsOf(repository()).enabledBarcodeFormats,
        )
    }

    private class FakeModeSettingsMapper : ModeSettingsMapper {

        val calls = mutableListOf<Call>()

        var result = ModeSettings()

        override fun map(stored: StoredModeSettings, isFrontFacing: Boolean): ModeSettings {
            calls += Call(stored = stored, isFrontFacing = isFrontFacing)

            return result
        }

        data class Call(
            val stored: StoredModeSettings,
            val isFrontFacing: Boolean,
        )
    }

    private class FakeStoredVideoQualityMapper : StoredVideoQualityMapper {

        val calls = mutableListOf<Quality>()

        var result = StoredVideoQuality.DEVICE_CHOICE

        override fun map(quality: Quality): StoredVideoQuality {
            calls += quality

            return result
        }
    }

    private companion object {
        val MODE = CameraMode.VIDEO
        val OTHER_MODE = CameraMode.CAMERA

        val SLOT = ModeSlot(mode = MODE, isFrontFacing = false)
        val FRONT_SLOT = ModeSlot(mode = MODE, isFrontFacing = true)
        val OTHER_SLOT = ModeSlot(mode = OTHER_MODE, isFrontFacing = false)

        val MAPPER_RESULT = ModeSettings(geoTagging = true, videoQuality = Quality.FHD)

        const val QR_CODE_FORMAT = "QR_CODE"
        const val AZTEC_FORMAT = "AZTEC"

        const val SOME_ASPECT_RATIO = 1
        const val SOME_PHOTO_QUALITY = 71
        const val OTHER_PHOTO_QUALITY = 42
    }
}
