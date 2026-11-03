package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureButtonUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.CaptureResultUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ExposureUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FlipCameraUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.FocusRingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ModeTabsUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.PreviewUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.QrUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RecordingUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.RotationUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.SelfTimerUiModel
import app.grapheneos.camera.ui.viewfinder.screen.model.ui.ThirdOptionUiModel

data class ViewfinderUiState(
    val mode: CameraMode = CameraMode.CAMERA,
    val preview: PreviewUiModel = PreviewUiModel(),
    val modeTabs: ModeTabsUiModel = ModeTabsUiModel(),
    val captureButton: CaptureButtonUiModel = CaptureButtonUiModel(),
    val flipCamera: FlipCameraUiModel = FlipCameraUiModel(),
    val thirdOption: ThirdOptionUiModel = ThirdOptionUiModel(),
    val cancelButtonVisible: Boolean = true,
    val selfTimer: SelfTimerUiModel = SelfTimerUiModel(),
    val recording: RecordingUiModel = RecordingUiModel(),
    val qr: QrUiModel = QrUiModel(),
    val focusRing: FocusRingUiModel? = null,
    val exposure: ExposureUiModel = ExposureUiModel(),
    val rotation: RotationUiModel = RotationUiModel(),
    val captureResult: CaptureResultUiModel = CaptureResultUiModel(),
    val keepScreenOn: Boolean = false,
    val isSelfIlluminating: Boolean = false,
    val isSettingsPanelAvailable: Boolean = false,
    val handlesVolumeKeys: Boolean = true,
    val cameraSoundsEnabled: Boolean = SettingsDefaults.CAMERA_SOUNDS,
    val dialog: ViewfinderDialog? = null,
)
