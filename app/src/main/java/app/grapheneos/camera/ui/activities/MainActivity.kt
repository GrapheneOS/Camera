package app.grapheneos.camera.ui.activities

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
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


open class MainActivity : AppCompatActivity(),
    OnTouchListener,
    OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    SensorOrientationChangeNotifier.Listener {

    val audioPermission = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val cameraPermission = arrayOf(Manifest.permission.CAMERA)

    lateinit var previewView: PreviewView

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private var cameraPermissionDialog: AlertDialog? = null
    private var audioPermissionDialog: AlertDialog? = null
    private var lastFrame: Bitmap? = null
    lateinit var config: CamConfig

    lateinit var rootView: View

    lateinit var imageCapturer: ImageCapturer
    lateinit var videoCapturer: VideoCapturer

    lateinit var flipCameraCircle: View
    lateinit var captureModeView: ImageView
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

    lateinit var locationListener: CustomLocationListener

    private val runnable = Runnable {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(), previewView.height.toFloat()
        )

        val autoFocusPoint = factory.createPoint(
            previewView.width / 2.0f,
            previewView.height / 2.0f, qrOverlay.size
        )

        config.camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(autoFocusPoint).disableAutoCancel().build()
        )

        startFocusTimer()
    }

    private val handler = Handler(Looper.getMainLooper())

    private var snackBar : Snackbar? = null

    fun startFocusTimer() {
        handler.postDelayed(runnable, autoCenterFocusDuration)
    }

    fun cancelFocusTimer() {
        handler.removeCallbacks(runnable)
    }

    // Used to request permission from the user
    val requestPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        if (permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted for recording audio.")
            } else {
                Log.i(TAG, "Permission denied for recording audio.")
                val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
        checkPermissions()
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
        val focusRing = findViewById<ImageView>(R.id.focusRing)

        // Move the focus ring so that its center is at the tap location (x, y)
        val width = focusRing.width.toFloat()
        focusRing.x = x - width / 2
        focusRing.y = y

        // Show focus ring
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f

        // Animate the focus ring to disappear
        focusRing.animate()
            .setStartDelay(500)
            .setDuration(300)
            .alpha(0f)
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    focusRing.visibility = View.INVISIBLE
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            }).start()
    }

    protected open fun openGallery() {

        if(config.latestMediaFile==null){
            showMessage(
                "Please capture a photo/video before trying to view" +
                        " them."
            )
            return
        }

        val intent = Intent(this, InAppGallery::class.java)

        intent.putExtra("show_videos_only", this.requiresVideoModeOnly)
        startActivity(intent)
    }

    private fun checkPermissions() {
        Log.i(TAG, "Checking camera status...")

        // Check if the app has access to the user's camera
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            // If the user has manually granted the permission, dismiss the dialog.
            if (cameraPermissionDialog != null && cameraPermissionDialog!!.isShowing) cameraPermissionDialog!!.cancel()
            Log.i(TAG, "Permission granted.")

            // Setup the camera since the permission is available
            config.initializeCamera()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {

            Log.i(TAG, "The user has default denied camera permission.")

            // Don't build and show a new dialog if it's already visible
            if (cameraPermissionDialog != null && cameraPermissionDialog!!.isShowing) return
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
        else {
            Log.i(TAG, "Requesting permission from user...")
            requestPermissionLauncher.unregister()
            requestPermissionLauncher.launch(cameraPermission)
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

        if (!config.isQRMode && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
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
        SensorOrientationChangeNotifier.getInstance(this)?.addListener(this)
        // Check camera permission again if the user switches back to the app (maybe
        // after enabling/disabling the camera permission in Settings)
        // Will also be called by Android Lifecycle when the app starts up
        checkPermissions()

        if (config.isQRMode) {
            startFocusTimer()
        }

        config.latestUri = null

        if (config.latestMediaFile == null) {
            imagePreview.setImageResource(android.R.color.transparent)
        }

        if (config.requireLocation) {
            locationListener.start()
        }

        config.startCamera(true)
    }

    val requiresVideoModeOnly: Boolean
        get() {
            return this is VideoOnlyActivity || this is VideoCaptureActivity
        }

    override fun onPause() {
        super.onPause()
        SensorOrientationChangeNotifier.getInstance(this)?.remove(this)
        if (config.isQRMode) {
            cancelFocusTimer()
        }
        lastFrame = null

        if (config.requireLocation)
            locationListener.stop()
    }

    lateinit var gestureDetectorCompat: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gestureDetectorCompat = GestureDetectorCompat(this, this)

        config = CamConfig(this)
        mainOverlay = findViewById(R.id.main_overlay)
        imageCapturer = ImageCapturer(this)
        videoCapturer = VideoCapturer(this)
        thirdOption = findViewById(R.id.third_option)
        previewLoader = findViewById(R.id.preview_loading)
        imagePreview = findViewById(R.id.image_preview)
        config.updatePreview()
        previewView = findViewById(R.id.camera)
        previewView.scaleType = PreviewView.ScaleType.FIT_START
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
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val mode = tab?.id!!
                config.switchMode(mode)
                Log.i(TAG, "Selected Mode: $mode")
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                val mode = tab?.text.toString()
                Log.i(TAG, "Reselected Mode: $mode")
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        })

        timerView = findViewById(R.id.timer)
        previewView.previewStreamState.observe(this, { state: StreamState ->
            if (state == StreamState.STREAMING) {
                mainOverlay.visibility = View.INVISIBLE
                config.reloadSettings()
                if (!config.isQRMode) {
                    previewGrid.visibility = View.VISIBLE
                    if(!settingsDialog.isShowing) {
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
        })
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
            config.toggleCameraSelector()
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
        captureButton.setOnClickListener(View.OnClickListener {
            if (config.isVideoMode) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(audioPermission)
                    return@OnClickListener
                }
                if (videoCapturer.isRecording) {
                    videoCapturer.stopRecording()
                } else {
                    videoCapturer.startRecording()
                }
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
        })

        captureModeView = findViewById(R.id.capture_mode)
        captureModeView.setOnClickListener(object : View.OnClickListener {

            val SWITCH_ANIM_DURATION = 150
            override fun onClick(v: View) {

                val imgID = if (config.isVideoMode) R.drawable.video_camera else R.drawable.camera
                config.switchCameraMode()
                val oa1 = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0f)
                val oa2 = ObjectAnimator.ofFloat(v, "scaleX", 0f, 1f)
                oa1.interpolator = DecelerateInterpolator()
                oa2.interpolator = AccelerateDecelerateInterpolator()
                oa1.duration = SWITCH_ANIM_DURATION.toLong()
                oa2.duration = SWITCH_ANIM_DURATION.toLong()
                oa1.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        captureModeView.setImageResource(imgID)
                        oa2.start()
                    }
                })
                oa1.start()
                if (config.isVideoMode) {
                    captureButton.setImageResource(R.drawable.recording)
                    cbText.visibility = View.INVISIBLE
                } else {
                    captureButton.setImageResource(R.drawable.camera_shutter)
                    if (timerDuration != 0) {
                        cbText.visibility = View.VISIBLE
                    }
                }
            }
        })

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
            if (!config.isQRMode)
                settingsDialog.show()
        }

        exposurePlusIcon = findViewById(R.id.exposure_plus_icon)
        exposureNegIcon = findViewById(R.id.exposure_neg_icon)

        zoomInIcon = findViewById(R.id.zoom_in_icon)
        zoomOutIcon = findViewById(R.id.zoom_out_icon)

        previewGrid = findViewById(R.id.preview_grid)
        previewGrid.setMainActivity(this)

        rootView = findViewById(R.id.root)

        cdTimer = findViewById(R.id.c_timer)
        cdTimer.setMainActivity(this)

        cbText = findViewById(R.id.capture_button_text)
        cbCross = findViewById(R.id.capture_button_cross)

        settingsDialog = SettingsDialog(this)
        config.loadSettings()

        locationListener = CustomLocationListener(this)

        snackBar = Snackbar.make(
            previewView,
            "",
            Snackbar.LENGTH_LONG
        )
    }

    private fun shareLatestMedia() {

        val mediaUri = config.latestUri

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

                showMessage(
                    "Copied to QR text to clipboard!"
                )
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
                        "Share QR text via"
                    )
                )
            }

            dialog.setOnDismissListener {
                isQRDialogShowing = false
                config.startCamera(true)
            }

            config.cameraProvider?.unbindAll()

            dialog.show()
        }
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        return BlurBitmap.get(bitmap)
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

            if (config.isQRMode)
                return false

            val x = event.x
            val y = event.y

            val autoFocusPoint = previewView.meteringPointFactory.createPoint(x, y)
            animateFocusRing(x, y)

            val focusBuilder = FocusMeteringAction.Builder(autoFocusPoint)

            config.mPlayer.playFocusStartSound()

            if (config.focusTimeout == 0L) {
                focusBuilder.disableAutoCancel()
            } else {
                focusBuilder.setAutoCancelDuration(config.focusTimeout, TimeUnit.SECONDS)
//                fTHandler.removeCallbacks(fTRunnable)
//                fTHandler.postDelayed(fTRunnable, focusTimeout * 1000)
            }

            config.camera!!.cameraControl.startFocusAndMetering(focusBuilder.build())

            exposureBar.showPanel()
            return v.performClick()
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        isZooming = true
        val zoomState = config.camera!!.cameraInfo.zoomState.value
        var scale = 1f
        if (zoomState != null) {
            scale = zoomState.zoomRatio * detector.scaleFactor
        }
        config.camera!!.cameraControl.setZoomRatio(scale)
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    private fun rotateView(view: View?, angle: Float) {
        if (view != null) {
            view.animate().cancel()
            view.animate()
                .rotationBy(angle)
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

        config.imageCapture?.targetRotation = tr
        config.videoCapture?.targetRotation = tr

        if (videoCapturer.isRecording) return

        if (flipCameraCircle.rotation == 0f) {
            flipCameraCircle.rotation = 360f
        }

        val iconOrientation = flipCameraCircle.rotation
        val previousDeviceOrientation = (360 - iconOrientation) % 360
        // The smallest rotation between the device's previous and current orientation
        // e.g. -90 instead of +270

        val iconRotation =
            if(Settings.System.getInt(contentResolver,
                Settings.System.ACCELEROMETER_ROTATION, 0) == 1) {
                    val deviceRotation = ((orientation - previousDeviceOrientation) + 180) % 360 - 180
                    -deviceRotation
            } else {
                -iconOrientation + 360
            }

        rotateView(flipCameraCircle, iconRotation)
        rotateView(captureModeView, iconRotation)
        rotateView(thirdOption, iconRotation)

        rotateView(exposurePlusIcon, iconRotation)
        rotateView(exposureNegIcon, iconRotation)
        rotateView(zoomInIcon, iconRotation)
        rotateView(zoomOutIcon, iconRotation)
        rotateView(
            settingsDialog.findViewById(R.id.settings_dialog),
            iconRotation
        )
    }

    companion object {
        private const val TAG = "GOCam"
        private const val autoCenterFocusDuration = 2000L
        private val hexArray = "0123456789ABCDEF".toCharArray()

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
        if (settingsIcon.isEnabled) {
            settingsIcon.performClick()
        }
    }

    private fun onSwipeRight() {

        if (isZooming || cdTimer.isRunning) return

        wasSwiping = true

        val i = tabLayout.selectedTabPosition - 1

        Log.i(TAG, "onSwipeRight $i")
        tabLayout.getTabAt(i)?.let {
            tabLayout.selectTab(it)
        }
    }

    private fun onSwipeTop() {
//        Log.i(TAG, "onSwipeTop")
    }

    private fun onSwipeLeft() {
        if (isZooming || cdTimer.isRunning) return

        wasSwiping = true
        val i = tabLayout.selectedTabPosition + 1
        tabLayout.getTabAt(i)?.let {
            tabLayout.selectTab(it)
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
}
