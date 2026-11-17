package app.grapheneos.camera.data.settings.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapperImpl
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import app.grapheneos.camera.data.settings.store.settingsPrefsSerializer
import app.grapheneos.camera.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fileExecutor = Executors.newSingleThreadExecutor()

    private val fileScope = CoroutineScope(SupervisorJob() + fileExecutor.asCoroutineDispatcher())

    private val dataStore: DataStore<SettingsPrefs> = InMemoryDataStore(SettingsPrefs())

    private val modeSettingsMapper: ModeSettingsMapper = mockk {
        every { map(stored = any(), slot = any()) } returns ModeSettings()
        every { map(flashMode = any(), mode = any()) } returns StoredFlashMode.OFF
    }

    private val storedVideoQualityMapper: StoredVideoQualityMapper = mockk {
        every { map(quality = any()) } returns StoredVideoQuality.DEVICE_CHOICE
    }

    private val cameraSettingsMapper: CameraSettingsMapper = CameraSettingsMapperImpl()

    @After
    fun stopWriting() {
        fileScope.cancel()
        fileExecutor.shutdownNow()
    }

    @Test
    fun update_returnsWhatItStored() {
        runTest {
            val repository = repository()

            repository.update { it.copy(aspectRatio = SOME_ASPECT_RATIO) }
            repository.update { it.copy(gridType = GridType.GOLDEN_RATIO) }

            val written = repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }

            assertEquals(SOME_ASPECT_RATIO, written.aspectRatio)
            assertEquals(GridType.GOLDEN_RATIO, written.gridType)
            assertEquals(SOME_PHOTO_QUALITY, written.photoQuality)
        }
    }

    @Test
    fun update_isReadableBeforeTheStoreCatchesUp() {
        runTest {
            val repository = repository(from = stalledStore())

            repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }
            repository.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }
            repository.modeSettings(SLOT)

            assertEquals(SOME_PHOTO_QUALITY, repository.settings.value.photoQuality)
            assertEquals(SOME_STORED_MODE, lastMappedMode())
        }
    }

    @Test
    fun update_transformReadingTheCurrentValue_buildsOnThePreviousWrite() {
        runTest {
            val repository = repository()
            repository.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }

            val written = repository.update { it.copy(photoQuality = it.photoQuality + 1) }

            assertEquals(SOME_PHOTO_QUALITY + 1, written.photoQuality)
            assertEquals(SOME_PHOTO_QUALITY + 1, stored().common.photoQuality)
        }
    }

    @Test
    fun update_manyTimes_leavesTheDiskHoldingTheLastOne() {
        runTest {
            val file = File(temporaryFolder.root, "settings_prefs.json")
            val store = DataStoreFactory.create(
                serializer = settingsPrefsSerializer,
                scope = fileScope,
            ) {
                file
            }
            val repository = repository(from = store)

            repeat(WRITES) { quality ->
                repository.update { it.copy(photoQuality = quality) }
            }
            store.data.first { it.common.photoQuality == WRITES - 1 }

            val onDisk = settingsPrefsSerializer.readFrom(file.inputStream())

            assertEquals(WRITES - 1, onDisk.common.photoQuality)
            assertEquals(WRITES - 1, repository.settings.value.photoQuality)
        }
    }

    @Test
    fun sessionCopy_startsFromWhatTheOwnerHoldsBeforeItIsPersisted() {
        runTest {
            val owners = repository(from = stalledStore())
            owners.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }
            owners.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }

            val session = owners.sessionCopy()
            session.modeSettings(SLOT)

            assertEquals(SOME_PHOTO_QUALITY, settingsOf(session).photoQuality)
            assertEquals(SOME_STORED_MODE, lastMappedMode())
        }
    }

    @Test
    fun sessionCopy_writesStayOutOfTheOwnersStore() {
        runTest {
            val owners = repository()
            owners.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }

            val session = owners.sessionCopy()
            session.update { it.copy(photoQuality = OTHER_PHOTO_QUALITY) }
            session.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }

            assertEquals(OTHER_PHOTO_QUALITY, settingsOf(session).photoQuality)
            assertEquals(SOME_PHOTO_QUALITY, settingsOf(owners).photoQuality)
            assertEquals(SOME_PHOTO_QUALITY, stored().common.photoQuality)
            assertEquals(emptyMap<String, StoredModeSettings>(), stored().modes)
        }
    }

    @Test
    fun sessionCopy_doesNotFollowTheOwnersLaterChanges() {
        runTest {
            val owners = repository()
            val session = owners.sessionCopy()

            owners.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }

            assertEquals(SettingsDefaults.PHOTO_QUALITY, settingsOf(session).photoQuality)
        }
    }

    @Test
    fun settings_ofARepositoryOpenedOnAPersistedStore_readsWhatIsThere() {
        runTest {
            val written = repository()

            written.update { it.copy(photoQuality = SOME_PHOTO_QUALITY) }

            assertEquals(SOME_PHOTO_QUALITY, repository().settings.value.photoQuality)
        }
    }

    @Test
    fun modeSettings_selectingTheSameModeAgain_keepsTheWritesMadeToIt() {
        runTest {
            val repository = repository()

            repository.modeSettings(SLOT)
            repository.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }
            repository.modeSettings(SLOT)

            assertEquals(
                SOME_STORED_MODE,
                lastMappedMode(),
            )
        }
    }

    @Test
    fun modeSettings_exposesWhatTheMapperMadeOfTheStoredMode() {
        runTest {
            val repository = repository()

            every {
                modeSettingsMapper.map(stored = any(), slot = any())
            } returns MAPPER_RESULT

            val selected = repository.modeSettings(FRONT_SLOT)

            verify(exactly = 1) {
                modeSettingsMapper.map(stored = StoredModeSettings(), slot = FRONT_SLOT)
            }
            confirmVerified(modeSettingsMapper)
            assertEquals(MAPPER_RESULT, selected)
        }
    }

    @Test
    fun updateMode_storesTheVideoQualityNameTheMapperGaveIt() {
        runTest {
            val repository = repository()

            storeVideoQualityAs(StoredVideoQuality.UHD)
            repository.updateMode(SLOT) { it.copy(videoQuality = VideoQuality.UHD) }

            verify(exactly = 1) { storedVideoQualityMapper.map(quality = VideoQuality.UHD) }
            confirmVerified(storedVideoQualityMapper)
            assertEquals(
                StoredVideoQuality.UHD,
                stored().modes[MODE.name]?.videoQualityBack,
            )
        }
    }

    @Test
    fun updateMode_storesOnlyTheFieldsItChanges() {
        runTest {
            val repository = repository()

            repository.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }

            assertEquals(SOME_STORED_MODE, stored().modes[MODE.name])
        }
    }

    @Test
    fun updateMode_changingNothing_storesNothing() {
        runTest {
            val repository = repository()

            repository.updateMode(SLOT) { it }

            assertNull(stored().mode(MODE).flashModeFront)
        }
    }

    @Test
    fun updateMode_eachLensFacing_storesItsVideoQualitySeparately() {
        runTest {
            val repository = repository()

            repository.modeSettings(SLOT)
            storeVideoQualityAs(StoredVideoQuality.UHD)
            repository.updateMode(SLOT) { it.copy(videoQuality = VideoQuality.UHD) }

            repository.modeSettings(FRONT_SLOT)
            storeVideoQualityAs(StoredVideoQuality.HD)
            repository.updateMode(FRONT_SLOT) { it.copy(videoQuality = VideoQuality.HD) }

            val storedMode = stored().modes.getValue(MODE.name)

            assertEquals(StoredVideoQuality.UHD, storedMode.videoQualityBack)
            assertEquals(StoredVideoQuality.HD, storedMode.videoQualityFront)
        }
    }

    @Test
    fun modeSettings_afterARelaunch_mapsWhatTheStoreHeld() {
        runTest {
            val repository = repository()

            repository.modeSettings(SLOT)
            storeVideoQualityAs(StoredVideoQuality.FHD)
            repository.updateMode(SLOT) { it.copy(videoQuality = VideoQuality.FHD) }

            val relaunched = repository()

            relaunched.modeSettings(SLOT)

            assertEquals(
                StoredModeSettings(videoQualityBack = StoredVideoQuality.FHD),
                lastMappedMode(),
            )
        }
    }

    @Test
    fun modeSettings_eachMode_isStoredSeparately() {
        runTest {
            val repository = repository()

            repository.modeSettings(SLOT)
            repository.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }
            repository.modeSettings(OTHER_SLOT)

            assertEquals(StoredModeSettings(), lastMappedMode())

            repository.modeSettings(SLOT)

            assertEquals(SOME_STORED_MODE, lastMappedMode())
        }
    }

    @Test
    fun observeModeSettings_followsWritesToThatSlotOnly() {
        runTest {
            every { modeSettingsMapper.map(stored = any(), slot = any()) } answers {
                val stored = firstArg<StoredModeSettings>()

                when (stored.frameRateBack) {
                    null -> ModeSettings()
                    else -> ModeSettings(videoFrameRate = SOME_FRAME_RATE)
                }
            }
            val repository = repository()
            val observed = mutableListOf<ModeSettings>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                repository.observeModeSettings(SLOT).collect { observed += it }
            }

            repository.updateMode(OTHER_SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }
            repository.updateMode(SLOT) { it.copy(videoFrameRate = SOME_FRAME_RATE) }

            assertEquals(
                listOf(ModeSettings(), ModeSettings(videoFrameRate = SOME_FRAME_RATE)),
                observed,
            )
        }
    }

    @Test
    fun updateMode_selfIllumination_isTemporaryAndScopedToItsSlot() {
        runTest {
            val repository = repository(mapper = ModeSettingsMapperImpl())

            repository.updateMode(FRONT_SLOT) { it.copy(flashMode = FlashMode.SELF_ILLUMINATION) }

            val relaunched = repository(mapper = ModeSettingsMapperImpl())

            assertEquals(FlashMode.SELF_ILLUMINATION, repository.modeSettings(FRONT_SLOT).flashMode)
            assertEquals(FlashMode.OFF, repository.modeSettings(SLOT).flashMode)
            assertNull(stored().mode(MODE).flashModeFront)
            assertEquals(SettingsDefaults.FLASH_MODE, relaunched.modeSettings(FRONT_SLOT).flashMode)
        }
    }

    @Test
    fun updateMode_savedFlashMode_replacesTemporaryLight() {
        runTest {
            val repository = repository(mapper = ModeSettingsMapperImpl())

            repository.updateMode(FRONT_SLOT) { it.copy(flashMode = FlashMode.SELF_ILLUMINATION) }
            repository.updateMode(FRONT_SLOT) { it.copy(flashMode = FlashMode.LOW_LIGHT_BOOST) }

            assertEquals(FlashMode.LOW_LIGHT_BOOST, repository.modeSettings(FRONT_SLOT).flashMode)
            val relaunched = repository(mapper = ModeSettingsMapperImpl())
            assertEquals(FlashMode.LOW_LIGHT_BOOST, relaunched.modeSettings(FRONT_SLOT).flashMode)
            assertEquals(
                StoredModeSettings(flashModeFront = StoredFlashMode.LOW_LIGHT_BOOST),
                stored().modes[MODE.name],
            )
        }
    }

    @Test
    fun updateMode_videoLight_isNotRestoredFromStorage() {
        runTest {
            val repository = repository(mapper = ModeSettingsMapperImpl())

            repository.updateMode(SLOT) { it.copy(flashMode = FlashMode.ON) }

            assertEquals(FlashMode.ON, repository.modeSettings(SLOT).flashMode)
            assertNull(stored().mode(MODE).flashModeBack)
            val relaunched = repository(mapper = ModeSettingsMapperImpl())
            assertEquals(FlashMode.OFF, relaunched.modeSettings(SLOT).flashMode)
        }
    }

    @Test
    fun updateMode_photoFlash_isRestoredFromStorage() {
        runTest {
            val repository = repository(mapper = ModeSettingsMapperImpl())

            repository.updateMode(OTHER_SLOT) { it.copy(flashMode = FlashMode.ON) }

            val relaunched = repository(mapper = ModeSettingsMapperImpl())
            assertEquals(FlashMode.ON, relaunched.modeSettings(OTHER_SLOT).flashMode)
        }
    }

    @Test
    fun sessionCopy_excludesTemporaryLightsAndKeepsSavedFlashModes() {
        runTest {
            val owners = repository(mapper = ModeSettingsMapperImpl())
            owners.updateMode(SLOT) { it.copy(flashMode = FlashMode.ON) }
            owners.updateMode(FRONT_SLOT) { it.copy(flashMode = FlashMode.SELF_ILLUMINATION) }
            owners.updateMode(OTHER_SLOT) { it.copy(flashMode = FlashMode.AUTO) }

            val session = owners.sessionCopy()

            assertEquals(FlashMode.OFF, session.modeSettings(SLOT).flashMode)
            assertEquals(FlashMode.OFF, session.modeSettings(FRONT_SLOT).flashMode)
            assertEquals(FlashMode.AUTO, session.modeSettings(OTHER_SLOT).flashMode)
            session.updateMode(FRONT_SLOT) { it.copy(flashMode = FlashMode.LOW_LIGHT_BOOST) }
            assertEquals(FlashMode.SELF_ILLUMINATION, owners.modeSettings(FRONT_SLOT).flashMode)
            assertEquals(FlashMode.ON, owners.modeSettings(SLOT).flashMode)
        }
    }

    @Test
    fun barcodeFormats_untouched_defaultToQrCodeOnly() {
        runTest {
            assertEquals(setOf(QR_CODE_FORMAT), settingsOf(repository()).enabledBarcodeFormats)
        }
    }

    @Test
    fun barcodeFormats_disabled_staysDisabledAcrossRelaunch() {
        runTest {
            val repository = repository()

            repository.update {
                it.withBarcodeFormat(formatName = QR_CODE_FORMAT, enabled = false)
            }

            assertFalse(QR_CODE_FORMAT in settingsOf(repository).enabledBarcodeFormats)
            assertFalse(QR_CODE_FORMAT in settingsOf(repository()).enabledBarcodeFormats)
        }
    }

    @Test
    fun barcodeFormats_anotherEnabled_keepsTheDefaultEnabledToo() {
        runTest {
            val repository = repository()

            repository.update {
                it.withBarcodeFormat(formatName = AZTEC_FORMAT, enabled = true)
            }

            assertEquals(
                setOf(QR_CODE_FORMAT, AZTEC_FORMAT),
                settingsOf(repository()).enabledBarcodeFormats,
            )
        }
    }

    private fun TestScope.repository(
        from: DataStore<SettingsPrefs> = dataStore,
        mapper: ModeSettingsMapper = modeSettingsMapper,
    ): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = from,
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = mapper,
            storedVideoQualityMapper = storedVideoQualityMapper,
            writeScope = backgroundScope,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        )
    }

    private fun lastMappedMode(): StoredModeSettings {
        val mapped = mutableListOf<StoredModeSettings>()
        verify { modeSettingsMapper.map(stored = capture(mapped), slot = any()) }
        return mapped.last()
    }

    private fun storeVideoQualityAs(stored: StoredVideoQuality) {
        every { storedVideoQualityMapper.map(quality = any()) } returns stored
    }

    private fun settingsOf(repository: SettingsRepository): CameraSettings {
        return repository.settings.value
    }

    private fun stalledStore(): DataStore<SettingsPrefs> {
        return mockk {
            every { data } returns flowOf(SettingsPrefs())
            coEvery { updateData(transform = any()) } coAnswers { awaitCancellation() }
        }
    }

    private suspend fun stored(from: DataStore<SettingsPrefs> = dataStore): SettingsPrefs {
        return from.data.first()
    }

    private companion object {
        val MODE = CameraMode.VIDEO
        val OTHER_MODE = CameraMode.CAMERA

        val SLOT = ModeSlot(mode = MODE, lensFacing = LensFacing.BACK)
        val FRONT_SLOT = ModeSlot(mode = MODE, lensFacing = LensFacing.FRONT)
        val OTHER_SLOT = ModeSlot(mode = OTHER_MODE, lensFacing = LensFacing.BACK)

        val SOME_FRAME_RATE = VideoFrameRate.FPS_60
        val SOME_STORED_MODE = StoredModeSettings(
            frameRateBack = SOME_FRAME_RATE.framesPerSecond,
        )

        val MAPPER_RESULT = ModeSettings(
            flashMode = FlashMode.AUTO,
            videoQuality = VideoQuality.FHD,
        )

        const val QR_CODE_FORMAT = "QR_CODE"
        const val AZTEC_FORMAT = "AZTEC"

        const val SOME_PHOTO_QUALITY = 71
        const val OTHER_PHOTO_QUALITY = 42

        val SOME_ASPECT_RATIO = AspectRatio.RATIO_16_9

        const val WRITES = 50
    }
}
