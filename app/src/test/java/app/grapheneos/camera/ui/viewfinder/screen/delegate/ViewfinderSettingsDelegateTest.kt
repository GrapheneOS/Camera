package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.PhotoResolutions
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.collectNavEvents
import app.grapheneos.camera.testutil.settingsRepositoryOver
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.state.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderRecordingState
import io.mockk.every
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderSettingsDelegateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val storedSettings = MutableStateFlow(CameraSettings())

    private val settingsRepository = settingsRepositoryOver(storedSettings)

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    @Test
    fun bind_startsFromWhatTheRepositoryHolds() {
        runTest {
            storedSettings.value = CameraSettings(gridType = GridType.FOUR_BY_FOUR)

            createDelegate()

            assertEquals(GridType.FOUR_BY_FOUR, stateHolder.state.value.settings.gridType)
        }
    }

    @Test
    fun settings_changedElsewhere_reachTheState() {
        runTest {
            createDelegate()

            storedSettings.value = CameraSettings(removeExifAfterCapture = false)

            assertFalse(stateHolder.state.value.settings.removeExifAfterCapture)
        }
    }

    @Test
    fun selectModeSlot_loadsThatSlotsSettings() {
        runTest {
            every { settingsRepository.modeSettings(SLOT) } returns SLOTTED

            val delegate = createDelegate()
            delegate.selectModeSlot(SLOT)

            assertEquals(SLOTTED, stateHolder.state.value.modeSettings)
        }
    }

    @Test
    fun modeSettings_changedElsewhere_reachTheStateForTheSlotInUse() {
        runTest {
            val slotted = MutableStateFlow(SLOTTED)
            val left = MutableStateFlow(ModeSettings())
            every { settingsRepository.modeSettings(SLOT) } returns SLOTTED
            every { settingsRepository.observeModeSettings(SLOT) } returns slotted
            every { settingsRepository.observeModeSettings(OTHER_SLOT) } returns left

            val delegate = createDelegate()
            delegate.selectModeSlot(OTHER_SLOT)
            delegate.selectModeSlot(SLOT)

            slotted.value = SLOTTED.copy(videoQuality = VideoQuality.UHD)
            left.value = ModeSettings(videoQuality = VideoQuality.SD)

            assertEquals(
                SLOTTED.copy(videoQuality = VideoQuality.UHD),
                stateHolder.state.value.modeSettings,
            )
        }
    }

    @Test
    fun rebindRequests_followAspectRatioWritesOnceTheStateHoldsThem() {
        runTest {
            val delegate = createDelegate()
            val ratiosAtRequest = mutableListOf<AspectRatio>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                delegate.rebindRequests().collect {
                    ratiosAtRequest += stateHolder.state.value.settings.aspectRatio
                }
            }

            settingsRepository.update { it.copy(aspectRatio = AspectRatio.RATIO_16_9) }
            settingsRepository.update { it.copy(aspectRatio = AspectRatio.RATIO_16_9) }

            assertEquals(listOf(AspectRatio.RATIO_16_9), ratiosAtRequest)
        }
    }

    @Test
    fun rebindRequests_followVideoQualityWritesButNotSlotSwitches() {
        runTest {
            val stored = MutableStateFlow(SLOTTED)
            every { settingsRepository.modeSettings(SLOT) } answers { stored.value }
            every { settingsRepository.observeModeSettings(SLOT) } returns stored
            val delegate = createDelegate()
            val qualitiesAtRequest = mutableListOf<VideoQuality>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                delegate.rebindRequests().collect {
                    qualitiesAtRequest += stateHolder.state.value.modeSettings.videoQuality
                }
            }

            delegate.selectModeSlot(SLOT)
            stored.value = SLOTTED.copy(videoQuality = VideoQuality.UHD)
            delegate.selectModeSlot(OTHER_SLOT)

            assertEquals(listOf(VideoQuality.UHD), qualitiesAtRequest)
        }
    }

    @Test
    fun rebindRequests_followFrameRateWrites() {
        runTest {
            val stored = MutableStateFlow(SLOTTED)
            every { settingsRepository.modeSettings(SLOT) } answers { stored.value }
            every { settingsRepository.observeModeSettings(SLOT) } returns stored
            val delegate = createDelegate()
            val frameRatesAtRequest = mutableListOf<VideoFrameRate>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                delegate.rebindRequests().collect {
                    frameRatesAtRequest += stateHolder.state.value.modeSettings.videoFrameRate
                }
            }

            delegate.selectModeSlot(SLOT)
            stored.value = SLOTTED.copy(videoFrameRate = VideoFrameRate.FPS_60)

            assertEquals(listOf(VideoFrameRate.FPS_60), frameRatesAtRequest)
        }
    }

    @Test
    fun videoLightWrites_followWritesToTheCurrentSlotButNotSlotSwitches() {
        runTest {
            val stored = MutableStateFlow(SLOTTED)
            every { settingsRepository.modeSettings(SLOT) } answers { stored.value }
            every { settingsRepository.observeModeSettings(SLOT) } returns stored
            val delegate = createDelegate()
            val writes = mutableListOf<Boolean>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                delegate.videoLightWrites().collect { enabled -> writes += enabled }
            }

            delegate.selectModeSlot(SLOT)
            stored.value = SLOTTED.copy(flashMode = FlashMode.ON)
            delegate.selectModeSlot(OTHER_SLOT)

            assertEquals(listOf(true), writes)
        }
    }

    @Test
    fun settingsPanel_isNavigatedToOutsideQrModeAndRecordings() {
        runTest {
            val delegate = createDelegate()
            val events = collectNavEvents(stateHolder)

            delegate.openSettingsPanel()
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.STARTING))
            }
            delegate.openSettingsPanel()
            stateHolder.update {
                it.copy(mode = CameraMode.QR_SCAN, recording = ViewfinderRecordingState())
            }
            delegate.openSettingsPanel()

            assertEquals(listOf(ViewfinderNavEvent.OpenSettingsPanel()), events)
        }
    }

    @Test
    fun settingsPanel_carriesTheSettingToHighlight() {
        runTest {
            val delegate = createDelegate()
            val events = collectNavEvents(stateHolder)

            delegate.openSettingsPanel(highlight = QuickSetting.SELF_TIMER)

            assertEquals(
                listOf(ViewfinderNavEvent.OpenSettingsPanel(highlight = QuickSetting.SELF_TIMER)),
                events,
            )
        }
    }

    @Test
    fun settingsPanel_closesOnSwitchingToQrScanning() {
        runTest {
            createDelegate()
            val events = collectNavEvents(stateHolder)
            stateHolder.update { it.copy(isSettingsPanelOpen = true) }

            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            assertEquals(listOf(ViewfinderNavEvent.CloseSettingsPanel), events)
        }
    }

    @Test
    fun settingsPanel_closesOnceARecordingOrTheSelfTimerStarts() {
        runTest {
            createDelegate()
            val events = collectNavEvents(stateHolder)
            stateHolder.update { it.copy(isSettingsPanelOpen = true) }

            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.STARTING))
            }
            stateHolder.update { it.copy(recording = ViewfinderRecordingState()) }
            stateHolder.update {
                it.copy(capture = it.capture.copy(selfTimerRemaining = 3.seconds))
            }

            assertEquals(
                listOf(
                    ViewfinderNavEvent.CloseSettingsPanel,
                    ViewfinderNavEvent.CloseSettingsPanel,
                ),
                events,
            )
        }
    }

    @Test
    fun settingsPanel_shownWhileUnavailable_isClosed() {
        runTest {
            createDelegate()
            val events = collectNavEvents(stateHolder)
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.STARTING))
            }

            stateHolder.update { it.copy(isSettingsPanelOpen = true) }

            assertEquals(listOf(ViewfinderNavEvent.CloseSettingsPanel), events)
        }
    }

    @Test
    fun capturing_withTheSettingsPanelClosed_navigatesNowhere() {
        runTest {
            createDelegate()
            val events = collectNavEvents(stateHolder)

            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.STARTING))
            }

            assertEquals(emptyList<ViewfinderNavEvent>(), events)
        }
    }

    @Test
    fun settingsPanel_isOpenOnlyWhileTheHostShowsIt() {
        runTest {
            val delegate = createDelegate()

            delegate.openSettingsPanel()
            assertFalse(stateHolder.state.value.isSettingsPanelOpen)

            delegate.onSettingsPanelVisibilityChanged(true)
            assertTrue(stateHolder.state.value.isSettingsPanelOpen)
        }
    }

    @Test
    fun openCameraSettings_opensThemWithWhatTheCameraSupports() {
        runTest {
            val delegate = createDelegate()
            val events = collectNavEvents(stateHolder)

            delegate.openCameraSettings(CAPABILITIES)

            assertEquals(
                listOf(ViewfinderNavEvent.OpenCameraSettings(CAPABILITIES)),
                events,
            )
        }
    }

    @Test
    fun openCameraSettings_whileRecording_saysSoInstead() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            val events = collectNavEvents(stateHolder)
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            delegate.openCameraSettings(CAPABILITIES)

            assertTrue(events.isEmpty())
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(
                        R.string.camera_settings_unavailable_during_recording,
                    ),
                ),
                effects,
            )
        }
    }

    private fun TestScope.createDelegate(): ViewfinderSettingsDelegate {
        val delegate = ViewfinderSettingsDelegateImpl(settingsRepository)

        delegate.bind(stateHolder)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            delegate.trackSettings()
        }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            delegate.trackModeSettings()
        }
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            delegate.trackSettingsPanelAvailability()
        }

        return delegate
    }

    private companion object {

        val CAPABILITIES = CameraCapabilities(
            lensFeatures = mapOf(
                LensFacing.BACK to LensFeatures(
                    isZslSupported = true,
                    isVideoStabilizationSupported = true,
                ),
            ),
            photoResolutions = PhotoResolutions(
                standardMegapixels = 12,
                fullMegapixels = 50,
            ),
        )

        val SLOT = ModeSlot(mode = CameraMode.VIDEO, lensFacing = LensFacing.BACK)
        val OTHER_SLOT = ModeSlot(mode = CameraMode.CAMERA, lensFacing = LensFacing.BACK)
        val SLOTTED = ModeSettings(selfIllumination = true, videoQuality = VideoQuality.FHD)
    }
}
