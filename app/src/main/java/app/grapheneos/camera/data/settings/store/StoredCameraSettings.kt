package app.grapheneos.camera.data.settings.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredCameraSettings(
    @SerialName("aspect_ratio")
    val aspectRatio: StoredAspectRatio? = null,
    @SerialName("grid_type")
    val gridType: StoredGridType? = null,
    @SerialName("focus_timeout_seconds")
    val focusTimeoutSeconds: Long? = null,
    @SerialName("volume_key_action")
    val volumeKeyAction: StoredVolumeKeyAction? = null,
    @SerialName("self_timer_duration_seconds")
    val selfTimerDurationSeconds: Int? = null,
    @SerialName("enable_camera_sounds")
    val enableCameraSounds: Boolean? = null,
    @SerialName("include_audio")
    val includeAudio: Boolean? = null,
    @SerialName("enable_eis")
    val enableEis: Boolean? = null,
    @SerialName("capture_mode")
    val captureMode: StoredCaptureMode? = null,
    @SerialName("enable_zsl")
    val enableZsl: Boolean? = null,
    @SerialName("wait_for_focus_lock")
    val waitForFocusLock: Boolean? = null,
    @SerialName("select_highest_resolution")
    val selectHighestResolution: Boolean? = null,
    @SerialName("photo_quality")
    val photoQuality: Int? = null,
    @SerialName("geo_tagging")
    val geoTagging: Boolean? = null,
    @SerialName("remove_exif_after_capture")
    val removeExifAfterCapture: Boolean? = null,
    @SerialName("gyroscope_suggestions")
    val gyroscopeSuggestions: Boolean? = null,
    @SerialName("keep_selfies_as_seen")
    val keepSelfiesAsSeen: Boolean? = null,
    @SerialName("save_image_as_previewed")
    val saveImageAsPreviewed: Boolean? = null,
    @SerialName("scan_all_codes")
    val scanAllCodes: Boolean? = null,
    @SerialName("enabled_barcode_formats")
    val enabledBarcodeFormats: Set<String>? = null,
)
