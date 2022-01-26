package app.grapheneos.camera.ui.activities

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.util.Linkify
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.Surface
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.Window
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.grapheneos.camera.BlurBitmap
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.CustomLocationListener
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.ImageCapturer
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.notifier.SensorOrientationChangeNotifier
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.CountDownTimerUI
import app.grapheneos.camera.ui.CustomGrid
import app.grapheneos.camera.ui.QROverlay
import app.grapheneos.camera.ui.SettingsDialog
import app.grapheneos.camera.ui.seekbar.ExposureBar
import app.grapheneos.camera.ui.seekbar.ZoomBar
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import android.graphics.Rect
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import app.grapheneos.camera.ui.QRToggle
import com.google.zxing.BarcodeFormat
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout


open class MainActivity : AppCompatActivity(),
    OnTouchListener,
    OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    SensorOrientationChangeNotifier.Listener {

    private val audioPermission = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val cameraPermission = arrayOf(Manifest.permission.CAMERA)

    lateinit var previewView: PreviewView
    lateinit var previewContainer: ConstraintLayout

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private var cameraPermissionDialog: AlertDialog? = null
    private var audioPermissionDialog: AlertDialog? = null
    private var lastFrame: Bitmap? = null

    private lateinit var mainFrame: View
    lateinit var rootView: View

    lateinit var qrScanToggles: View
    private lateinit var moreOptionsToggle: View

    lateinit var qrToggle: QRToggle
    lateinit var dmToggle: QRToggle
    lateinit var cBToggle: QRToggle
    lateinit var azToggle: QRToggle

    lateinit var imageCapturer: ImageCapturer
    lateinit var videoCapturer: VideoCapturer

    lateinit var flipCameraCircle: View
    lateinit var cancelButtonView: ImageView
    lateinit var tabLayout: BottomTabLayout
    lateinit var thirdCircle: ImageView
    lateinit var captureButton: ImageButton

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var dbTapGestureDetector: GestureDetector
    lateinit var timerView: TextView
    lateinit var thirdOption: View
    lateinit var imagePreview: ShapeableImageView
    lateinit var previewLoader: ProgressBar
    private var isZooming = false

    lateinit var zoomBar: ZoomBar
    lateinit var zoomBarPanel: LinearLayout

    lateinit var exposureBar: ExposureBar
    lateinit var exposureBarPanel: LinearLayout

    lateinit var qrOverlay: QROverlay

    lateinit var threeButtons: LinearLayout

    lateinit var settingsIcon: ImageView

    private lateinit var exposurePlusIcon: ImageView
    private lateinit var exposureNegIcon: ImageView

    private lateinit var zoomInIcon: ImageView
    private lateinit var zoomOutIcon: ImageView

    lateinit var flipCamIcon: ImageView

    lateinit var mainOverlay: ImageView

    lateinit var settingsDialog: SettingsDialog
    lateinit var previewGrid: CustomGrid

    private var wasSwiping = false

    lateinit var cdTimer: CountDownTimerUI
    var timerDuration = 0

    lateinit var cbText: TextView
    lateinit var cbCross: ImageView

    lateinit var gCircleFrame: FrameLayout

    private lateinit var gAngleTextView: TextView
    private lateinit var gCircle: LinearLayout

    private lateinit var gLineX: View
    private lateinit var gLineZ: View

    private lateinit var gLeftDash: View
    private lateinit var gRightDash: View

    lateinit var locationListener: CustomLocationListener

    private val runnable = Runnable {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(), previewView.height.toFloat()
        )

        val autoFocusPoint = factory.createPoint(
            previewView.width / 2.0f,
            previewView.height / 2.0f, qrOverlay.size
        )

        camConfig.camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(autoFocusPoint).disableAutoCancel().build()
        )

        startFocusTimer()
    }

    private val handler = Handler(Looper.getMainLooper())

    private var snackBar: Snackbar? = null

    private val autoRotateSettingObserver =
        object : ContentObserver(Handler(Looper.myLooper()!!)) {
            override fun onChange(selfChange: Boolean) {
                forceUpdateOrientationSensor()
            }
        }

    private lateinit var focusRing: ImageView

    private val focusRingHandler: Handler = Handler(Looper.getMainLooper())
    private val focusRingCallback: Runnable = Runnable {
        focusRing.visibility = View.INVISIBLE
    }

    lateinit var micOffIcon: ImageView

    fun startFocusTimer() {
        handler.postDelayed(runnable, autoCenterFocusDuration)
    }

    fun cancelFocusTimer() {
        handler.removeCallbacks(runnable)
    }

    // Used to request permission from the user
    private val requestPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        if (permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted for recording audio.")
            } else {
                Log.i(TAG, "Permission denied for recording audio.")
                val builder =
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                builder.setTitle(R.string.audio_permission_dialog_title)
                builder.setMessage(R.string.audio_permission_dialog_message)

                // Open the settings menu for the current app
                builder.setPositiveButton("Settings") { _: DialogInterface?, _: Int ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts(
                        "package",
                        packageName, null
                    )
                    intent.data = uri
                    startActivity(intent)
                }
                builder.setNegativeButton("Cancel", null)

                builder.setNeutralButton("Disable Audio") { _: DialogInterface?, _: Int ->
                    camConfig.includeAudio = false
                }

                audioPermissionDialog = builder.show()
            }
        }
        if (permissions.containsKey(Manifest.permission.CAMERA)) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted for camera.")
            } else {
                Log.i(TAG, "Permission denied for camera.")
            }
        }
    }

    // Used to request permission from the user
    var dirPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result ->

        val data: Uri? = result.data?.data

        if (data?.encodedPath != null) {
            val file = File(data.encodedPath!!)
            if (file.exists()) {
                showMessage("File exists: ${file.absolutePath}")
            } else {
                showMessage(
                    "File does not exist :( ${data.encodedPath!!} "
                )
            }
        }

        Log.i(TAG, "Selected location: ${data?.encodedPath!!}")
    }

    fun updateLastFrame() {
        lastFrame = previewView.bitmap
    }

    private fun animateFocusRing(x: Float, y: Float) {

        // Move the focus ring so that its center is at the tap location (x, y)
        val width = focusRing.width.toFloat()
        focusRing.x = x - width / 2
        focusRing.y = y - width / 2

        // Show focus ring
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f

        if (areSystemAnimationsEnabled()) {
            // Animate the focus ring to disappear
            focusRing.animate()
                .setStartDelay(500)
                .setDuration(300)
                .alpha(0f)
                .setListener(object : Animator.AnimatorListener {

                    var isCancelled = false

                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animator: Animator) {

                        if (!isCancelled) {
                            focusRing.visibility = View.INVISIBLE
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
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )

        val transition: Float = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        )

        return duration != 0f && transition != 0f
    }

    protected open fun openGallery() {

        if (camConfig.latestMediaFile == null) {
            showMessage(
                "Please capture a photo/video before trying to view" +
                        " them."
            )
            return
        }

        val intent = Intent(this, InAppGallery::class.java)

        intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
        intent.putExtra("is_secure_mode", false)
        startActivity(intent)
    }

    private fun checkPermissions() {
        Log.i(TAG, "Checking camera status...")

        // Check if the app has access to the user's camera
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) ==
                    PackageManager.PERMISSION_GRANTED -> {

                // If the user has manually granted the permission, dismiss the dialog.
                if (cameraPermissionDialog != null && cameraPermissionDialog!!.isShowing) cameraPermissionDialog!!.cancel()
                Log.i(TAG, "Permission granted.")

                // Setup the camera since the permission is available
                camConfig.initializeCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {

                Log.i(TAG, "The user has default denied camera permission.")

                // Don't build and show a new dialog if it's already visible
                if (cameraPermissionDialog != null && cameraPermissionDialog!!.isShowing) return
                val builder =
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                builder.setTitle(R.string.camera_permission_dialog_title)
                builder.setMessage(R.string.camera_permission_dialog_message)
                val positiveClicked = AtomicBoolean(false)

                // Open the settings menu for the current app
                builder.setPositiveButton("Settings") { _: DialogInterface?, _: Int ->
                    positiveClicked.set(true)
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts(
                        "package",
                        packageName, null
                    )
                    intent.data = uri
                    startActivity(intent)
                }
                builder.setNegativeButton("Cancel", null)
                builder.setOnDismissListener {

                    // The dialog could have either been dismissed by clicking on the
                    // background or by clicking the cancel button. So in those cases,
                    // the app should exit as the app depends on the camera permission.
                    if (!positiveClicked.get()) {
                        finish()
                    }
                }
                cameraPermissionDialog = builder.show()
            }

            // Request for the permission (Android will actually popup the permission
            // dialog in this case)
            else -> {
                Log.i(TAG, "Requesting permission from user...")

                requestPermissionLauncher.launch(cameraPermission)
            }
        }

        audioPermissionDialog?.let { dialog ->
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }

        locationListener.locationPermissionDialog?.let { dialog ->
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }

        if (!camConfig.isQRMode && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            captureButton.performClick()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Pretend as if the event was handled by the app (avoid volume bar from appearing)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        resumeOrientationSensor()
        // Check camera permission again if the user switches back to the app (maybe
        // after enabling/disabling the camera permission in Settings)
        // Will also be called by Android Lifecycle when the app starts up
        checkPermissions()

        if (camConfig.isQRMode) {
            startFocusTimer()
        }

        camConfig.latestUri = null

        if (camConfig.latestMediaFile == null) {
            imagePreview.setImageResource(android.R.color.transparent)
        }

        if (camConfig.requireLocation) {
            locationListener.start()
        }

        gCircleFrame.visibility = if (camConfig.gSuggestions) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        // If the preview of video capture activity isn't showing
        if (!(this is VideoCaptureActivity && thirdOption.visibility == View.VISIBLE)) {
            if (!isQRDialogShowing) {
                camConfig.initializeCamera(true)
            }
        }
    }

    val requiresVideoModeOnly: Boolean
        get() {
            return this is VideoOnlyActivity || this is VideoCaptureActivity
        }

    override fun onPause() {
        super.onPause()
        pauseOrientationSensor()
        if (camConfig.isQRMode) {
            cancelFocusTimer()
        }
        lastFrame = null

        if (camConfig.requireLocation)
            locationListener.stop()
    }

    lateinit var gestureDetectorCompat: GestureDetectorCompat

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gestureDetectorCompat = GestureDetectorCompat(this, this)

        camConfig = CamConfig(this)
        mainOverlay = findViewById(R.id.main_overlay)
        imageCapturer = ImageCapturer(this)
        videoCapturer = VideoCapturer(this)
        thirdOption = findViewById(R.id.third_option)
        previewLoader = findViewById(R.id.preview_loading)
        imagePreview = findViewById(R.id.image_preview)
        camConfig.updatePreview()
        previewView = findViewById(R.id.preview)
        previewView.scaleType = PreviewView.ScaleType.FIT_START
        previewContainer = findViewById(R.id.preview_container)
        scaleGestureDetector = ScaleGestureDetector(this, this)
        dbTapGestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.i(TAG, "===============Double tap detected.=========")
//                val zoomState = config.camera!!.cameraInfo.zoomState.value
//                if (zoomState != null) {
//                    val start = zoomState.linearZoom
//                    var end = start * 1.5f
//                    if (end < 0.25f) end = 0.25f else if (end > zoomState.maxZoomRatio) end =
//                        zoomState.maxZoomRatio
//                    val animator = ValueAnimator.ofFloat(start, end)
//                    animator.duration = 300
//                    animator.addUpdateListener { valueAnimator: ValueAnimator ->
//                        config.camera!!.cameraControl.setLinearZoom(
//                            valueAnimator.animatedValue as Float
//                        )
//                    }
//                    animator.start()
//                }
                return super.onDoubleTap(e)
            }
        })

        tabLayout = findViewById(R.id.camera_mode_tabs)

        tabLayout.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                val tab = tabLayout.getTabAtX(tabLayout.scrollX)
                finalizeMode(tab)
                return@setOnTouchListener true
            }

            return@setOnTouchListener false
        }

        timerView = findViewById(R.id.timer)
        previewView.previewStreamState.observe(this) { state: StreamState ->
            if (state == StreamState.STREAMING) {
                mainOverlay.visibility = View.INVISIBLE
                camConfig.reloadSettings()
                if (!camConfig.isQRMode) {
                    previewGrid.visibility = View.VISIBLE
                    if (!settingsDialog.isShowing) {
                        settingsIcon.visibility = View.VISIBLE
                    }
                    settingsIcon.isEnabled = true
                }
            } else {
                previewGrid.visibility = View.INVISIBLE
                if (lastFrame != null && this !is CaptureActivity) {
                    mainOverlay.setImageBitmap(blurBitmap(lastFrame!!))
                    settingsIcon.visibility = View.INVISIBLE
                    settingsIcon.isEnabled = false
                    mainOverlay.visibility = View.VISIBLE
                }
            }
        }
        flipCameraCircle = findViewById(R.id.flip_camera_circle)

        flipCamIcon = findViewById(R.id.flip_camera_icon_content)

        var tapDownTimestamp: Long = 0
        flipCameraCircle.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (tapDownTimestamp == 0L) {
                        tapDownTimestamp = System.currentTimeMillis()
                        Log.i(TAG, "I was called!")
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

            if (camConfig.isQRMode) {
                camConfig.scanAllCodes = !camConfig.scanAllCodes
                return@setOnClickListener
            }

            if (videoCapturer.isRecording) {
                videoCapturer.isPaused = !videoCapturer.isPaused
                return@setOnClickListener
            }

            val flipCameraIcon: ImageView = findViewById(R.id.flip_camera_icon)
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
            camConfig.toggleCameraSelector()
        }
        thirdCircle = findViewById(R.id.third_circle)
        thirdCircle.setOnClickListener {
            if (videoCapturer.isRecording) {
                imageCapturer.takePicture()
            } else {
                if (imageCapturer.isTakingPicture) {
                    showMessage(
                        "Please wait for the image to get captured " +
                                "before trying to open the gallery."
                    )
                } else {
                    openGallery()
                }
                Log.i(TAG, "Attempting to open gallery...")
            }
        }

        thirdCircle.setOnLongClickListener {

            if (videoCapturer.isRecording) {
                imageCapturer.takePicture()
            } else {
                if (imageCapturer.isTakingPicture) {
                    showMessage(
                        "Please wait for the image to get " +
                                "captured before attempting to share via long tap"
                    )
                } else {
                    shareLatestMedia()
                }

            }

            return@setOnLongClickListener true
        }

        captureButton = findViewById(R.id.capture_button)
        captureButton.setOnClickListener {
            if (camConfig.isVideoMode) {
                if (videoCapturer.isRecording) {
                    videoCapturer.stopRecording()
                } else {
                    videoCapturer.startRecording()
                }
            } else if (camConfig.isQRMode) {
                camConfig.toggleTorchState()
                captureButton.setImageResource(
                    if (camConfig.isTorchOn) {
                        R.drawable.torch_on_button
                    } else {
                        R.drawable.torch_off_button
                    }
                )
            } else {
                if (timerDuration == 0) {
                    imageCapturer.takePicture()
                } else {
                    if (cdTimer.isRunning) {
                        cdTimer.cancelTimer()
                    } else {
                        cdTimer.startTimer()
                    }
                }
            }
        }

        cancelButtonView = findViewById(R.id.cancel_button)
//        cancelButtonView.setOnClickListener(object : View.OnClickListener {
//
//            val SWITCH_ANIM_DURATION = 150
//            override fun onClick(v: View) {
//
//                val imgID = if (config.isVideoMode) R.drawable.video_camera else R.drawable.camera
//                config.switchCameraMode()
//                val oa1 = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0f)
//                val oa2 = ObjectAnimator.ofFloat(v, "scaleX", 0f, 1f)
//                oa1.interpolator = DecelerateInterpolator()
//                oa2.interpolator = AccelerateDecelerateInterpolator()
//                oa1.duration = SWITCH_ANIM_DURATION.toLong()
//                oa2.duration = SWITCH_ANIM_DURATION.toLong()
//                oa1.addListener(object : AnimatorListenerAdapter() {
//                    override fun onAnimationEnd(animation: Animator) {
//                        super.onAnimationEnd(animation)
//                        cancelButtonView.setImageResource(imgID)
//                        oa2.start()
//                    }
//                })
//                oa1.start()
//                if (config.isVideoMode) {
//                    captureButton.setImageResource(R.drawable.recording)
//                    cbText.visibility = View.INVISIBLE
//                } else {
//                    captureButton.setImageResource(R.drawable.camera_shutter)
//                    if (timerDuration != 0) {
//                        cbText.visibility = View.VISIBLE
//                    }
//                }
//            }
//        })

        zoomBar = findViewById(R.id.zoom_bar)
        zoomBar.setMainActivity(this)

        zoomBarPanel = findViewById(R.id.zoom_bar_panel)

        exposureBar = findViewById(R.id.exposure_bar)
        exposureBar.setMainActivity(this)

        exposureBarPanel = findViewById(R.id.exposure_bar_panel)

        qrOverlay = findViewById(R.id.qr_overlay)
        qrOverlay.post {
            qrOverlay.setViewFinder()
        }

        threeButtons = findViewById(R.id.three_buttons)
        settingsIcon = findViewById(R.id.settings_option)
        settingsIcon.setOnClickListener {
            if (!camConfig.isQRMode)
                settingsDialog.show()
        }

        settingsIcon.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val displayCutout = window.decorView.rootWindowInsets.displayCutout
                    val layoutParams = (settingsIcon.layoutParams as RelativeLayout.LayoutParams)

                    val rect = if (displayCutout?.boundingRects?.isNotEmpty() == true)
                        displayCutout.boundingRects.first() else null

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

                    if (rect == null || rect.left == 0 || rect.right == windowsSize.right) {
                        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                    } else {
                        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    }
                }
            })

        exposurePlusIcon = findViewById(R.id.exposure_plus_icon)
        exposureNegIcon = findViewById(R.id.exposure_neg_icon)

        zoomInIcon = findViewById(R.id.zoom_in_icon)
        zoomOutIcon = findViewById(R.id.zoom_out_icon)

        previewGrid = findViewById(R.id.preview_grid)
        previewGrid.setMainActivity(this)

        rootView = findViewById(R.id.root)

        mainFrame = findViewById(R.id.main_frame)

        qrScanToggles = findViewById(R.id.qr_scan_toggles)

        var isInsetSet = false

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.setMargins(
                    insets.left,
                    0,
                    insets.right,
                    insets.bottom,
                )

                it
            }

            if (insets.top != 0 && !isInsetSet) {
                mainFrame.layoutParams =
                    (mainFrame.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            insets.top,
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

        WindowInsetsControllerCompat(window, rootView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        cdTimer = findViewById(R.id.c_timer)
        cdTimer.setMainActivity(this)

        cbText = findViewById(R.id.capture_button_text)
        cbCross = findViewById(R.id.capture_button_cross)

        settingsDialog = SettingsDialog(this)

        locationListener = CustomLocationListener(this)

        snackBar = Snackbar.make(
            previewView,
            "",
            Snackbar.LENGTH_LONG
        )

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            true,
            autoRotateSettingObserver
        )

        focusRing = findViewById(R.id.focusRing)

        micOffIcon = findViewById(R.id.mic_off)

        previewView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    previewView.viewTreeObserver.removeOnPreDrawListener(this)
                    repositionTabLayout()
                    return true
                }
            }
        )

        moreOptionsToggle = findViewById(R.id.more_options)
        moreOptionsToggle.setOnClickListener {
            camConfig.showMoreOptionsForQR()
        }

        qrToggle = findViewById(R.id.qr_scan_toggle)
        qrToggle.mActivity = this
        qrToggle.key = BarcodeFormat.QR_CODE.name

        dmToggle = findViewById(R.id.data_matrix_toggle)
        dmToggle.mActivity = this
        dmToggle.key = BarcodeFormat.DATA_MATRIX.name

        cBToggle = findViewById(R.id.pdf417_toggle)
        cBToggle.mActivity = this
        cBToggle.key = BarcodeFormat.PDF_417.name

        azToggle = findViewById(R.id.aztec_toggle)
        azToggle.mActivity = this
        azToggle.key = BarcodeFormat.AZTEC.name

        camConfig.loadSettings()

        gCircle = findViewById(R.id.g_circle)
        gAngleTextView = findViewById(R.id.g_circle_text)

        gLineX = findViewById(R.id.g_circle_line_x)
        gLineZ = findViewById(R.id.g_circle_line_z)

        gLeftDash = findViewById(R.id.g_circle_left_dash)
        gRightDash = findViewById(R.id.g_circle_right_dash)

        gCircleFrame = findViewById(R.id.g_circle_frame)
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else Rect().apply { window.decorView.getWindowVisibleDisplayFrame(this) }.top
    }

    private fun repositionTabLayout() {

        threeButtons.visibility = View.VISIBLE

        tabLayout.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {

                    tabLayout.viewTreeObserver
                        .removeOnPreDrawListener(
                            this
                        )

                    val previewHeight169 = previewContainer.width * 16 / 9

                    val previewHeight43 = previewContainer.width * 4 / 3

                    val extraHeight169 = previewContainer.height -
                            previewHeight169 -
                            tabLayout.height -
                            10 * resources.displayMetrics.density.toInt()

                    val halfOfExtraHeight = (previewContainer.height -
                            previewHeight43) / 2

                    tabLayout.layoutParams =
                        (tabLayout.layoutParams as ViewGroup.MarginLayoutParams).let {

                            it.setMargins(
                                it.leftMargin,
                                it.topMargin,
                                it.rightMargin,
                                if (extraHeight169 > 0) {
                                    extraHeight169
                                } else {
                                    it.bottomMargin
                                }
                            )

                            it
                        }

                    qrScanToggles.layoutParams =
                        (qrScanToggles.layoutParams as ViewGroup.MarginLayoutParams).let {

                            it.height = halfOfExtraHeight

                            it
                        }

                    return true
                }

            })
    }

    fun finalizeMode(tab: TabLayout.Tab? = null) {

        val selectedTab = tab ?: tabLayout.selectedTab
        if (selectedTab != null) {
            val mode = selectedTab.id
            tabLayout.centerTab(selectedTab)
            tab?.let { tabLayout.centerTab(it) }
            camConfig.switchMode(mode)
        }
    }

    fun requestAudioPermission() {
        requestPermissionLauncher.launch(audioPermission)
    }

    private fun shareLatestMedia() {

        val mediaUri = camConfig.latestUri

        if (mediaUri == null) {
            showMessage(
                "Please capture a photo/video before attempting to share via long tap"
            )
            return
        }

        val share = Intent(Intent.ACTION_SEND)
        share.data = mediaUri
        share.putExtra(Intent.EXTRA_STREAM, mediaUri)
        share.type = if (VideoCapturer.isVideo(mediaUri)) {
            "video/*"
        } else {
            "image/*"
        }

        startActivity(Intent.createChooser(share, "Share Image"))
    }

    private lateinit var dialog: Dialog

    open fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size)
        for (i in 0 until bytes.indices.last - 1 step 3) {
            val v: Int = bytes[i].toInt() and 0xFF
            hexChars[i] = hexArray[v ushr 4]
            hexChars[i + 1] = hexArray[v and 0x0F]
            hexChars[i + 2] = ' '
        }
        return String(hexChars)
            .replace("(.{12})".toRegex(), "$0  ")
            .replace("(.{28})".toRegex(), "$0\n")
    }

    private var isQRDialogShowing = false

    fun onScanResultSuccess(rawText: String) {

        if (isQRDialogShowing) return

        isQRDialogShowing = true

        val hString = bytesToHex(
            rawText.toByteArray(StandardCharsets.UTF_8)
        )

        runOnUiThread {
            dialog = Dialog(this, R.style.Theme_Dialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.scan_result_dialog)
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            val tabLayout: TabLayout = dialog.findViewById(R.id.encoding_tabs)

            val textView = dialog.findViewById<View>(R.id.scan_result_text) as TextView

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.text.toString()) {
                        "Binary" -> {
                            textView.autoLinkMask = 0
                            textView.text = hString
                        }

                        "UTF-8" -> {
                            textView.autoLinkMask = Linkify.ALL
                            textView.text = rawText
                        }
                    }
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {}

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
            })

            tabLayout.addTab(tabLayout.newTab().apply {
                text = "UTF-8"
            })

            tabLayout.addTab(tabLayout.newTab().apply {
                text = "Binary"
            })

            val ctc: ImageButton = dialog.findViewById(R.id.copy_qr_text)
            ctc.setOnClickListener {
                val clipboardManager = getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager
                val clipData = ClipData.newPlainText(
                    "text",
                    textView.text
                )
                clipboardManager.setPrimaryClip(clipData)

                showMessage("Copied text to clipboard!")
            }

            val sButton: ImageButton = dialog.findViewById(
                R.id.share_qr_text
            )
            sButton.setOnClickListener {
                val sIntent = Intent(Intent.ACTION_SEND)
                sIntent.type = "text/plain"
                sIntent.putExtra(Intent.EXTRA_TEXT, textView.text.toString())
                startActivity(
                    Intent.createChooser(
                        sIntent,
                        "Share text via"
                    )
                )
            }

            dialog.setOnDismissListener {
                isQRDialogShowing = false
                camConfig.startCamera(true)
            }

            camConfig.cameraProvider?.unbindAll()

            dialog.show()
        }
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        return BlurBitmap[bitmap]
    }

//    private val fTHandler : Handler = Handler(Looper.getMainLooper())
//    private val fTRunnable : Runnable = Runnable {
//        config.mPlayer.playFocusCompleteSound()
//    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        dbTapGestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
        gestureDetectorCompat.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_DOWN) return true else if (event.action == MotionEvent.ACTION_UP) {

            if (wasSwiping) {
                wasSwiping = false
                return wasSwiping
            }

            if (isZooming) {
                isZooming = false
                return true
            }

            if (camConfig.isQRMode)
                return false

            val x = event.x
            val y = event.y

            val autoFocusPoint = previewView.meteringPointFactory.createPoint(x, y)
            animateFocusRing(x, y)

            val focusBuilder = FocusMeteringAction.Builder(autoFocusPoint)

            camConfig.mPlayer.playFocusStartSound()

            if (camConfig.focusTimeout == 0L) {
                focusBuilder.disableAutoCancel()
            } else {
                focusBuilder.setAutoCancelDuration(camConfig.focusTimeout, TimeUnit.SECONDS)
//                fTHandler.removeCallbacks(fTRunnable)
//                fTHandler.postDelayed(fTRunnable, focusTimeout * 1000)
            }

            camConfig.camera!!.cameraControl.startFocusAndMetering(focusBuilder.build())

            exposureBar.showPanel()
            zoomBar.showPanel()
            return v.performClick()
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        isZooming = true
        val zoomState = camConfig.camera!!.cameraInfo.zoomState.value
        var scale = 1f
        if (zoomState != null) {
            scale = zoomState.zoomRatio * detector.scaleFactor
        }
        camConfig.camera!!.cameraControl.setZoomRatio(scale)
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    private fun rotateView(view: View?, angle: Float) {
        if (view != null) {
            view.animate().cancel()

            // Ensuring that the rotation seems continuous
            if (view.rotation == 0f && angle == 270f)
                view.rotation = 360f

            if (view.rotation == 270f && angle == 0f)
                view.rotation = -90f

            view.animate()
                .rotation(angle)
                .setDuration(400)
                .setInterpolator(LinearInterpolator())
                .start()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onOrientationChange(orientation: Int) {

        val tr = when (orientation) {
            in 45..134 -> Surface.ROTATION_270
            in 135..224 -> Surface.ROTATION_180
            in 225..314 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

        camConfig.imageCapture?.targetRotation = tr
        camConfig.videoCapture?.targetRotation = tr
        camConfig.iAnalyzer?.targetRotation = tr

        if (videoCapturer.isRecording) return

        var iconRotation = (360f - orientation) % 360

        // Rotate views that should rotate irrespective of the auto-rotate setting
        rotateView(gCircleFrame, iconRotation)

        // Set iconRotation to 0
        if (Settings.System.getInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION, 0
            ) != 1
        ) {
            iconRotation = 0f
        }

        // Rotate views that shouldn't be affected by the auto rotate setting
        // (Rotates back to 0 when the auto rotate gets toggled to off when the app
        // is running)
        rotateView(flipCameraCircle, iconRotation)
        rotateView(cancelButtonView, iconRotation)
        rotateView(thirdOption, iconRotation)

        rotateView(exposurePlusIcon, iconRotation)
        rotateView(exposureNegIcon, iconRotation)
        rotateView(zoomInIcon, iconRotation)
        rotateView(zoomOutIcon, iconRotation)
        rotateView(settingsDialog.settingsFrame, iconRotation)
    }

    companion object {
        private const val TAG = "GOCam"
        private const val autoCenterFocusDuration = 2000L
        private val hexArray = "0123456789ABCDEF".toCharArray()

        lateinit var camConfig: CamConfig

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent?) {}

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
        return false
    }

    override fun onLongPress(p0: MotionEvent?) {}

    override fun onFling(
        e1: MotionEvent, e2: MotionEvent,
        velocityX: Float, velocityY: Float
    ): Boolean {

        var result = false
        try {
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x

            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                    result = true
                }
            } else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) {
                    onSwipeBottom()
                } else {
                    onSwipeTop()
                }
                result = true
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return result
    }

    private fun onSwipeBottom() {
        if (isZooming || cdTimer.isRunning) return
        wasSwiping = true
        if (settingsDialog.isShowing) return

        if (camConfig.isQRMode) {
            if (!camConfig.scanAllCodes) {
                camConfig.showMoreOptionsForQR()
            }
        } else {
            if (settingsIcon.isEnabled) {
                settingsIcon.performClick()
            }
        }
    }

    private fun onSwipeRight() {

        if (isZooming || cdTimer.isRunning || videoCapturer.isRecording)
            return

        if (this is VideoOnlyActivity) return

        wasSwiping = true
        if (settingsDialog.isShowing) return


        val i = tabLayout.selectedTabPosition - 1

        Log.i(TAG, "onSwipeRight $i")
        tabLayout.getTabAt(i)?.let {
            finalizeMode(it)
        }
    }

    private fun onSwipeTop() {
        if (isZooming || cdTimer.isRunning || videoCapturer.isRecording) return
        wasSwiping = true
        settingsDialog.slideDialogUp()
    }

    private fun onSwipeLeft() {
        if (isZooming || cdTimer.isRunning) return

        if (this is VideoOnlyActivity) return

        wasSwiping = true
        if (settingsDialog.isShowing) return

        val i = tabLayout.selectedTabPosition + 1
        tabLayout.getTabAt(i)?.let {
            finalizeMode(it)
        }
    }

    override fun onSingleTapConfirmed(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTap(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTapEvent(p0: MotionEvent?): Boolean {
        return false
    }

    fun showMessage(msg: String) {
        snackBar?.setText(msg)
        snackBar?.show()
    }

    private fun pauseOrientationSensor() {
        SensorOrientationChangeNotifier
            .getInstance(this)?.remove(this)
    }

    private fun resumeOrientationSensor() {
        sensorNotifier?.addListener(this)
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
            display?.rotation ?: @Suppress("DEPRECATION")
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

    fun onDeviceAngleChange(xAngle: Float, zAngle: Float) {
        // If we are in photo mode and the countdown timer isn't running
        if (!(camConfig.isVideoMode || camConfig.isVideoMode || cdTimer.isRunning)) {

            if (gCircle.rotation != xAngle) {
                gCircle.rotation = xAngle
                gLineZ.rotation = xAngle
                gAngleTextView.text = getString(R.string.degree_format, abs(xAngle).toInt())
                if (xAngle == 0f) {
                    setThicknessOfGLines(4)
                    vibrateDevice()
                } else {
                    setThicknessOfGLines(2)
                }
            }

            Log.i(TAG, "zAngle: $zAngle")

            val lzAngle = when {
                zAngle < -45 -> {
                    -45
                }
                zAngle > 45 -> {
                    45
                }
                else -> {
                    zAngle
                }
            }.toFloat()

            if (zAngle.toInt() == 0) {
                gLineX.setBackgroundResource(R.drawable.yellow_shadow_rect)
                gLineZ.visibility = View.GONE

                gLeftDash.setBackgroundResource(R.drawable.yellow_shadow_rect)
                gRightDash.setBackgroundResource(R.drawable.yellow_shadow_rect)

                gAngleTextView.setTextColor(ContextCompat.getColor(this, R.color.z_yellow))

            } else {
                gLineX.setBackgroundResource(R.drawable.white_shadow_rect)
                gLineZ.visibility = View.VISIBLE

                gLeftDash.setBackgroundResource(R.drawable.white_shadow_rect)
                gRightDash.setBackgroundResource(R.drawable.white_shadow_rect)

                gAngleTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
            }

            val zOffset = (lzAngle / 60) * dp32

            gLineZ.layoutParams = (gLineZ.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.setMargins(
                    it.leftMargin,
                    it.topMargin,
                    it.rightMargin,
                    zOffset.toInt(),
                )
                it
            }
        }
    }

    private val dp32 by lazy {
        32 * resources.displayMetrics.density
    }

    private fun setThicknessOfGLines(dp: Int) {
        val t = dp * resources.displayMetrics.density

        gLeftDash.layoutParams = gLeftDash.layoutParams.let {
            it.height = t.toInt()
            it
        }

        gRightDash.layoutParams = gRightDash.layoutParams.let {
            it.height = t.toInt()
            it
        }

        gLineX.layoutParams = gLineX.layoutParams.let {
            it.height = t.toInt()
            it
        }
    }

    // Vibrates the device for 100 milliseconds.
    private fun vibrateDevice() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(50, 10))
    }

    override fun onDestroy() {
        super.onDestroy()
        SensorOrientationChangeNotifier.clearInstance()
    }
}
