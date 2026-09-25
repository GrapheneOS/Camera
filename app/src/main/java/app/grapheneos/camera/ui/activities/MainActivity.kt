package app.grapheneos.camera.ui.activities

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.util.Linkify
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import app.grapheneos.camera.App
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.databinding.ActivityMainBinding
import app.grapheneos.camera.databinding.ScanResultDialogBinding
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.coordinator.CapturedItemSession
import app.grapheneos.camera.domain.qr.BarcodeFormats
import app.grapheneos.camera.ktx.SystemSettingsObserver
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.notifier.SensorOrientationChangeNotifier
import app.grapheneos.camera.shareCapturedItem
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.CaptureButton
import app.grapheneos.camera.ui.CountDownTimerUI
import app.grapheneos.camera.ui.CustomGrid
import app.grapheneos.camera.ui.QROverlay
import app.grapheneos.camera.ui.QRToggle
import app.grapheneos.camera.ui.SettingsDialog
import app.grapheneos.camera.ui.seekbar.ExposureBar
import app.grapheneos.camera.ui.seekbar.ZoomBar
import app.grapheneos.camera.ui.showIgnoringShortEdgeMode
import app.grapheneos.camera.ui.showMoreQrFormatOptions
import app.grapheneos.camera.ui.viewfinder.ViewfinderGestureHandler
import app.grapheneos.camera.ui.viewfinder.ViewfinderOrientationHandler
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolder
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolderImpl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChromeImpl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffectHandler
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffectHandlerImpl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewModel
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewRenderer
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.util.ImageResizer
import app.grapheneos.camera.util.executeIfAlive
import app.grapheneos.camera.util.getVideoThumbnail
import app.grapheneos.camera.util.resolveActivity
import app.grapheneos.camera.util.setBlurBitmapCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.AndroidEntryPoint
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var cameraEntryPoint: CameraEntryPoint

    @Inject
    lateinit var barcodeFormats: BarcodeFormats

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var capturedItemSession: CapturedItemSession

    val viewfinder: ViewfinderViewModel by viewModels(
        extrasProducer = { viewfinderCreationExtras() },
    )

    @Inject
    lateinit var session: CameraSession

    @Inject
    lateinit var clipboardManager: ClipboardManager

    @Inject
    lateinit var notificationManager: NotificationManager

    private val application: App
        get() = applicationContext as App

    internal lateinit var binding: ActivityMainBinding

    internal val gestureHandler by lazy { ViewfinderGestureHandler(this) }
    internal val orientationHandler by lazy { ViewfinderOrientationHandler(this) }

    val gestureDetector: GestureDetector
        get() = gestureHandler.gestureDetector

    open fun takePicture() {
        viewfinder.onAction(CaptureAction.ShutterClicked)
    }

    @set:VisibleForTesting
    lateinit var tunePlayer: TunePlayer

    lateinit var settingsDialog: SettingsDialog

    val threeButtons: View
        get() = binding.threeButtons

    val previewView: PreviewView
        get() = binding.preview

    val bottomOverlay: View
        get() = binding.bottomOverlay

    val rootView: View
        get() = binding.root

    val qrScanToggles: View
        get() = binding.qrScanToggles

    val qrToggle: QRToggle
        get() = binding.qrScanToggle

    val dmToggle: QRToggle
        get() = binding.dataMatrixToggle

    val cBToggle: QRToggle
        get() = binding.pdf417Toggle

    val azToggle: QRToggle
        get() = binding.aztecToggle

    val flipCameraCircle: View
        get() = binding.flipCameraCircle

    val cancelButtonView: ImageView
        get() = binding.cancelButton

    val tabLayout: BottomTabLayout
        get() = binding.cameraModeTabs

    val captureButton: CaptureButton
        get() = binding.captureButton

    val timerView: TextView
        get() = binding.timer

    val thirdOption: View
        get() = binding.thirdOption

    val imagePreview: ShapeableImageView
        get() = binding.imagePreview

    val previewLoader: ProgressBar
        get() = binding.previewLoading

    val zoomBar: ZoomBar
        get() = binding.zoomBar

    val zoomBarPanel: LinearLayout
        get() = binding.zoomBarPanel

    val exposureBar: ExposureBar
        get() = binding.exposureBar

    val exposureBarPanel: LinearLayout
        get() = binding.exposureBarPanel

    val qrOverlay: QROverlay
        get() = binding.qrOverlay

    val settingsIcon: ImageView
        get() = binding.settingsOption

    val mainOverlay: ImageView
        get() = binding.mainOverlay

    val previewGrid: CustomGrid
        get() = binding.previewGrid

    val cdTimer: CountDownTimerUI
        get() = binding.cTimer

    val cbText: TextView
        get() = binding.captureButtonText

    val cbCross: ImageView
        get() = binding.captureButtonCross

    val gCircleFrame: FrameLayout
        get() = binding.gCircleFrame

    val muteToggle: ShapeableImageView
        get() = binding.muteToggle

    val micOffIcon: ImageView
        get() = binding.micOff

    private val cameraPermission = arrayOf(Manifest.permission.CAMERA)

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private var cameraPermissionDialog: AlertDialog? = null

    private var audioPermissionDialog: AlertDialog? = null

    internal val previewFrames: PreviewFrameHolder by lazy {
        PreviewFrameHolderImpl(
            previewView = previewView,
            onLateFrame = ::showLateTransitionFrame,
        )
    }

    // Whether the transition still is standing in for the preview.
    private var transitionShown = false

    val selfTimerSeconds: Int
        get() {
            return viewfinder.uiState.value.settingsSheet.selfTimerSeconds
        }

    private var bottomNavigationBarPadding: Int = 0

    private var shouldRestartRecording = false

    val thumbnailLoaderExecutor = Executors.newSingleThreadExecutor()

    private lateinit var snackBar: Snackbar

    private val focusRingHandler: Handler = Handler(Looper.getMainLooper())

    private val focusRingCallback: Runnable = Runnable {
        binding.focusRing.visibility = View.INVISIBLE
    }

    private val restartRecordingWithAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            shouldRestartRecording = true
            viewfinder.onAction(LifecycleAction.RecordAudioPermissionGranted)
            return@registerForActivityResult
        }
        showAudioPermissionDeniedDialog {
            requestRecording()
        }
    }

    // Used to request permission from the user
    private val requestPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        if (permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                Log.i(TAG, "Permission granted for recording audio.")
            } else {
                Log.i(TAG, "Permission denied for recording audio.")
                showAudioPermissionDeniedDialog()
            }
        }
        if (permissions.containsKey(Manifest.permission.CAMERA)) {
            if (hasCameraPermission()) {
                Log.i(TAG, "Permission granted for camera.")
            } else {
                Log.i(TAG, "Permission denied for camera.")
            }
        }
    }

    fun onDeviceAngleChange(xDegrees: Float, zDegrees: Float) {
        orientationHandler.onDeviceAngleChange(xDegrees, zDegrees)
    }

    private fun showAudioPermissionDeniedDialog(onDisableAudio: () -> Unit = {}) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.audio_permission_dialog_title)
            .setMessage(R.string.audio_permission_dialog_message)

        // Open the settings menu for the current app
        builder.setPositiveButton(R.string.settings) { _: DialogInterface?, _: Int ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts(
                "package",
                packageName,
                null
            )
            intent.data = uri
            startActivity(intent)
        }
        builder.setNegativeButton(R.string.cancel, null)

        builder.setNeutralButton(R.string.disable_audio) { _: DialogInterface?, _: Int ->
            viewfinder.onAction(SettingsAction.AudioToggled(enabled = false))
            onDisableAudio()
        }

        audioPermissionDialog = builder.showIgnoringShortEdgeMode()
    }

    // The blurred still that stands in for the preview whenever the camera is not streaming.
    private fun showPreviewTransition() {
        transitionShown = true
        previewGrid.visibility = View.INVISIBLE

        val lastFrame = previewFrames.lastFrame
        if (lastFrame == null || this is CaptureActivity) return

        // The still is of the camera being left behind, and the box under it takes the new camera's
        // aspect ratio partway through the switch. Cropping keeps it covering that box; the layout's
        // fitStart, which is there for the captured photo CaptureActivity shows in this same view,
        // would leave a bar down the side of the preview until the new camera streams.
        mainOverlay.scaleType = ImageView.ScaleType.CENTER_CROP
        setBlurBitmapCompat(mainOverlay, lastFrame)
        settingsIcon.visibility = View.INVISIBLE
        settingsIcon.isEnabled = false
        mainOverlay.visibility = View.VISIBLE
    }

    private fun showLateTransitionFrame() {
        if (transitionShown) {
            showPreviewTransition()
        }
    }

    private fun hidePreviewTransition() {
        transitionShown = false
        mainOverlay.visibility = View.INVISIBLE

        if (viewfinder.uiState.value.isQrMode) {
            return
        }

        previewGrid.visibility = View.VISIBLE
        if (!settingsDialog.isShowing) {
            settingsIcon.visibility = View.VISIBLE
        }

        settingsIcon.isEnabled = true
    }

    // Not from the strip's own touch listener: a tab view takes the DOWN, so the strip is only
    // handed a gesture once the scroll view has taken it back off the tab -- by which point a
    // switch started by a plain tap has already happened. The freshness window keeps a touch that
    // switches nothing from costing more than one copy every couple of seconds.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            previewFrames.prefetch()
        }
        return super.dispatchTouchEvent(ev)
    }

    fun animateFocusRing(x: Float, y: Float) {
        // Move the focus ring so that its center is at the tap location (x, y)
        val width = binding.focusRing.width.toFloat()
        binding.focusRing.updateLayoutParams<ConstraintLayout.LayoutParams> {
            updateMargins(
                left = (x - width / 2).roundToInt(),
                top = (y - width / 2).roundToInt()
            )
        }

        // Show focus ring
        binding.focusRing.visibility = View.VISIBLE
        binding.focusRing.alpha = 1f

        if (areSystemAnimationsEnabled()) {
            // Animate the focus ring to disappear
            binding.focusRing.animate()
                .setStartDelay(500)
                .setDuration(300)
                .alpha(0f)
                .setListener(object : Animator.AnimatorListener {

                    var isCancelled = false

                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animator: Animator) {
                        if (!isCancelled) {
                            binding.focusRing.visibility = View.INVISIBLE
                        }

                        isCancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        isCancelled = true
                    }

                    override fun onAnimationRepeat(animation: Animator) {}
                }).start()
        } else {
            focusRingHandler.removeCallbacks(focusRingCallback)
            focusRingHandler.postDelayed(focusRingCallback, 800)
        }
    }

    private fun areSystemAnimationsEnabled(): Boolean {
        val duration: Float = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        val transition: Float = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )

        return duration != 0f && transition != 0f
    }

    private fun openGallery() {
        check(this !is CaptureActivity)

        Intent(this, InAppGallery::class.java).let {
            if (this is SecureMainActivity) {
                it.putExtra(InAppGallery.INTENT_KEY_SECURE_MODE, true)

                val list = capturedItems
                if (list.isEmpty()) {
                    showMessage(R.string.no_image)
                    return
                }
                it.putParcelableArrayListExtra(
                    InAppGallery.INTENT_KEY_LIST_OF_SECURE_MODE_CAPTURED_ITEMS,
                    list
                )
            } else {
                it.putExtra(InAppGallery.INTENT_KEY_VIDEO_ONLY_MODE, requiresVideoModeOnly)
            }

            if (isThumbnailLoaded) { // indicates that last captured item is accessible
                it.putExtra(
                    InAppGallery.INTENT_KEY_LAST_CAPTURED_ITEM,
                    capturedItemSession.lastCapturedItem,
                )
            }

            startActivity(it)
        }
    }

    internal fun requestRecording() {
        viewfinder.onAction(
            RecordingAction.RecordingRequested(
                hasAudioPermission = hasPermission(Manifest.permission.RECORD_AUDIO),
            ),
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return hasPermission(Manifest.permission.CAMERA)
    }

    private fun checkPermissions() {
        Log.i(TAG, "Checking camera status...")

        // Check if the app has access to the user's camera
        when {
            hasCameraPermission() -> {
                // If the user has manually granted the permission, dismiss the dialog.
                if (cameraPermissionDialog != null &&
                    cameraPermissionDialog!!.isShowing
                ) {
                    cameraPermissionDialog!!.cancel()
                }
                Log.i(TAG, "Permission granted.")

                // Setup the camera since the permission is available
                viewfinder.onAction(LifecycleAction.CameraPermissionGranted)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Log.i(TAG, "The user has default denied camera permission.")

                // Don't build and show a new dialog if it's already visible
                if (cameraPermissionDialog != null && cameraPermissionDialog!!.isShowing) return
                val builder = MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.camera_permission_dialog_title)
                    .setMessage(R.string.camera_permission_dialog_message)
                val positiveClicked = AtomicBoolean(false)

                // Open the settings menu for the current app
                builder.setPositiveButton(R.string.settings) { _: DialogInterface?, _: Int ->
                    positiveClicked.set(true)
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts(
                        "package",
                        packageName,
                        null
                    )
                    intent.data = uri
                    startActivity(intent)
                }
                builder.setNegativeButton(R.string.cancel, null)
                builder.setOnDismissListener {
                    // The dialog could have either been dismissed by clicking on the
                    // background or by clicking the cancel button. So in those cases,
                    // the app should exit as the app depends on the camera permission.
                    if (!positiveClicked.get()) {
                        finish()
                    }
                }
                cameraPermissionDialog = builder.showIgnoringShortEdgeMode()
            }

            // Request for the permission (Android will actually popup the permission
            // dialog in this case)
            else -> {
                Log.i(TAG, "Requesting permission from user...")

                requestPermissionLauncher.launch(cameraPermission)
            }
        }

        audioPermissionDialog?.let { dialog ->
            if (hasPermission(Manifest.permission.RECORD_AUDIO) && dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // there are no camera controls in qr mode
        if (viewfinder.uiState.value.isQrMode) {
            return super.onKeyUp(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_CAMERA,
            -> {
                captureButton.performClick()
            }
            KeyEvent.KEYCODE_FOCUS -> {
                // cancel any manual focus
                // CameraX will start the continuous autofocus (if supported) automatically
                viewfinder.onAction(CameraAction.FocusKeyPressed)
            }
            KeyEvent.KEYCODE_ZOOM_IN -> {
                viewfinder.onAction(CameraAction.ZoomInKeyPressed)
            }
            KeyEvent.KEYCODE_ZOOM_OUT -> {
                viewfinder.onAction(CameraAction.ZoomOutKeyPressed)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Pretend as if the event was handled by the app (avoid volume bar from appearing)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        orientationHandler.resumeOrientationSensor()
        // Check camera permission again if the user switches back to the app (maybe
        // after enabling/disabling the camera permission in Settings)
        // Will also be called by Android Lifecycle when the app starts up
        checkPermissions()

        updateThumbnail()

        if (viewfinder.uiState.value.settingsSheet.geoTagging) {
            requestLocation()
        }

        // If the preview of video capture activity isn't showing
        if (!(this is VideoCaptureActivity && viewfinder.uiState.value.capturedPreviewVisible)) {
            if (!viewfinder.uiState.value.qrResultVisible) {
                if (hasCameraPermission()) {
                    viewfinder.onAction(LifecycleAction.ScreenResumed)
                } else {
                    Log.i(TAG, "Leaving the camera uninitialized until the permission is granted.")
                }
            }
        }
    }

    val requiresVideoModeOnly: Boolean
        get() {
            return cameraEntryPoint.requiresVideoModeOnly
        }

    private fun selectBarcodeFormatToggles() {
        val toggles = mapOf(
            BarcodeFormat.QR_CODE to qrToggle,
            BarcodeFormat.AZTEC to azToggle,
            BarcodeFormat.PDF_417 to cBToggle,
            BarcodeFormat.DATA_MATRIX to dmToggle,
        )

        barcodeFormats.enabled.forEach { format ->
            toggles[format]?.isSelected = true
        }
    }

    override fun onPause() {
        super.onPause()

        // Leaving a mode switch waiting on an animation that will never finish would strand the
        // strip on a mode the camera never entered.
        tabLayout.settleNow()
        orientationHandler.pauseOrientationSensor()

        // The countdown would otherwise keep ticking while the app is in the background and fire a
        // capture into a camera that has already been unbound.
        cdTimer.cancelTimer()
        if (!viewfinder.uiState.value.isQrMode) {
            viewfinder.onAction(CaptureAction.PictureCaptureCancelled)
        }
        if (viewfinder.uiState.value.settingsSheet.geoTagging) {
            locationRepository.pauseUpdates()
        }
        previewFrames.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        snackBar = Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG)

        val renderer = ViewfinderViewRenderer(
            activity = this,
        )
        val effectHandler: ViewfinderEffectHandler = ViewfinderEffectHandlerImpl(
            activity = this,
            clipboardManager = clipboardManager,
            notificationManager = notificationManager,
            onAction = viewfinder::onAction,
        )
        viewfinder.onAction(
            LifecycleAction.ScreenCreated(
                host = ViewfinderHost(
                    previewTarget = PreviewTarget(
                        lifecycleOwner = this,
                        surfaceProvider = previewView.surfaceProvider,
                        meteringPointFactory = previewView.meteringPointFactory,
                    ),
                    chrome = ViewfinderChromeImpl(activity = this),
                    previewFrames = previewFrames,
                ),
            ),
        )
        tunePlayer = TunePlayer(
            context = this,
            soundsEnabled = { viewfinder.uiState.value.capture.cameraSounds },
        )

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.uiState.collect { state ->
                    renderer.render(state)
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.effects.collect { effect ->
                    effectHandler.handle(effect)
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                locationRepository.providersDisabled.collect {
                    indicateLocationProvidedIsDisabled()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            capturedItemSession.prepare()

            updateThumbnail()
        }

        previewView.scaleType = PreviewView.ScaleType.FIT_START

        tabLayout.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                val tab = tabLayout.getTabAtX(tabLayout.scrollX)
                finalizeMode(tab)
                return@setOnTouchListener true
            }

            return@setOnTouchListener false
        }

        previewView.previewStreamState.observe(this) { state: StreamState ->
            if (state == StreamState.STREAMING) {
                hidePreviewTransition()
                viewfinder.onAction(LifecycleAction.PreviewStreamingStarted)

                restartRecordingIfPermissionsWasUnavailable()
            } else {
                showPreviewTransition()
            }
        }

        var tapDownTimestamp: Long = 0
        flipCameraCircle.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (tapDownTimestamp == 0L) {
                        tapDownTimestamp = System.currentTimeMillis()
                        flipCameraCircle.animate().scaleXBy(0.05f).setDuration(300).start()
                        flipCameraCircle.animate().scaleYBy(0.05f).setDuration(300).start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dif = System.currentTimeMillis() - tapDownTimestamp
                    if (dif < 300) {
                        flipCameraCircle.performClick()
                    }

                    tapDownTimestamp = 0
                    flipCameraCircle.animate().cancel()
                    flipCameraCircle.animate().scaleX(1f).setDuration(300).start()
                    flipCameraCircle.animate().scaleY(1f).setDuration(300).start()
                }
                else -> {
                }
            }
            true
        }
        flipCameraCircle.setOnClickListener {
            resetAutoSleep()
            if (viewfinder.uiState.value.isQrMode) {
                viewfinder.onAction(SettingsAction.ScanAllCodesToggleClicked)
                return@setOnClickListener
            }

            if (viewfinder.uiState.value.isRecordingActive) {
                viewfinder.onAction(
                    RecordingAction.RecordingPauseToggled(
                        paused = !viewfinder.uiState.value.isRecordingPaused,
                    ),
                )
                return@setOnClickListener
            }

            val flipCameraIcon: ImageView = binding.flipCameraIcon
            val rotation: Float = if (flipCameraIcon.rotation < 180) {
                180f
            } else {
                360f
            }

            val rotate = RotateAnimation(
                0F,
                rotation,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            rotate.duration = 400
            rotate.interpolator = LinearInterpolator()

            it.startAnimation(rotate)
            viewfinder.onAction(CameraAction.LensSwitchClicked)
        }

        binding.thirdCircle.setOnClickListener {
            resetAutoSleep()
            if (viewfinder.uiState.value.isRecordingActive) {
                takePicture()
            } else {
                openGallery()
                Log.i(TAG, "Attempting to open gallery...")
            }
        }

        binding.thirdCircle.setOnLongClickListener {
            if (viewfinder.uiState.value.isRecordingActive) {
                takePicture()
            } else {
                shareLatestMedia()
            }

            return@setOnLongClickListener true
        }

        captureButton.setOnClickListener {
            resetAutoSleep()

            // A mode the strip is still settling into has not reached the camera yet, and this
            // would otherwise capture in the mode being left behind.
            tabLayout.settleNow()

            if (viewfinder.uiState.value.isVideoMode) {
                if (viewfinder.uiState.value.isRecordingActive) {
                    viewfinder.onAction(RecordingAction.RecordingStopRequested)
                } else {
                    requestRecording()
                }
            } else if (viewfinder.uiState.value.isQrMode) {
                viewfinder.onAction(CameraAction.TorchToggleClicked)
            } else {
                if (selfTimerSeconds == 0) {
                    takePicture()
                } else {
                    if (cdTimer.isRunning) {
                        cdTimer.cancelTimer()
                    } else {
                        cdTimer.startTimer()
                    }
                }
            }
        }

        zoomBar.setMainActivity(this)
        exposureBar.setMainActivity(this)

        settingsIcon.setOnClickListener {
            if (!viewfinder.uiState.value.isQrMode) {
                settingsDialog.show()
            }
        }

        settingsIcon.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val displayCutout = window.decorView.rootWindowInsets.displayCutout
                val layoutParams = (settingsIcon.layoutParams as RelativeLayout.LayoutParams)

                val rect = if (displayCutout?.boundingRects?.isNotEmpty() == true) {
                    displayCutout.boundingRects.first()
                } else {
                    null
                }

                val windowsSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    windowManager.currentWindowMetrics.bounds
                } else {
                    val size = Point()
                    // defaultDisplay isn't deprecated below API 30 as highlighted by the IDE
                    // and this code would only execute if it is (Hint: enclosing if-block)
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealSize(size)
                    Rect(0, 0, size.x, size.y)
                }

                if (rect == null || rect.left <= 0 || rect.right == windowsSize.right) {
                    layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                }
            }
        })

        var isInsetSet = false

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
            )

            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.setMargins(
                    insets.left,
                    0,
                    insets.right,
                    0,
                )

                it
            }

            zoomBarPanel.setPadding(0, 0, 0, insets.bottom)
            exposureBarPanel.setPadding(0, 0, 0, insets.bottom)
            bottomNavigationBarPadding = insets.bottom

            if (insets.top != 0 && !isInsetSet) {
                binding.mainFrame.layoutParams =
                    (binding.mainFrame.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            (8 * resources.displayMetrics.density.toInt()) + insets.top,
                            it.rightMargin,
                            it.bottomMargin,
                        )

                        it
                    }

                qrScanToggles.layoutParams =
                    (qrScanToggles.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            (16 * resources.displayMetrics.density.toInt()) +
                                insets.top,
                            it.rightMargin,
                            it.bottomMargin,
                        )

                        it
                    }

                isInsetSet = true
            }

            WindowInsetsCompat.CONSUMED
        }

        setContentView(binding.getRoot())

        WindowInsetsControllerCompat(window, rootView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()

        cdTimer.setMainActivity(this)

        val themedContext = DynamicColors.wrapContextIfAvailable(this, R.style.Theme_SettingsDialog)
        settingsDialog = SettingsDialog(this, themedContext)

        SystemSettingsObserver(lifecycle, Settings.System.ACCELEROMETER_ROTATION, this) {
            forceUpdateOrientationSensor()
        }

        previewView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    previewView.viewTreeObserver.removeOnPreDrawListener(this)
                    repositionTabLayout()
                    return true
                }
            }
        )

        binding.moreOptions.setOnClickListener {
            showMoreQrFormatOptions(
                activity = this,
                barcodeFormats = barcodeFormats,
            )
        }

        qrToggle.mActivity = this
        qrToggle.key = BarcodeFormat.QR_CODE.name

        dmToggle.mActivity = this
        dmToggle.key = BarcodeFormat.DATA_MATRIX.name

        cBToggle.mActivity = this
        cBToggle.key = BarcodeFormat.PDF_417.name

        azToggle.mActivity = this
        azToggle.key = BarcodeFormat.AZTEC.name

        selectBarcodeFormatToggles()

        settingsDialog.loadInitialState()

        muteToggle.setOnClickListener {
            if (viewfinder.uiState.value.isRecordingMuted) {
                viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = false))
                showMessage(R.string.video_audio_recording_unmuted)
            } else {
                viewfinder.onAction(RecordingAction.RecordingMuteToggled(muted = true))
                showMessage(R.string.video_audio_recording_muted)
            }
        }
    }

    private fun repositionTabLayout() {
        threeButtons.visibility = View.VISIBLE

        tabLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                tabLayout.viewTreeObserver
                    .removeOnPreDrawListener(
                        this
                    )

                val previewHeight169 = binding.previewContainer.width * 16 / 9

                val extraHeight169 = binding.previewContainer.height -
                    previewHeight169 -
                    tabLayout.height -
                    10 * resources.displayMetrics.density.toInt()

                // When there's no extra space in 16:9 for even the bottom nav bar to be present without
                // obscuring the preview or if there's sufficient space for the entire bottom UI to exist
                val shouldSnapAboveBottomNav = extraHeight169 < bottomNavigationBarPadding ||
                    extraHeight169 >=
                    (threeButtons.height + tabLayout.height + tabLayout.marginTop)

                tabLayout.layoutParams =
                    (tabLayout.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            it.topMargin,
                            it.rightMargin,
                            if (shouldSnapAboveBottomNav) {
                                bottomNavigationBarPadding
                            } else {
                                extraHeight169
                            }
                        )

                        it
                    }

                return true
            }
        })
    }

    fun finalizeMode(tab: TabLayout.Tab? = null) {
        // The strip is untouchable during a recording but not while its start sound still plays, and
        // rebinding the camera there starts the queued recording on a dead recorder. The touch may
        // already have dragged the strip, so put it back on the mode the camera is really in.
        if (viewfinder.uiState.value.isRecordingActive) {
            tabLayout.getTabForMode(viewfinder.uiState.value.mode)?.let {
                tabLayout.goToTab(it)
            }
            return
        }

        val selectedTab = tab ?: tabLayout.selectedTab
        if (selectedTab != null) {
            val mode = selectedTab.tag as CameraMode

            // Blurred while the strip is still gliding, not once the camera has gone: the rebind
            // holds the main thread for half a second, so a transition left to the stream state
            // would only reach the screen after the wait it is there to explain. Guarded on the
            // mode really changing, since nothing would rebind to take it back down again.
            if (mode != viewfinder.uiState.value.mode) {
                showPreviewTransition()
            }

            // switchMode() puts the strip on the mode the camera actually ended up in, which is a
            // different one when an extension fails to bind.
            tabLayout.goToTab(selectedTab) {
                if (mode != viewfinder.uiState.value.mode) {
                    viewfinder.onAction(CameraAction.ModeSelected(mode))
                } else if (
                    transitionShown &&
                    previewView.previewStreamState.value == StreamState.STREAMING
                ) {
                    // A transition raised for a switch this tap has just cancelled, over a camera
                    // that never stopped streaming: nothing is left to rebind and take it down.
                    hidePreviewTransition()
                }
            }

            resetAutoSleep()
        }
    }

    /** Shows the pending self-timer duration on the capture button, where it applies at all. */
    fun updateSelfTimerBadge() {
        val state = viewfinder.uiState.value
        cbText.text = state.selfTimerBadge
        cbText.visibility = if (state.selfTimerBadgeVisible) View.VISIBLE else View.INVISIBLE
    }

    fun restartRecordingWithMicPermission() {
        restartRecordingWithAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun shareLatestMedia() {
        if (this is SecureActivity) {
            showMessage(R.string.sharing_not_allowed)
            return
        }

        val item = capturedItemSession.lastCapturedItem
        if (item == null) {
            showMessage(R.string.please_wait_for_image_to_get_captured_before_sharing)
            return
        }

        shareCapturedItem(this, item)?.let { showMessage(it) }
    }

    open fun bytesToHex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "" // outLen will be wrong for empty inputs

        // Represent bytes as a grid of hex digits:
        // Add a space between every byte
        // Double space every 4 bytes (unless end or newline)
        // Add a newline every 8 bytes (unless end)

        var outLen = bytes.size * 3 - 1 // 2 hex digits + 1 space/newline per byte (except last)
        outLen += bytes.size / 8 // One double space per row except the last incomplete row
        if (bytes.size % 8 > 4) {
            outLen += 1 // One double space for the last incomplete row, if it has >4 columns
        }

        val hexChars = CharArray(outLen)
        var j = 0 // Output index

        for (i in bytes.indices) {
            val byte = bytes[i].toInt() and 0xFF
            hexChars[j++] = hexArray[byte ushr 4]
            hexChars[j++] = hexArray[byte and 0x0F]

            if (i == bytes.lastIndex) break // No trailing whitespace
            if (i % 8 == 7) {
                hexChars[j++] = '\n'
            } else {
                hexChars[j++] = ' '
                if (i % 4 == 3) hexChars[j++] = ' '
            }
        }

        return String(hexChars)
    }

    fun showQrResult(rawText: String) {
        val hString = bytesToHex(
            rawText.toByteArray(StandardCharsets.UTF_8)
        )

        val builder = MaterialAlertDialogBuilder(this)
        val dialogBinding = ScanResultDialogBinding.inflate(layoutInflater)
        builder.setView(dialogBinding.root)

        val tabLayout: TabLayout = dialogBinding.encodingTabs
        val textView = dialogBinding.scanResultText

        val intentView = Intent(Intent.ACTION_VIEW, rawText.toUri())

        if (packageManager.resolveActivity(intentView, 0L) != null) {
            dialogBinding.openWith.setOnClickListener {
                val chooser = Intent.createChooser(intentView, getString(R.string.open_with))
                startActivity(chooser)
            }
        } else {
            dialogBinding.openWith.visibility = View.GONE
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.text.toString()) {
                    "Binary" -> {
                        textView.autoLinkMask = 0
                        textView.text = hString
                    }

                    "UTF-8" -> {
                        textView.autoLinkMask =
                            Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES
                        textView.text = rawText
                    }
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        })

        tabLayout.addTab(
            tabLayout.newTab().apply {
                text = "UTF-8"
            }
        )

        tabLayout.addTab(
            tabLayout.newTab().apply {
                text = "Binary"
            }
        )

        val ctc: ImageButton = dialogBinding.copyQrText
        ctc.setOnClickListener {
            val clipboardManager = getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val clipData = ClipData.newPlainText(
                "text",
                textView.text
            )
            clipboardManager.setPrimaryClip(clipData)

            showMessage(getString(R.string.copied_text_to_clipboard))
        }

        val sButton: ImageButton = dialogBinding.shareQrText
        sButton.setOnClickListener {
            val sIntent = Intent(Intent.ACTION_SEND)
            sIntent.type = "text/plain"
            sIntent.putExtra(Intent.EXTRA_TEXT, textView.text.toString())
            startActivity(
                Intent.createChooser(
                    sIntent,
                    getString(R.string.share_text_via)
                )
            )
        }

        builder.setOnDismissListener {
            viewfinder.onAction(LifecycleAction.QrResultDismissed)
        }

        builder.showIgnoringShortEdgeMode()
    }

    private fun viewfinderCreationExtras(): CreationExtras {
        val defaults = defaultViewModelCreationExtras
        val arguments = Bundle().apply {
            defaults[DEFAULT_ARGS_KEY]?.let(::putAll)
            putAll(ViewfinderViewModel.arguments(cameraEntryPoint))
        }

        return MutableCreationExtras(defaults).apply {
            set(DEFAULT_ARGS_KEY, arguments)
        }
    }

    fun showMessage(@StringRes message: Int) {
        showMessage(getString(message))
    }

    fun showMessage(
        @StringRes msg: Int,
        action: String?,
        callback: View.OnClickListener?,
    ) {
        showMessage(getString(msg), action, callback)
    }

    /**
     * The icon in the left circle stands for whatever [flipCameraCircle] does right now — flip
     * the camera, pause/resume the recording, or toggle scanning of every barcode format. Swap
     * its description together with its drawable so that it is never announced as the wrong
     * button.
     */
    fun setFlipCameraIcon(@DrawableRes icon: Int, @StringRes description: Int) {
        binding.flipCameraIconContent.setImageResource(icon)
        binding.flipCameraIconContent.contentDescription = getString(description)
    }

    /**
     * `thirdCircle` is the view that carries the click listener, so it is the one that has to be
     * described: it opens the gallery, except while a video is being recorded, when it takes a
     * still instead.
     */
    fun setThirdCircleIcon(@DrawableRes icon: Int, @StringRes description: Int) {
        binding.thirdCircle.setImageResource(icon)
        binding.thirdCircle.contentDescription = getString(description)
    }

    /**
     * The mute toggle signals its state through an icon, a background colour and a tooltip, and a
     * tooltip is supplementary text rather than a view's label, so the state was never described
     * to accessibility services at all. Set all four together here so they cannot drift apart.
     */
    fun setMuteToggleState(muted: Boolean) {
        muteToggle.setImageResource(if (muted) R.drawable.mic_off else R.drawable.mic_on)
        muteToggle.setBackgroundColor(
            if (muted) getColor(android.R.color.darker_gray) else getColor(R.color.red)
        )
        muteToggle.tooltipText = getString(
            if (muted) R.string.tap_to_unmute_audio else R.string.tap_to_mute_audio
        )
        muteToggle.contentDescription = getString(
            if (muted) R.string.unmute_audio else R.string.mute_audio
        )
    }

    fun showMessage(message: String) {
        showMessage(message, action = null, callback = null)
    }

    fun showMessage(msg: String, action: String?, callback: View.OnClickListener?) {
        snackBar.apply {
            setText(msg)
            setAction(action, callback)
            show()
        }
    }

    fun indicateLocationProvidedIsDisabled() {
        showMessage(
            getString(R.string.location_is_disabled),
            if (this !is SecureMainActivity) getString(R.string.enable) else null
        ) {
            enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // The activity declares configChanges for orientation, so nothing else refreshes
        // rotation-dependent state.
        // The preview follows the window; the capture use cases follow the sensor and are updated
        // by onOrientationChange.
        session.preview?.targetRotation =
            previewView.display?.rotation ?: Surface.ROTATION_0
        val state = viewfinder.uiState.value
        state.sensorOrientationDegrees?.let {
            previewView.applyPreviewRatio(
                aspectRatio = state.aspectRatio,
                sensorOrientationDegrees = it,
            )
        }

        rootView.post { sensorNotifier?.notifyListeners() }
    }

    fun forceUpdateOrientationSensor() {
        sensorNotifier?.notifyListeners(true)
    }

    val sensorNotifier: SensorOrientationChangeNotifier?
        get() {
            return SensorOrientationChangeNotifier.getInstance(this)
        }

    fun getRotation(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
                ?:
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        return when (rotation) {
            Surface.ROTATION_90 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 90
            else -> 0
        }
    }

    internal val dp32 by lazy {
        32 * resources.displayMetrics.density
    }

    internal fun vibrateDevice() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    override fun onDestroy() {
        super.onDestroy()
        SensorOrientationChangeNotifier.clearInstance()
        thumbnailLoaderExecutor.shutdownNow()
        previewFrames.release()
        viewfinder.onAction(LifecycleAction.ScreenDestroyed)
        capturedItemSession.close()
    }

    fun onRequireLocationChanged(required: Boolean) {
        if (required) {
            requestLocation()
        } else {
            locationRepository.stopUpdates()
        }
    }

    private val enableLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The snackbar that leads here outlives a mode switch, so geo-tagging can be off for the
        // mode this returns to
        if (viewfinder.uiState.value.settingsSheet.geoTagging) {
            requestLocation(locationRepository.isAnyProviderEnabled())
        }
    }

    // Used to request permission from the user
    private val locationPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) {
        if (!locationRepository.shouldAskForPermission()) {
            requestLocation()
        } else {
            viewfinder.onAction(SettingsAction.GeoTaggingToggled(enabled = false))
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation(reAttach: Boolean = false) {
        when {
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) && ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                MaterialAlertDialogBuilder(this).let {
                    it.setTitle(R.string.location_permission_dialog_title)
                    it.setMessage(R.string.location_permission_dialog_message)

                    if (this !is SecureActivity) {
                        it.setPositiveButton(R.string.settings) { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            this.startActivity(intent)
                        }
                    }

                    it.setOnDismissListener {
                        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            viewfinder.onAction(SettingsAction.GeoTaggingToggled(enabled = false))
                        }
                    }
                }.showIgnoringShortEdgeMode()
            }

            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                if (!locationRepository.isLocationEnabled()) {
                    indicateLocationProvidedIsDisabled()
                }
                locationRepository.startUpdates(reattach = reAttach)
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }
    }

    private fun resetAutoSleep() {
        application.resetPreventScreenFromSleeping()
    }

    @Volatile var isStarted = false

    override fun onStart() {
        super.onStart()
        isStarted = true
    }

    override fun onStop() {
        isStarted = false
        // Stop explicitly rather than letting the unbind tear the recording down for us.
        if (viewfinder.uiState.value.isRecordingActive) {
            previewFrames.holdCurrentFrame()
            viewfinder.onAction(RecordingAction.RecordingStopRequested)
        }
        super.onStop()
    }

    var isThumbnailLoaded = false

    fun updateThumbnail() {
        val item = capturedItemSession.lastCapturedItem
        val preview = imagePreview
        preview.setImageBitmap(null)
        isThumbnailLoaded = false

        if (item == null) {
            return
        }

        val ctx = applicationContext

        thumbnailLoaderExecutor.executeIfAlive {
            var bitmap: Bitmap? = null
            try {
                val side = preview.layoutParams.width

                if (item.type == ITEM_TYPE_VIDEO) {
                    val origBitmap = getVideoThumbnail(ctx, item.uri)
                    origBitmap?.let {
                        val w = it.width.toDouble()
                        val h = it.height.toDouble()
                        val ratio = max(w / side, h / side)

                        bitmap = it.scale((w / ratio).toInt(), (h / ratio).toInt())
                        origBitmap.recycle()
                    }
                } else if (item.type == ITEM_TYPE_IMAGE) {
                    val source = ImageDecoder.createSource(ctx.contentResolver, item.uri)
                    bitmap = ImageDecoder.decodeBitmap(source, ImageResizer(side, side))
                }
            } catch (e: Exception) {
                Log.d(TAG, "unable to update preview", e)
            }

            if (bitmap != null) {
                mainExecutor.execute {
                    if (isStarted && capturedItemSession.lastCapturedItem == item) {
                        preview.setImageBitmap(bitmap)
                        isThumbnailLoaded = true
                    }
                }
            }
        }
    }

    private fun restartRecordingIfPermissionsWasUnavailable() {
        if (shouldRestartRecording) {
            shouldRestartRecording = false
            requestRecording()
        }
    }

    companion object {
        private const val TAG = "GOCam"

        private val hexArray = "0123456789ABCDEF".toCharArray()
    }
}
