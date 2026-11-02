package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class ViewfinderUiState(
    val mode: CameraMode = CameraMode.CAMERA,
    val isVideoMode: Boolean = false,
    val preview: PreviewUiState = PreviewUiState(),
    val modeTabs: ModeTabsUiState = ModeTabsUiState(),
    val captureButton: CaptureButtonUiState = CaptureButtonUiState(),
    val flipCamera: FlipCameraUiState = FlipCameraUiState(),
    val thirdOption: ThirdOptionUiState = ThirdOptionUiState(),
    val cancelButtonVisible: Boolean = true,
    val selfTimer: SelfTimerUiState = SelfTimerUiState(),
    val recording: RecordingUiState = RecordingUiState(),
    val qr: QrUiState = QrUiState(),
    val exposure: ExposureUiState = ExposureUiState(),
    val rotation: RotationUiState = RotationUiState(),
    val capturedPreviewVisible: Boolean = false,
    val keepScreenOn: Boolean = false,
    val isSelfIlluminating: Boolean = false,
    val isSettingsPanelAvailable: Boolean = false,
    val handlesVolumeKeys: Boolean = true,
    val cameraSoundsEnabled: Boolean = SettingsDefaults.CAMERA_SOUNDS,
    val dialog: ViewfinderDialog? = null,
)
