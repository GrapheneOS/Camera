package app.grapheneos.camera.data.settings

import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapperImpl
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.store.StoredCameraSettings
import app.grapheneos.camera.data.settings.store.StoredGridType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraSettingsMapperTest {

    private val mapper: CameraSettingsMapper = CameraSettingsMapperImpl()

    @Test
    fun storedValues_mapToTheFeatureModel() {
        val stored = StoredCameraSettings(
            gridType = StoredGridType.GOLDEN_RATIO,
            photoQuality = SOME_PHOTO_QUALITY,
        )

        val settings = mapper.map(stored)

        assertEquals(GridType.GOLDEN_RATIO, settings.gridType)
        assertEquals(SOME_PHOTO_QUALITY, settings.photoQuality)
    }

    @Test
    fun featureDefaults_areAbsentFromStorage() {
        assertEquals(StoredCameraSettings(), mapper.map(CameraSettings()))
    }

    private companion object {
        const val SOME_PHOTO_QUALITY = 71
    }
}
