package app.grapheneos.camera.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.video.Quality
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.commonPreferences
import app.grapheneos.camera.data.core.store.modePreferences
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.repository.SettingsKeys
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.util.edit
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A write must land before it returns, on the thread that made it: the in-memory preferences a
 * lockscreen session gets crash if their editor is used from another thread.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun persistentCommons(): SharedPreferences {
        return commonPreferences(context, ephemeral = false)
    }

    private fun persistentModePrefs(mode: CameraMode): SharedPreferences {
        return context.getSharedPreferences(mode.name, Context.MODE_PRIVATE)
    }

    private fun repository(ephemeral: Boolean = false): SettingsRepository {
        return SettingsRepositoryImpl(
            commons = commonPreferences(context, ephemeral = ephemeral),
            modePreferences = modePreferences(context, ephemeral = ephemeral),
        )
    }

    private fun threadRecordingRepository(threads: MutableList<Thread>): SettingsRepository {
        return SettingsRepositoryImpl(
            commons = ThreadRecordingPrefs(persistentCommons(), threads),
            modePreferences = CameraMode.entries.associateWith { mode ->
                lazy { ThreadRecordingPrefs(persistentModePrefs(mode), threads) }
            },
        )
    }

    @Before
    fun clearPersistentPrefs() {
        persistentCommons().edit().clear().commit()
        persistentModePrefs(MODE).edit().clear().commit()
    }

    /** The camera rebinds off the snapshot, so a write must be in it by the time collection ends. */
    @Test
    fun write_flowCollected_isVisibleInTheSnapshot() {
        val repository = repository()

        runBlocking { repository.setAspectRatio(SOME_ASPECT_RATIO).collect() }
        runBlocking { repository.setGridType(GridType.GOLDEN_RATIO).collect() }
        runBlocking { repository.setPhotoQuality(SOME_PHOTO_QUALITY).collect() }

        assertEquals(SOME_ASPECT_RATIO, repository.settings.value.aspectRatio)
        assertEquals(GridType.GOLDEN_RATIO, repository.settings.value.gridType)
        assertEquals(SOME_PHOTO_QUALITY, repository.settings.value.photoQuality)
    }

    /** The writes are cold, so a caller that drops the Flow silently drops the setting with it. */
    @Test
    @Suppress("IgnoredReturnValue")
    fun write_flowNotCollected_doesNothing() {
        val repository = repository()

        repository.setPhotoQuality(SOME_PHOTO_QUALITY)

        assertEquals(DEFAULT_PHOTO_QUALITY, repository.settings.value.photoQuality)
        assertFalse(persistentCommons().contains(SettingsKeys.PHOTO_QUALITY))
    }

    @Test
    fun write_flowCollected_reachesThePreferencesBeforeReturning() {
        val repository = repository()

        runBlocking { repository.setPhotoQuality(SOME_PHOTO_QUALITY).collect() }

        assertEquals(
            SOME_PHOTO_QUALITY,
            persistentCommons().getInt(SettingsKeys.PHOTO_QUALITY, -1),
        )
    }

    @Test
    fun write_alwaysRunsOnTheCallersThread() {
        val editorThreads = mutableListOf<Thread>()
        val repository = threadRecordingRepository(editorThreads)

        runBlocking { repository.setEnableEis(false).collect() }
        runBlocking { repository.setStorageLocation("content://tree/example").collect() }
        repository.reslotMode(mode = MODE, isFrontFacing = true)
        runBlocking { repository.setSelfIllumination(true).collect() }

        assertTrue(editorThreads.isNotEmpty())
        editorThreads.forEach { thread ->
            assertSame(Thread.currentThread(), thread)
        }
    }

    @Test
    fun write_lockscreenSession_neverTouchesThePersistentPreferences() {
        persistentCommons().edit {
            putInt(SettingsKeys.PHOTO_QUALITY, SOME_PHOTO_QUALITY)
        }

        val repository = repository(ephemeral = true)

        assertEquals(SOME_PHOTO_QUALITY, repository.settings.value.photoQuality)

        runBlocking { repository.setPhotoQuality(OTHER_PHOTO_QUALITY).collect() }
        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setGeoTagging(true).collect() }

        assertEquals(OTHER_PHOTO_QUALITY, repository.settings.value.photoQuality)
        assertEquals(
            SOME_PHOTO_QUALITY,
            persistentCommons().getInt(SettingsKeys.PHOTO_QUALITY, -1),
        )
        assertFalse(persistentModePrefs(MODE).contains(SettingsKeys.GEO_TAGGING))
    }

    /** A session handed a fresh in-memory copy per lookup would read the owner's value back. */
    @Test
    fun writeMode_lockscreenSession_keepsItsOwnWrites() {
        val repository = repository(ephemeral = true)

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setGeoTagging(true).collect() }
        repository.reslotMode(mode = MODE, isFrontFacing = false)

        assertTrue(repository.modeSettings.value.geoTagging)
    }

    @Test
    fun writeMode_noModeSlotted_dropsTheWrite() {
        val repository = repository()

        runBlocking { repository.setGeoTagging(true).collect() }
        runBlocking { repository.setVideoQuality(Quality.UHD).collect() }

        assertFalse(repository.modeSettings.value.geoTagging)
        assertFalse(persistentModePrefs(MODE).contains(SettingsKeys.GEO_TAGGING))

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setGeoTagging(true).collect() }

        assertTrue(repository.modeSettings.value.geoTagging)
        assertTrue(persistentModePrefs(MODE).getBoolean(SettingsKeys.GEO_TAGGING, false))
    }

    @Test
    fun videoQuality_unset_readsAsHighestRatherThanTheMappersFallback() {
        val repository = repository()

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        assertEquals(Quality.HIGHEST, repository.modeSettings.value.videoQuality)

        runBlocking { repository.setVideoQuality(Quality.UHD).collect() }
        repository.reslotMode(mode = MODE, isFrontFacing = false)
        assertEquals(Quality.UHD, repository.modeSettings.value.videoQuality)
    }

    /** HIGHEST has no title of its own, and the placeholder one reads back as the lowest quality. */
    @Test
    fun setVideoQuality_highest_clearsTheKeyRatherThanStoringAPlaceholder() {
        val repository = repository()

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setVideoQuality(Quality.UHD).collect() }
        runBlocking { repository.setVideoQuality(Quality.HIGHEST).collect() }

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        assertEquals(Quality.HIGHEST, repository.modeSettings.value.videoQuality)
    }

    @Test
    fun setVideoQuality_eachLensFacing_isStoredSeparately() {
        val repository = repository()

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setVideoQuality(Quality.UHD).collect() }

        repository.reslotMode(mode = MODE, isFrontFacing = true)
        assertEquals(Quality.HIGHEST, repository.modeSettings.value.videoQuality)

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        assertEquals(Quality.UHD, repository.modeSettings.value.videoQuality)
    }

    @Test
    fun writeMode_eachMode_isStoredSeparately() {
        val repository = repository()

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        runBlocking { repository.setGeoTagging(true).collect() }

        repository.reslotMode(mode = OTHER_MODE, isFrontFacing = false)
        assertFalse(repository.modeSettings.value.geoTagging)

        repository.reslotMode(mode = MODE, isFrontFacing = false)
        assertTrue(repository.modeSettings.value.geoTagging)
    }

    @Test
    fun init_installPredatingSaveAsPreviewed_keepsRecordingTheOldWay() {
        persistentCommons().edit {
            putBoolean(SettingsKeys.SAVE_IMAGE_AS_PREVIEW, true)
        }

        val repository = repository()

        assertTrue(repository.settings.value.saveImageAsPreviewed)
        assertFalse(repository.settings.value.saveVideoAsPreviewed)
    }

    @Test
    fun init_freshInstall_savesBothAsPreviewed() {
        val repository = repository()

        assertTrue(repository.settings.value.saveImageAsPreviewed)
        assertTrue(repository.settings.value.saveVideoAsPreviewed)
    }

    /**
     * The snapshot is taken in a field initializer, so a migration that ran after construction
     * would never reach it — and a stored quality of 0 handed to `ImageCapture.setJpegQuality`
     * throws, which is the crash this migration exists to prevent.
     */
    @Test
    fun init_pendingMigrations_areVisibleInTheFirstSnapshot() {
        persistentCommons().edit {
            putInt(SettingsKeys.PHOTO_QUALITY, 0)
        }

        val repository = repository()

        assertEquals(DEFAULT_PHOTO_QUALITY, repository.settings.value.photoQuality)
    }

    @Test
    fun init_legacyQualityEmphasis_isMigratedOnceAndThenLeftAlone() {
        persistentCommons().edit {
            putBoolean(SettingsKeys.EMPHASIS_ON_QUALITY, true)
        }

        val repository = repository()

        assertEquals(MAX_PHOTO_QUALITY, repository.settings.value.photoQuality)
        assertFalse(persistentCommons().contains(SettingsKeys.EMPHASIS_ON_QUALITY))

        // A later change must survive the migration running again on the next launch.
        runBlocking { repository.setPhotoQuality(SOME_PHOTO_QUALITY).collect() }
        val relaunched = repository()

        assertEquals(SOME_PHOTO_QUALITY, relaunched.settings.value.photoQuality)
        assertEquals(
            SOME_PHOTO_QUALITY,
            persistentCommons().getInt(SettingsKeys.PHOTO_QUALITY, -1),
        )
    }

    @Test
    fun init_relaunch_leavesSeededBarcodeFormatsAlone() {
        val repository = repository()

        assertTrue(repository.isBarcodeFormatEnabled(QR_CODE_FORMAT))

        runBlocking {
            repository.setBarcodeFormatEnabled(formatName = QR_CODE_FORMAT, enabled = false)
                .collect()
        }

        val relaunched = repository()

        assertFalse(relaunched.isBarcodeFormatEnabled(QR_CODE_FORMAT))
    }

    private class ThreadRecordingPrefs(
        private val delegate: SharedPreferences,
        private val threads: MutableList<Thread>,
    ) : SharedPreferences by delegate {

        override fun edit(): SharedPreferences.Editor {
            return RecordingEditor(delegate.edit(), threads)
        }

        private class RecordingEditor(
            private val delegate: SharedPreferences.Editor,
            private val threads: MutableList<Thread>,
        ) : SharedPreferences.Editor by delegate {

            override fun apply() {
                threads.add(Thread.currentThread())
                delegate.apply()
            }

            override fun commit(): Boolean {
                threads.add(Thread.currentThread())
                return delegate.commit()
            }
        }
    }

    private companion object {
        val MODE = CameraMode.VIDEO
        val OTHER_MODE = CameraMode.CAMERA

        const val QR_CODE_FORMAT = "QR_CODE"

        const val SOME_ASPECT_RATIO = 1
        const val SOME_PHOTO_QUALITY = 71
        const val OTHER_PHOTO_QUALITY = 42
        const val MAX_PHOTO_QUALITY = 100
        const val DEFAULT_PHOTO_QUALITY = 95
    }
}
