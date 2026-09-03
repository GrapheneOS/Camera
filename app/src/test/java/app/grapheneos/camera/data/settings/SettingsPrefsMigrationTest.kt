package app.grapheneos.camera.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.core.LEGACY_CAPTURE_KEY_NAMES
import app.grapheneos.camera.data.core.LEGACY_STORAGE_KEY_NAMES
import app.grapheneos.camera.data.core.clearLegacyPreferences
import app.grapheneos.camera.data.core.legacyCommonsFile
import app.grapheneos.camera.data.core.legacyCommonsFileExists
import app.grapheneos.camera.data.core.legacyModeFile
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.writeLegacyPreferences
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.SettingsPrefsMigration
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.data.settings.store.StoredVideoQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPrefsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val migration = SettingsPrefsMigration(context)

    private val mapper: CameraSettingsMapper = CameraSettingsMapperImpl()

    private fun commons(): SharedPreferences {
        return legacyCommonsFile(context)
    }

    private fun modePreferences(mode: CameraMode): SharedPreferences {
        return legacyModeFile(context, mode)
    }

    private fun commonsFileExists(): Boolean {
        return legacyCommonsFileExists(context)
    }

    private fun migrate(): SettingsPrefs {
        return runBlocking {
            assertTrue("nothing to migrate", migration.shouldMigrate(SettingsPrefs()))
            migration.migrate(SettingsPrefs())
        }
    }

    private fun migrateCommon(): CameraSettings {
        return mapper.map(migrate().common)
    }

    @Before
    fun clearLegacyFiles() {
        clearLegacyPreferences(context)
    }

    @Test
    fun shouldMigrate_freshInstall_isFalse() {
        assertFalse(runBlocking { migration.shouldMigrate(SettingsPrefs()) })
    }

    @Test
    fun shouldMigrate_onlyAModeFileHoldsAnything_isTrue() {
        modePreferences(MODE).edit(commit = true) { putBoolean(GEO_TAGGING, true) }

        assertTrue(runBlocking { migration.shouldMigrate(SettingsPrefs()) })
    }

    @Test
    fun migrate_gridStoredAsAnOrdinal_becomesTheNamedConstant() {
        commons().edit(commit = true) { putInt(GRID, GOLDEN_RATIO_ORDINAL) }

        assertEquals(GridType.GOLDEN_RATIO, migrateCommon().gridType)
    }

    @Test
    fun migrate_gridOrdinalPastTheEnd_readsAsTheDefault() {
        commons().edit(commit = true) { putInt(GRID, GOLDEN_RATIO_ORDINAL + 1) }

        assertEquals(SettingsDefaults.GRID_TYPE, migrateCommon().gridType)
    }

    @Test
    fun migrate_focusTimeout_becomesSeconds() {
        commons().edit(commit = true) { putString(FOCUS_TIMEOUT, "10s") }

        assertEquals(10L, migrateCommon().focusTimeoutSeconds)

        commons().edit(commit = true) { putString(FOCUS_TIMEOUT, "Off") }

        assertEquals(0L, migrateCommon().focusTimeoutSeconds)
    }

    @Test
    fun migrate_photoQualityOfZero_readsAsTheDefault() {
        commons().edit(commit = true) { putInt(PHOTO_QUALITY, 0) }

        assertEquals(SettingsDefaults.PHOTO_QUALITY, migrateCommon().photoQuality)
    }

    @Test
    fun migrate_legacyQualityEmphasis_becomesTheHighestQuality() {
        commons().edit(commit = true) { putBoolean(EMPHASIS_ON_QUALITY, true) }

        assertEquals(MAX_PHOTO_QUALITY, migrateCommon().photoQuality)
    }

    @Test
    fun migrate_legacySpeedEmphasis_becomesTheDefaultQuality() {
        commons().edit(commit = true) { putBoolean(EMPHASIS_ON_QUALITY, false) }
        val current = SettingsPrefs(
            common = StoredCameraSettings(photoQuality = SOME_PHOTO_QUALITY),
        )

        val migrated = runBlocking { migration.migrate(current) }

        assertEquals(SettingsDefaults.PHOTO_QUALITY, migrated.common.photoQuality)
    }

    @Test
    fun migrate_qualityEmphasisAlongsideAChosenQuality_keepsTheChosenOne() {
        commons().edit(commit = true) {
            putBoolean(EMPHASIS_ON_QUALITY, true)
            putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY)
        }

        assertEquals(SOME_PHOTO_QUALITY, migrateCommon().photoQuality)
    }

    @Test
    fun migrate_installPredatingSaveAsPreviewed_keepsRecordingTheOldWay() {
        commons().edit(commit = true) { putBoolean(SAVE_IMAGE_AS_PREVIEW, true) }

        val migrated = migrateCommon()

        assertEquals(true, migrated.saveImageAsPreviewed)
        assertEquals(false, migrated.saveVideoAsPreviewed)
    }

    @Test
    fun migrate_settingsNobodyTouched_takeTheDeclaredDefault() {
        commons().edit(commit = true) { putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY) }

        val migrated = migrateCommon()

        assertEquals(SettingsDefaults.SAVE_IMAGE_AS_PREVIEW, migrated.saveImageAsPreviewed)
        assertEquals(SettingsDefaults.SAVE_VIDEO_AS_PREVIEW, migrated.saveVideoAsPreviewed)
        assertEquals(SettingsDefaults.ENABLE_EIS, migrated.enableEis)
        assertEquals(SettingsDefaults.ASPECT_RATIO, migrated.aspectRatio)
    }

    @Test
    fun migrate_barcodeFormatsNeverOpened_readAsTheDefault() {
        commons().edit(commit = true) { putBoolean(SCAN_ALL_CODES, true) }

        assertEquals(
            SettingsDefaults.ENABLED_BARCODE_FORMATS,
            migrateCommon().enabledBarcodeFormats,
        )
    }

    @Test
    fun migrate_barcodeFormats_carryOverOnlyTheEnabledOnes() {
        commons().edit(commit = true) {
            putBoolean(SCAN_ALL_CODES, true)
            putBoolean("scan_QR_CODE", true)
            putBoolean("scan_AZTEC", true)
            putBoolean("scan_CODE_39", false)
        }

        assertEquals(setOf("QR_CODE", "AZTEC"), migrateCommon().enabledBarcodeFormats)
    }

    @Test
    fun migrate_videoQualityStoredAsItsLabel_becomesTheNamedQuality() {
        modePreferences(MODE).edit(commit = true) {
            putString(VIDEO_QUALITY_BACK, "1080p (FHD)")
            putString(VIDEO_QUALITY_FRONT, "720p (HD)")
        }

        val migrated = migrate().modes.getValue(MODE.name)

        assertEquals(StoredVideoQuality.FHD, migrated.videoQualityBack)
        assertEquals(StoredVideoQuality.HD, migrated.videoQualityFront)
    }

    @Test
    fun migrate_unrecognisedVideoQualityLabel_keepsReadingAsItAlwaysHas() {
        modePreferences(MODE).edit(commit = true) { putString(VIDEO_QUALITY_BACK, "Unknown") }

        assertEquals(
            StoredVideoQuality.SD,
            migrate().modes.getValue(MODE.name).videoQualityBack,
        )
    }

    @Test
    fun migrate_perModeSettings_stayWithTheirOwnMode() {
        modePreferences(MODE).edit(commit = true) { putBoolean(GEO_TAGGING, true) }
        modePreferences(OTHER_MODE).edit(commit = true) { putInt(FLASH_MODE, SOME_FLASH_MODE) }

        val modes = migrate().modes

        assertEquals(
            StoredModeSettings(geoTagging = true),
            modes.getValue(MODE.name),
        )
        assertEquals(
            StoredModeSettings(flashMode = SOME_FLASH_MODE),
            modes.getValue(OTHER_MODE.name),
        )
    }

    @Test
    fun migrate_onlyPresentLegacyKeys_replaceCurrentData() {
        val futureMode = "A_MODE_FROM_THE_FUTURE"
        val current = SettingsPrefs(
            common = StoredCameraSettings(
                aspectRatio = 1,
                photoQuality = 55,
            ),
            modes = mapOf(
                MODE.name to StoredModeSettings(geoTagging = true),
                futureMode to StoredModeSettings(flashMode = 2),
            ),
        )
        commons().edit(commit = true) { putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY) }
        modePreferences(MODE).edit(commit = true) { putInt(FLASH_MODE, SOME_FLASH_MODE) }

        val migrated = runBlocking { migration.migrate(current) }

        assertEquals(1, migrated.common.aspectRatio)
        assertEquals(SOME_PHOTO_QUALITY, migrated.common.photoQuality)
        assertEquals(true, migrated.modes.getValue(MODE.name).geoTagging)
        assertEquals(SOME_FLASH_MODE, migrated.modes.getValue(MODE.name).flashMode)
        assertEquals(current.modes.getValue(futureMode), migrated.modes.getValue(futureMode))
    }

    @Test
    fun migrate_modeWithNothingStored_isNotCarriedOver() {
        modePreferences(MODE).edit(commit = true) { putBoolean(GEO_TAGGING, true) }

        assertEquals(setOf(MODE.name), migrate().modes.keys)
    }

    @Test
    fun cleanUp_leavesNoLegacyFileBehind() {
        commons().edit(commit = true) { putInt(PHOTO_QUALITY, SOME_PHOTO_QUALITY) }
        modePreferences(MODE).edit(commit = true) { putBoolean(GEO_TAGGING, true) }

        runBlocking {
            migration.migrate(SettingsPrefs())
            migration.cleanUp()
        }

        assertFalse(runBlocking { migration.shouldMigrate(SettingsPrefs()) })
        assertTrue(commons().all.isEmpty())
        assertTrue(modePreferences(MODE).all.isEmpty())
        assertFalse(commonsFileExists())
    }

    @Test
    fun cleanUp_removesOnlyItsOwnKeys() {
        writeLegacyPreferences(context)

        runBlocking {
            migration.migrate(SettingsPrefs())
            migration.cleanUp()
        }

        assertEquals(LEGACY_CAPTURE_KEY_NAMES + LEGACY_STORAGE_KEY_NAMES, commons().all.keys)
    }

    @Test
    fun cleanUp_anotherOwnersKeysRemain_keepsTheCommonsFile() {
        writeLegacyPreferences(context)

        runBlocking {
            migration.migrate(SettingsPrefs())
            migration.cleanUp()
        }

        assertTrue(commonsFileExists())
    }

    @Test
    fun shouldMigrate_onlyAnotherOwnersKeysArePresent_isFalse() {
        commons().edit(commit = true) {
            val owned = LEGACY_CAPTURE_KEY_NAMES + LEGACY_STORAGE_KEY_NAMES

            owned.forEach { putString(it, "whatever its owner stores here") }
        }

        assertFalse(runBlocking { migration.shouldMigrate(SettingsPrefs()) })
    }

    private companion object {
        val MODE = CameraMode.VIDEO
        val OTHER_MODE = CameraMode.CAMERA

        const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"
        const val FLASH_MODE = "flash_mode"
        const val FOCUS_TIMEOUT = "focus_timeout"
        const val GEO_TAGGING = "geo_tagging"
        const val GRID = "grid"
        const val PHOTO_QUALITY = "photo_quality"
        const val SAVE_IMAGE_AS_PREVIEW = "save_image_as_preview"
        const val SCAN_ALL_CODES = "scan_all_codes"
        const val VIDEO_QUALITY_BACK = "video_quality_BACK"
        const val VIDEO_QUALITY_FRONT = "video_quality_FRONT"

        const val GOLDEN_RATIO_ORDINAL = 3
        const val MAX_PHOTO_QUALITY = 100
        const val SOME_PHOTO_QUALITY = 71
        const val SOME_FLASH_MODE = 1
    }
}
