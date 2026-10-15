package app.grapheneos.camera.ui.camerasettings.screen.mapper

import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsUiState
import app.grapheneos.camera.ui.camerasettings.screen.model.CaptureModeOption
import app.grapheneos.camera.ui.camerasettings.screen.model.ChoiceSettingUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.GridOption
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityLevel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoQualityUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.PhotoResolutionUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageLocationUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.StorageSpaceUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.SwitchSettingUiModel
import app.grapheneos.camera.ui.camerasettings.screen.model.VolumeKeyActionOption
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

internal interface CameraSettingsUiStateMapper {
    fun map(state: CameraSettingsState): CameraSettingsUiState
}

internal class CameraSettingsUiStateMapperImpl @Inject constructor() : CameraSettingsUiStateMapper {

    override fun map(state: CameraSettingsState): CameraSettingsUiState {
        val settings = state.settings

        return CameraSettingsUiState(
            showsSecureSessionBanner = state.isSecureSession,
            geoTagging = geoTagging(state),
            offersLocationSettings = settings.geoTagging &&
                !state.isLocationPermissionGranted &&
                !state.isSecureSession,
            removeExif = removeExif(state),
            grid = grid(state),
            gyroscopeSuggestions = switchSetting(
                checked = settings.gyroscopeSuggestions,
                default = SettingsDefaults.GYROSCOPE_SUGGESTIONS,
            ),
            keepSelfiesAsSeen = switchSetting(
                checked = settings.keepSelfiesAsSeen,
                default = SettingsDefaults.KEEP_SELFIES_AS_SEEN,
            ),
            photoQuality = photoQuality(state),
            captureMode = captureMode(state),
            videoStabilization = videoStabilization(state),
            hdrVideo = hdrVideo(state),
            cameraSounds = switchSetting(
                checked = settings.enableCameraSounds,
                default = SettingsDefaults.CAMERA_SOUNDS,
            ),
            focusTimeout = focusTimeout(state),
            volumeKeyAction = volumeKeyAction(state),
            timerLight = switchSetting(
                checked = settings.timerLight,
                default = SettingsDefaults.TIMER_LIGHT,
            ),
            storageSpace = storageSpace(state),
            storageLocation = storageLocation(state),
            showsLicenses = !state.isSecureSession,
            canResetSettings = state.settingsAfterReset() != settings,
            dialog = state.dialog,
        )
    }

    private fun geoTagging(state: CameraSettingsState): SwitchSettingUiModel {
        val setting = switchSetting(
            checked = state.settings.geoTagging,
            default = SettingsDefaults.GEO_TAGGING,
        )

        return setting.copy(enabled = state.isLocationPermissionGranted || !state.isSecureSession)
    }

    private fun removeExif(state: CameraSettingsState): SwitchSettingUiModel {
        return when {
            state.isCaptureSession -> SwitchSettingUiModel(
                checked = true,
                enabled = false,
            )

            else -> switchSetting(
                checked = state.settings.removeExifAfterCapture,
                default = SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE,
            )
        }
    }

    private fun grid(state: CameraSettingsState): ChoiceSettingUiModel<GridOption> {
        val selected = GridOption.of(state.settings.gridType)

        return ChoiceSettingUiModel(
            selected = selected,
            options = GridOption.entries.toImmutableList(),
            isChanged = selected.type != SettingsDefaults.GRID_TYPE,
        )
    }

    private fun photoQuality(state: CameraSettingsState): PhotoQualityUiModel {
        val quality = state.settings.photoQuality
        val isFullResolution = state.settings.selectHighestResolution
        val resolution = state.capabilities.photoResolutions?.let { resolutions ->
            PhotoResolutionUiModel(
                standardMegapixels = resolutions.standardMegapixels,
                fullMegapixels = resolutions.fullMegapixels,
            )
        }

        return PhotoQualityUiModel(
            quality = quality,
            level = PhotoQualityLevel.of(quality),
            isFullResolution = isFullResolution,
            resolution = resolution,
            isChanged = quality != SettingsDefaults.PHOTO_QUALITY || isFullResolution,
            presets = PhotoQualityLevel.presets().toImmutableList(),
        )
    }

    private fun captureMode(state: CameraSettingsState): ChoiceSettingUiModel<CaptureModeOption> {
        val selected = CaptureModeOption.of(state.settings.captureMode)
        val options = CaptureModeOption.entries.filter { option ->
            option != CaptureModeOption.ZERO_SHUTTER_LAG ||
                state.capabilities.isZslSupported ||
                option == selected
        }

        return ChoiceSettingUiModel(
            selected = selected,
            options = options.toImmutableList(),
            isChanged = selected.mode != SettingsDefaults.CAPTURE_MODE,
        )
    }

    private fun hdrVideo(state: CameraSettingsState): SwitchSettingUiModel? {
        if (!state.capabilities.isHdrVideoSupported) return null

        return switchSetting(
            checked = state.settings.hdrVideo,
            default = SettingsDefaults.HDR_VIDEO,
        )
    }

    private fun videoStabilization(state: CameraSettingsState): SwitchSettingUiModel {
        val setting = switchSetting(
            checked = state.settings.enableEis,
            default = SettingsDefaults.ENABLE_EIS,
        )

        return setting.copy(enabled = state.capabilities.isVideoStabilizationSupported)
    }

    private fun focusTimeout(state: CameraSettingsState): ChoiceSettingUiModel<Long> {
        val selected = state.settings.focusTimeoutSeconds
        val options = (FOCUS_TIMEOUT_OPTIONS + selected).distinct().sorted()

        return ChoiceSettingUiModel(
            selected = selected,
            options = options.toImmutableList(),
            isChanged = selected != SettingsDefaults.FOCUS_TIMEOUT_SECONDS,
        )
    }

    private fun volumeKeyAction(
        state: CameraSettingsState,
    ): ChoiceSettingUiModel<VolumeKeyActionOption> {
        val selected = VolumeKeyActionOption.of(state.settings.volumeKeyAction)

        return ChoiceSettingUiModel(
            selected = selected,
            options = VolumeKeyActionOption.entries.toImmutableList(),
            isChanged = selected.action != SettingsDefaults.VOLUME_KEY_ACTION,
        )
    }

    private fun storageSpace(state: CameraSettingsState): StorageSpaceUiModel? {
        if (state.isSecureSession) return null

        val space = state.storageSpace

        return when {
            !state.isStorageSpaceLoaded -> StorageSpaceUiModel()
            space == null -> null
            else -> StorageSpaceUiModel(
                freeBytes = space.freeBytes,
                usedFraction = 1f - space.freeBytes.toFloat() / space.totalBytes,
                photos = photoBytes(state)?.let { photoBytes -> space.freeBytes / photoBytes },
                videoMinutes = videoBytesPerMinute(state)?.let { bytesPerMinute ->
                    space.freeBytes / bytesPerMinute
                },
            )
        }
    }

    private fun photoBytes(state: CameraSettingsState): Long? {
        val capabilities = state.capabilities
        val fullMegapixels = capabilities.photoResolutions?.fullMegapixels
            ?.takeIf { state.settings.selectHighestResolution }
        val megapixels = fullMegapixels ?: capabilities.photoMegapixels ?: return null
        val bits = megapixels * PIXELS_PER_MEGAPIXEL * jpegBitsPerPixel(state.settings.photoQuality)

        return (bits / BITS_PER_BYTE).toLong().takeIf { it > 0 }
    }

    private fun jpegBitsPerPixel(quality: Int): Double {
        val lower = JPEG_BITS_PER_PIXEL.last { (level, _) -> level <= quality }
        val upper = JPEG_BITS_PER_PIXEL.first { (level, _) -> level >= quality }
        val fraction = when (upper.first) {
            lower.first -> 0.0
            else -> (quality - lower.first).toDouble() / (upper.first - lower.first)
        }

        return lower.second + (upper.second - lower.second) * fraction
    }

    private fun videoBytesPerMinute(state: CameraSettingsState): Long? {
        val bitrates = state.capabilities.videoBitrates[state.videoQuality] ?: return null
        val audioBitsPerSecond = when {
            state.settings.includeAudio -> bitrates.audioBitsPerSecond
            else -> 0
        }
        val profileBitsPerSecond = bitrates.videoBitsPerSecond.toLong()
        val videoBitsPerSecond = when {
            bitrates.videoFrameRate <= 0 -> profileBitsPerSecond
            else -> profileBitsPerSecond * RECORDING_FRAME_RATE / bitrates.videoFrameRate
        }
        val bitsPerSecond = videoBitsPerSecond + audioBitsPerSecond

        return (bitsPerSecond * SECONDS_PER_MINUTE / BITS_PER_BYTE).takeIf { it > 0 }
    }

    private fun storageLocation(state: CameraSettingsState): StorageLocationUiModel? {
        val location = state.storageLocation.takeUnless { state.isSecureSession } ?: return null

        return StorageLocationUiModel(
            location = location,
            isChanged = location != CapturedItemRepository.MEDIA_STORE_LOCATION,
        )
    }

    private fun switchSetting(
        checked: Boolean,
        default: Boolean,
    ): SwitchSettingUiModel {
        return SwitchSettingUiModel(
            checked = checked,
            isChanged = checked != default,
        )
    }

    private companion object {
        // A typical photo's: a JPEG's size depends on the scene as much as on its quality.
        val JPEG_BITS_PER_PIXEL = listOf(
            1 to 0.2,
            75 to 1.0,
            90 to 1.5,
            95 to 2.5,
            100 to 5.5,
        )
        val FOCUS_TIMEOUT_OPTIONS = listOf(0L, 3L, 5L, 8L, 10L)

        // With no frame rate set, CameraX records at 30 fps and scales the profile's bitrate to it.
        const val RECORDING_FRAME_RATE = 30
        const val SECONDS_PER_MINUTE = 60
        const val BITS_PER_BYTE = 8
        const val PIXELS_PER_MEGAPIXEL = 1_000_000
    }
}
