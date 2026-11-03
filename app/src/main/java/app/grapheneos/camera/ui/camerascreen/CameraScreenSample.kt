package app.grapheneos.camera.ui.camerascreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.components.capturebutton.CaptureButton
import app.grapheneos.camera.ui.components.lensswitchbutton.LensSwitchButton
import app.grapheneos.camera.ui.components.modeselector.ModeSelector
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.thumbnailbutton.ThumbnailButton
import app.grapheneos.camera.ui.core.gesture.SETTLE_VELOCITY
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.core.preview.CameraPreviewControl
import app.grapheneos.camera.ui.core.preview.PREVIEW_LENS_SWITCH_ICON
import app.grapheneos.camera.ui.core.preview.PREVIEW_SCENE
import app.grapheneos.camera.ui.core.preview.PREVIEW_WARM_ICON
import app.grapheneos.camera.ui.core.preview.previewShot
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.core.theme.SelfIlluminationTheme
import app.grapheneos.camera.ui.core.theme.cameraColors
import app.grapheneos.camera.ui.quicksettings.components.QuickSettingsSheetState
import app.grapheneos.camera.ui.quicksettings.components.RowHighlightState
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsContent
import app.grapheneos.camera.ui.quicksettings.screen.QuickSettingsDialogs
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsInsets
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import app.grapheneos.camera.ui.statusbar.screen.StatusBarContent
import app.grapheneos.camera.ui.statusbar.screen.mapper.StatusBarUiStateMapperImpl
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarUiState
import app.grapheneos.camera.ui.viewfinder.components.ViewfinderBottomRow

private const val SCREEN_WIDTH_DP = 376
private const val SCREEN_HEIGHT_DP = 835
private const val PREVIEW_ASPECT_RATIO = 3f / 4f

private val TOP_BAR_HEIGHT = 62.dp
private val NAVIGATION_BAR_HEIGHT = 24.dp
private val BOTTOM_ROW_BOTTOM_PADDING = 20.dp
private val BOTTOM_ROW_HEIGHT = 48.dp
private val MODES_BOTTOM_PADDING = 24.dp
private val SHUTTER_ROW_BOTTOM_PADDING = 18.dp
private val CONTROLS_PADDING = 8.dp

private val SAMPLE_MODES = listOf(
    CameraMode.CAMERA,
    CameraMode.VIDEO,
)
private val SAMPLE_QUALITIES = listOf(VideoQuality.UHD, VideoQuality.FHD, VideoQuality.HD)
private val SAMPLE_HIGH_FRAME_RATES = listOf(
    null,
    HighFrameRateSupport.Supported,
    HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.HDR),
    HighFrameRateSupport.Unavailable(limit = HighFrameRateLimit.RESOLUTION),
)

@Preview(widthDp = SCREEN_WIDTH_DP, heightDp = SCREEN_HEIGHT_DP)
@Composable
private fun CameraScreenSamplePreview() {
    CameraScreenSample()
}

@Preview(widthDp = 320, heightDp = SCREEN_HEIGHT_DP, fontScale = 2f)
@Composable
private fun CameraScreenLargeFontSamplePreview() {
    CameraScreenSample()
}

@Composable
private fun CameraScreenSample() {
    CameraTheme(darkTheme = true) {
        val state = remember { CameraScreenSampleState() }
        val sheetState = rememberSampleSheetState()
        val rowHighlight = remember { RowHighlightState() }

        LaunchedEffect(sheetState, state.isSheetOpen) {
            sheetState.show(state.isSheetOpen)
        }
        LaunchedEffect(state.highlight) {
            val setting = state.highlight ?: return@LaunchedEffect

            sheetState.awaitShown()
            rowHighlight.blink(setting)
            state.clearHighlight()
        }

        SampleLayers(
            state = state,
            sheetState = sheetState,
            rowHighlight = rowHighlight,
        )
        QuickSettingsDialogs(
            dialog = state.quickSettings.dialog,
            onAction = state::onQuickSettingsAction,
        )
    }
}

@Composable
private fun rememberSampleSheetState(): QuickSettingsSheetState {
    val density = LocalDensity.current

    return remember(density) {
        QuickSettingsSheetState(settleVelocity = with(density) { SETTLE_VELOCITY.toPx() })
    }
}

@Composable
private fun SampleLayers(
    state: CameraScreenSampleState,
    sheetState: QuickSettingsSheetState,
    rowHighlight: RowHighlightState,
) {
    val background by animateColorAsState(
        targetValue = when {
            state.statusBar.isSelfIlluminating -> MaterialTheme.cameraColors.selfIllumination
            else -> Color.Black
        },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(color = background),
    ) {
        val previewBottom = maxHeight - TOP_BAR_HEIGHT - maxWidth / PREVIEW_ASPECT_RATIO
        var shutterRow by remember { mutableStateOf(0.dp) }

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBarContent(
                uiState = state.statusBar,
                enabled = sheetState.isHidden,
                onItemClick = state::openSheet,
                onAction = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT),
            )
            SampleViewfinder(
                state = state,
                modifier = Modifier.clickable { state.closeSheet() },
            )
        }
        SelfIlluminationTheme(selfIlluminating = state.statusBar.isSelfIlluminating) {
            ViewfinderBottomRow(
                isVideoMode = state.quickSettings.isVideoMode,
                onQuickSettingsClick = { state.openSheet(highlight = null) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = NAVIGATION_BAR_HEIGHT + BOTTOM_ROW_BOTTOM_PADDING),
            )
        }
        SampleSheet(
            state = state,
            sheetState = sheetState,
            rowHighlight = rowHighlight,
            insets = QuickSettingsInsets(
                previewTop = TOP_BAR_HEIGHT,
                previewBottom = previewBottom,
                shutterRow = shutterRow,
            ),
        )
        SampleShutterControls(
            state = state,
            onShutterRowPlaced = { top -> shutterRow = maxHeight - top },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SampleSheet(
    state: CameraScreenSampleState,
    sheetState: QuickSettingsSheetState,
    rowHighlight: RowHighlightState,
    insets: QuickSettingsInsets,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.previewTop)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        QuickSettingsContent(
            uiState = state.quickSettings,
            sheetState = sheetState,
            rowHighlight = rowHighlight,
            insets = insets,
            onSettle = state::settleSheet,
            onAction = state::onQuickSettingsAction,
        )
    }
}

@Composable
private fun SampleViewfinder(
    state: CameraScreenSampleState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio = PREVIEW_ASPECT_RATIO)
            .background(color = PREVIEW_SCENE),
    ) {
        FlowRow(
            modifier = Modifier.padding(all = CONTROLS_PADDING),
        ) {
            CameraPreviewControl(
                text = state.flashUnitLabel,
                onClick = state::toggleFlashUnit,
            )
            CameraPreviewControl(
                text = state.lowLightBoostLabel,
                onClick = state::toggleLowLightBoost,
            )
            CameraPreviewControl(
                text = state.highFrameRateLabel,
                onClick = state::nextHighFrameRate,
            )
        }
    }
}

@Composable
private fun SampleShutterControls(
    state: CameraScreenSampleState,
    onShutterRowPlaced: (top: Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shot = remember { previewShot(top = PREVIEW_WARM_ICON, bottom = PREVIEW_SCENE) }

    Column(
        modifier = modifier.padding(
            bottom = NAVIGATION_BAR_HEIGHT + BOTTOM_ROW_BOTTOM_PADDING + BOTTOM_ROW_HEIGHT,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced { coordinates ->
                    onShutterRowPlaced(with(density) { coordinates.positionInRoot().y.toDp() })
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailButton(
                image = shot,
                onClick = {},
            )
            CaptureButton(
                onClick = {},
                core = ShutterCore.Disc,
            )
            LensSwitchButton(
                flipped = state.isFront,
                onClick = state::flipLens,
                icon = PREVIEW_LENS_SWITCH_ICON,
            )
        }
        Spacer(modifier = Modifier.height(SHUTTER_ROW_BOTTOM_PADDING))
        ModeSelector(
            labels = SAMPLE_MODES.map { mode -> mode.name },
            selectedIndex = state.modeIndex,
            onModeSelected = state::selectMode,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MODES_BOTTOM_PADDING))
    }
}

@Stable
private class CameraScreenSampleState {

    var modeIndex by mutableIntStateOf(SAMPLE_MODES.indexOf(CameraMode.CAMERA))
        private set
    var isFront by mutableStateOf(false)
        private set
    var isSheetOpen by mutableStateOf(false)
        private set
    var highlight by mutableStateOf<QuickSetting?>(null)
        private set

    private var hasFlashUnit by mutableStateOf(true)
    private var isLowLightBoostSupported by mutableStateOf(true)
    private var highFrameRateIndex by mutableIntStateOf(1)
    private var settings by mutableStateOf(CameraSettings())
    private var modeSettings by mutableStateOf(ModeSettings())
    private var shownLimit by mutableStateOf<OptionLimit?>(null)

    private val quickSettingsMapper = QuickSettingsUiStateMapperImpl()
    private val statusBarMapper = StatusBarUiStateMapperImpl()

    val quickSettings: QuickSettingsUiState
        get() {
            return quickSettingsMapper.map(
                QuickSettingsState(
                    settings = settings,
                    modeSettings = modeSettings,
                    boundCamera = boundCamera(),
                    shownLimit = shownLimit,
                ),
            )
        }

    val statusBar: StatusBarUiState
        get() {
            return statusBarMapper.map(
                StatusBarState(
                    settings = settings,
                    modeSettings = modeSettings,
                    boundCamera = boundCamera(),
                ),
            )
        }

    val flashUnitLabel: String
        get() {
            return "Flash unit: ${onOff(hasFlashUnit)}"
        }

    val lowLightBoostLabel: String
        get() {
            return "Low light boost: ${onOff(isLowLightBoostSupported)}"
        }

    val highFrameRateLabel: String
        get() {
            val label = when (val support = SAMPLE_HIGH_FRAME_RATES[highFrameRateIndex]) {
                null -> "unchecked"
                HighFrameRateSupport.Supported -> "supported"
                is HighFrameRateSupport.Unavailable -> support.limit.name
            }

            return "60 fps: $label"
        }

    fun selectMode(index: Int) {
        modeIndex = index
    }

    fun flipLens() {
        isFront = !isFront
    }

    fun toggleFlashUnit() {
        hasFlashUnit = !hasFlashUnit
    }

    fun toggleLowLightBoost() {
        isLowLightBoostSupported = !isLowLightBoostSupported
    }

    fun nextHighFrameRate() {
        highFrameRateIndex = (highFrameRateIndex + 1) % SAMPLE_HIGH_FRAME_RATES.size
    }

    fun openSheet(highlight: QuickSetting?) {
        isSheetOpen = true
        this.highlight = highlight
    }

    fun closeSheet() {
        isSheetOpen = false
    }

    fun settleSheet(isShown: Boolean) {
        isSheetOpen = isShown
    }

    fun clearHighlight() {
        highlight = null
    }

    fun onQuickSettingsAction(action: QuickSettingsAction) {
        when (action) {
            is QuickSettingsAction.CameraSettingsClicked -> Unit
            is QuickSettingsAction.CameraSettingsLongClicked -> Unit
            is QuickSettingsAction.LimitClicked -> shownLimit = action.limit
            is QuickSettingsAction.LimitDismissed -> shownLimit = null
            is QuickSettingsAction.FlashModeSelected -> {
                updateMode { it.copy(flashMode = action.mode) }
            }

            is QuickSettingsAction.SelfIlluminationToggled -> {
                updateMode { it.copy(selfIllumination = action.enabled) }
            }

            is QuickSettingsAction.SelfTimerSelected -> {
                settings = settings.copy(selfTimerDuration = action.duration)
            }

            is QuickSettingsAction.AspectRatioSelected -> {
                settings = settings.copy(aspectRatio = action.ratio)
            }

            is QuickSettingsAction.VideoQualitySelected -> {
                updateMode { it.copy(videoQuality = action.quality) }
            }

            is QuickSettingsAction.VideoFrameRateSelected -> {
                updateMode { it.copy(videoFrameRate = action.frameRate) }
            }

            is QuickSettingsAction.AudioToggled -> {
                settings = settings.copy(includeAudio = action.enabled)
            }
        }
    }

    private fun updateMode(transform: (ModeSettings) -> ModeSettings) {
        modeSettings = transform(modeSettings)
    }

    private fun boundCamera(): BoundCamera {
        val mode = SAMPLE_MODES[modeIndex]

        return BoundCamera(
            mode = mode,
            isVideoMode = mode.isVideo,
            lensFacing = when {
                isFront -> LensFacing.FRONT
                else -> LensFacing.BACK
            },
            hasFlashUnit = hasFlashUnit,
            videoQualities = SAMPLE_QUALITIES,
            isLowLightBoostSupported = isLowLightBoostSupported,
            highFrameRate = SAMPLE_HIGH_FRAME_RATES[highFrameRateIndex].takeIf { mode.isVideo },
        )
    }

    private fun onOff(value: Boolean): String {
        return when {
            value -> "on"
            else -> "off"
        }
    }
}
