package app.grapheneos.camera.ui.quicksettings.screen

import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.HighFrameRateLimit
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import app.grapheneos.camera.domain.clipboard.usecase.CopyText
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapperImpl
import app.grapheneos.camera.ui.quicksettings.screen.model.OptionLimit
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsDialog as Dialog
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsNavEvent as NavEvent
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val storedModeSettings = mutableMapOf<ModeSlot, ModeSettings>()

    private val settingsRepository = settingsRepositoryOver(
        storedSettings = storedSettings,
        storedModeSettings = storedModeSettings,
    )

    private val boundCamera = MutableStateFlow<BoundCamera?>(PHOTO_CAMERA)

    private val session = mockk<CameraSession>(relaxed = true) {
        every { boundCamera } returns this@QuickSettingsViewModelTest.boundCamera
        every { capabilities() } returns CAPABILITIES
    }

    private val copyText = mockk<CopyText>()

    @Test
    fun modeSettings_followTheSlotOfTheBoundCamera() {
        runTest {
            storedModeSettings[FRONT_VIDEO_SLOT] = STORED_FRONT_VIDEO
            val viewModel = viewModel()

            boundCamera.value = FRONT_VIDEO_CAMERA

            assertEquals(VideoQuality.HD, viewModel.uiState.value.videoQuality?.selected)
        }
    }

    @Test
    fun flashModeSelected_storesItForTheBoundSlot() {
        runTest {
            storedModeSettings[PHOTO_SLOT] = ModeSettings(flashMode = FlashMode.OFF)
            val viewModel = viewModel()

            viewModel.onAction(Action.FlashModeSelected(FlashMode.ON))

            assertEquals(FlashMode.ON, storedModeSettings.getValue(PHOTO_SLOT).flashMode)
        }
    }

    @Test
    fun flashModeSelected_inVideo_storesItForTheBoundVideoSlot() {
        runTest {
            boundCamera.value = FRONT_VIDEO_CAMERA
            storedModeSettings[FRONT_VIDEO_SLOT] = ModeSettings(flashMode = FlashMode.AUTO)
            val viewModel = viewModel()

            viewModel.onAction(Action.FlashModeSelected(FlashMode.LOW_LIGHT_BOOST))

            assertEquals(
                FlashMode.LOW_LIGHT_BOOST,
                storedModeSettings.getValue(FRONT_VIDEO_SLOT).flashMode,
            )
        }
    }

    @Test
    fun videoFrameRateSelected_storesItForTheBoundSlot() {
        runTest {
            boundCamera.value = FRONT_VIDEO_CAMERA
            storedModeSettings[FRONT_VIDEO_SLOT] = STORED_FRONT_VIDEO
            val viewModel = viewModel()

            viewModel.onAction(Action.VideoFrameRateSelected(VideoFrameRate.FPS_60))

            assertEquals(
                VideoFrameRate.FPS_60,
                storedModeSettings.getValue(FRONT_VIDEO_SLOT).videoFrameRate,
            )
        }
    }

    @Test
    fun limitClicked_explainsTheLimitUntilDismissed() {
        runTest {
            val limit = OptionLimit.HighFrameRate(limit = HighFrameRateLimit.RESOLUTION)
            boundCamera.value = FRONT_VIDEO_CAMERA.copy(
                highFrameRate = HighFrameRateSupport.Unavailable(limit = limit.limit),
            )
            val viewModel = viewModel()
            viewModel.onAction(Action.LimitClicked(limit))

            val shown = viewModel.uiState.value.dialog
            viewModel.onAction(Action.LimitDismissed)

            assertEquals(Dialog.OptionUnavailable(limit = limit), shown)
            assertNull(viewModel.uiState.value.dialog)
        }
    }

    @Test
    fun aspectRatioSelected_storesIt() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.AspectRatioSelected(AspectRatio.RATIO_16_9))

            assertEquals(AspectRatio.RATIO_16_9, storedSettings.value.aspectRatio)
        }
    }

    @Test
    fun selfTimerSelected_storesIt() {
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(Action.SelfTimerSelected(SELF_TIMER))

            assertEquals(SELF_TIMER, storedSettings.value.selfTimerDuration)
        }
    }

    @Test
    fun videoQualitySelected_storesItForTheBoundSlot() {
        runTest {
            boundCamera.value = FRONT_VIDEO_CAMERA
            val viewModel = viewModel()

            viewModel.onAction(Action.VideoQualitySelected(VideoQuality.UHD))

            assertEquals(
                VideoQuality.UHD,
                storedModeSettings.getValue(FRONT_VIDEO_SLOT).videoQuality,
            )
        }
    }

    @Test
    fun modeWrites_beforeACameraIsBound_areDropped() {
        runTest {
            boundCamera.value = null
            val viewModel = viewModel()

            viewModel.onAction(Action.SelfIlluminationToggled(enabled = true))

            assertEquals(emptyMap<ModeSlot, ModeSettings>(), storedModeSettings)
        }
    }

    @Test
    fun cameraSettingsClicked_opensThemForThisSessionWithWhatTheCameraSupports() {
        runTest {
            val entryPoint = cameraEntryPoint(isSecureSession = true)
            val viewModel = viewModel(entryPoint = entryPoint)
            val events = collectNavigation(viewModel)
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.CameraSettingsClicked)
            assertEquals(emptyList<Effect>(), effects)

            assertEquals(
                listOf(
                    NavEvent.OpenCameraSettings(
                        entryPoint = entryPoint,
                        capabilities = CAPABILITIES,
                    ),
                ),
                events,
            )
        }
    }

    @Test
    fun cameraSettingsLongClicked_copiesTheVersionAndConfirmsWhereTheSystemDoesNot() {
        runTest {
            every { copyText(COPIED_VERSION) } returns CopyTextResult.Unconfirmed
            val viewModel = viewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.CameraSettingsLongClicked)

            verify { copyText(COPIED_VERSION) }
            assertEquals(listOf(Effect.TextCopied), effects)
        }
    }

    @Test
    fun cameraSettingsLongClicked_leavesTheConfirmationToTheSystemWhereItShowsOne() {
        runTest {
            every { copyText(any()) } returns CopyTextResult.ConfirmedBySystem
            val viewModel = viewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(Action.CameraSettingsLongClicked)

            assertEquals(emptyList<Effect>(), effects)
        }
    }

    private fun viewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): QuickSettingsViewModel {
        return QuickSettingsViewModel(
            entryPoint = entryPoint,
            settingsRepository = settingsRepository,
            session = session,
            copyText = copyText,
            appLabel = { APP_LABEL },
            uiStateMapper = QuickSettingsUiStateMapperImpl(),
        )
    }

    private fun TestScope.collectEffects(viewModel: QuickSettingsViewModel): List<Effect> {
        val effects = mutableListOf<Effect>()

        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.effects.toList(effects)
        }

        return effects
    }

    private fun TestScope.collectNavigation(viewModel: QuickSettingsViewModel): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.navigationEvents.toList(events)
        }
        return events
    }

    private companion object {
        const val APP_LABEL = "Camera"
        const val COPIED_VERSION = "$APP_LABEL version ${BuildConfig.VERSION_NAME}"

        val SELF_TIMER = 5.seconds

        val CAPABILITIES = CameraCapabilities(
            lensFacing = LensFacing.BACK,
        )

        val PHOTO_CAMERA = BoundCamera(
            mode = CameraMode.CAMERA,
            isVideoMode = false,
            lensFacing = LensFacing.BACK,
            hasFlashUnit = true,
            videoQualities = emptyList(),
        )

        val FRONT_VIDEO_CAMERA = BoundCamera(
            mode = CameraMode.VIDEO,
            isVideoMode = true,
            lensFacing = LensFacing.FRONT,
            hasFlashUnit = false,
            videoQualities = listOf(VideoQuality.FHD, VideoQuality.HD),
        )

        val PHOTO_SLOT = ModeSlot(mode = CameraMode.CAMERA, lensFacing = LensFacing.BACK)
        val FRONT_VIDEO_SLOT = ModeSlot(mode = CameraMode.VIDEO, lensFacing = LensFacing.FRONT)

        val STORED_FRONT_VIDEO = ModeSettings(videoQuality = VideoQuality.HD)
    }
}
