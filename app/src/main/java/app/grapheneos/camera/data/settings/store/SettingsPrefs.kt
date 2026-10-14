package app.grapheneos.camera.data.settings.store

import androidx.datastore.core.Serializer
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.store.JsonPreferenceSerializer
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsPrefs(
    val common: StoredCameraSettings = StoredCameraSettings(),
    val modes: Map<String, StoredModeSettings> = emptyMap(),
) {
    fun mode(mode: CameraMode): StoredModeSettings {
        return modes[storedModeName(mode)] ?: StoredModeSettings()
    }

    fun withMode(mode: CameraMode, settings: StoredModeSettings): SettingsPrefs {
        return copy(modes = modes + (storedModeName(mode) to settings))
    }

    companion object {
        fun storedModeName(mode: CameraMode): String {
            return when (mode) {
                CameraMode.QR_SCAN -> "QR_SCAN"
                CameraMode.AUTO -> "AUTO"
                CameraMode.FACE_RETOUCH -> "FACE_RETOUCH"
                CameraMode.PORTRAIT -> "PORTRAIT"
                CameraMode.NIGHT -> "NIGHT"
                CameraMode.HDR -> "HDR"
                CameraMode.CAMERA -> "CAMERA"
                CameraMode.VIDEO -> "VIDEO"
            }
        }
    }
}

internal val settingsPrefsSerializer: Serializer<SettingsPrefs> = JsonPreferenceSerializer(
    serializer = SettingsPrefs.serializer(),
    defaultValue = SettingsPrefs(),
)
