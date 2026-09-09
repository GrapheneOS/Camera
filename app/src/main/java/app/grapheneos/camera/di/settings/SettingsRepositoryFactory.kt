package app.grapheneos.camera.di.settings

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepositoryImpl
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SettingsRepositoryFactory @Inject constructor(
    private val cameraSettingsMapper: CameraSettingsMapper,
    private val modeSettingsMapper: ModeSettingsMapper,
    private val storedVideoQualityMapper: StoredVideoQualityMapper,
) {

    fun create(dataStore: DataStore<SettingsPrefs>): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = modeSettingsMapper,
            storedVideoQualityMapper = storedVideoQualityMapper,
        )
    }
}
