package app.grapheneos.camera.ui.activities

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View.OnTouchListener
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import app.grapheneos.camera.adapter.FlashAdapter
import app.grapheneos.camera.capturer.ImageCapturer
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.notifier.SensorOrientationChangeNotifier
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.QROverlay
import app.grapheneos.camera.ui.seekbar.ExposureBar
import app.grapheneos.camera.ui.seekbar.ZoomBar
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import android.widget.TextView
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.util.Linkify
import android.view.animation.*
import app.grapheneos.camera.BlurBitmap
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import android.view.MotionEvent

open class MainActivity : AppCompatActivity(), OnTouchListener, OnScaleGestureListener,
    SensorOrientationChangeNotifier.Listener {

    private val audioPermission = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val cameraPermission = arrayOf(Manifest.permission.CAMERA)

    lateinit var previewView: PreviewView

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private var cameraPermissionDialog: AlertDialog? = null
    private var audioPermissionDialog: AlertDialog? = null
    private var lastFrame: Bitmap? = null
    lateinit var flashPager: ViewPager2
    lateinit var config: CamConfig

    private lateinit var imageCapturer: ImageCapturer
    private lateinit var videoCapturer: VideoCapturer

    lateinit var flipCameraCircle: View
    lateinit var captureModeView: ImageView
    lateinit var tabLayout: BottomTabLayout
    lateinit var thirdCircle: ImageView
    lateinit var captureButton: ImageButton

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var dbTapGestureDetector: GestureDetector
    lateinit var timerView: TextView
    private lateinit var thirdOption: View
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

    private val runnable = Runnable {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(), previewView.height.toFloat()
        )

        val autoFocusPoint = factory.createPoint(previewView.width / 2.0f,
            previewView.height / 2.0f, qrOverlay.size)

        config.camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(autoFocusPoint).disableAutoCancel().build()
        )

        startFocusTimer()
    }

    private val handler = Handler(Looper.getMainLooper())

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
                val builder = AlertDialog.Builder(this)
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

    fun updateLastFrame() {
        lastFrame = previewView.bitmap
    }

    private fun animateFocusRing(x: Float, y: Float) {
        val focusRing = findViewById<ImageView>(R.id.focusRing)

        // Move the focus ring so that its center is at the tap location (x, y)
        val width = focusRing.width.toFloat()
        val height = focusRing.height.toFloat()
        focusRing.x = x - width / 2
        focusRing.y = y - height / 2

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

    private fun openGallery() {

        val latestMediaFile: File? = config.latestMediaFile

        if(latestMediaFile==null){
            Toast.makeText(this,
                "Please capture a image/video before trying to view it in gallery",
                Toast.LENGTH_LONG).show()
            return
        }

        val fileName = latestMediaFile.name

        var mediaId = ""
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        var mediaUri: Uri
        mediaUri = if (videoCapturer.isLatestMediaVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val cursor = contentResolver.query(
            mediaUri, projection, null, null, null
        )
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val dIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns
                    .DISPLAY_NAME)
                val name = cursor.getString(dIndex)

                if (name == fileName) {
                    val iIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns
                        ._ID)
                    mediaId = cursor.getString(iIndex)
                    break
                }
            }
            cursor.close()
        }
        if (mediaId != "") {
            mediaUri = mediaUri.buildUpon()
                .authority("media")
                .appendPath(mediaId)
                .build()
        }
        Log.d("TagInfo", "Uri:  $mediaUri")
        val intent = Intent(Intent.ACTION_VIEW, mediaUri)
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
            val builder = AlertDialog.Builder(this)
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
    }

    override fun onPause() {
        super.onPause()
        SensorOrientationChangeNotifier.getInstance(this)?.remove(this)
        if (config.isQRMode) {
            cancelFocusTimer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = CamConfig(this)
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
                val zoomState = config.camera!!.cameraInfo.zoomState.value
                if (zoomState != null) {
                    val start = zoomState.linearZoom
                    var end = start * 1.5f
                    if (end < 0.25f) end = 0.25f else if (end > zoomState.maxZoomRatio) end =
                        zoomState.maxZoomRatio
                    val animator = ValueAnimator.ofFloat(start, end)
                    animator.duration = 300
                    animator.addUpdateListener { valueAnimator: ValueAnimator ->
                        config.camera!!.cameraControl.setLinearZoom(
                            valueAnimator.animatedValue as Float
                        )
                    }
                    animator.start()
                }
                return super.onDoubleTap(e)
            }
        })

        tabLayout = findViewById(R.id.camera_mode_tabs)
        tabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val mode = tab?.text.toString()
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
        val mainOverlay = findViewById<ImageView>(R.id.main_overlay)
        previewView.previewStreamState.observe(this, { state: StreamState ->
            if (state == StreamState.STREAMING) {
                mainOverlay.visibility = View.INVISIBLE
            } else {
                if (lastFrame != null) {
                    mainOverlay.setImageBitmap(blurBitmap(lastFrame!!))
                    mainOverlay.visibility = View.VISIBLE
                }
            }
        })
        flipCameraCircle = findViewById(R.id.flip_camera_circle)

        var tapDownTimestamp: Long = 0
        flipCameraCircle.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(tapDownTimestamp==0L) {
                        tapDownTimestamp = System.currentTimeMillis()
                        Log.i(TAG, "I was called!")
                        flipCameraCircle.animate().scaleXBy(0.1f).setDuration(400).start()
                        flipCameraCircle.animate().scaleYBy(0.1f).setDuration(400).start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dif = System.currentTimeMillis() - tapDownTimestamp
                    if(dif<300){
                        flipCameraCircle.performClick()
                    }

                    tapDownTimestamp = 0
                    flipCameraCircle.animate().cancel()
                    flipCameraCircle.animate().scaleX(1f).setDuration(400).start()
                    flipCameraCircle.animate().scaleY(1f).setDuration(400).start()
                }
                else -> {
                }
            }
            true
        }
        flipCameraCircle.setOnClickListener {
            val flipCameraIcon: ImageView = findViewById(R.id.flip_camera_icon)
            val rotation: Float = if (flipCameraIcon.rotation<180) {
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
                    Toast.makeText(
                        this, "Please wait for the image to get " +
                                "captured before trying to open the gallery.",
                        Toast.LENGTH_SHORT
                    ).show()
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
                    Toast.makeText(
                        this, "Please wait for the image to get " +
                                "captured before attempting to share via long tap",
                        Toast.LENGTH_SHORT
                    ).show()
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
                imageCapturer.takePicture()
            }
        })
        flashPager = findViewById(R.id.flash_pager)
        flashPager.adapter = FlashAdapter()
        flashPager.isUserInputEnabled = false

        flashPager.setOnClickListener { config.toggleFlashMode() }
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
                    captureButton.setBackgroundResource(0)
                    captureButton.setImageResource(R.drawable.start_recording)
                } else {
                    captureButton.setBackgroundResource(R.drawable.camera_shutter)
                    captureButton.setImageResource(0)
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
    }

    private fun shareLatestMedia(){

        val file: File? = config.latestMediaFile

        if(file==null){
            Toast.makeText(this,
                "Please capture a photo/video before attempting to share via long tap",
                Toast.LENGTH_LONG).show()
            return
        }

        // We'll be using an temporary file to avoid storage permission related issue on the
        // app where the user wants to share the media file but can't due to absence of
        // storage related permission

        val share = Intent(Intent.ACTION_SEND)
        val values = ContentValues()
        val uri: Uri?

        if(file.extension=="mp4"){
            // Share video file
            share.type = "video/mp4"
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            )
        } else {
            // Share image file
            share.type = "image/jpeg"
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        }

        val outStream: OutputStream? = contentResolver.openOutputStream(uri!!)
        outStream?.write(file.readBytes())
        outStream?.close()

        share.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(share, "Share Image"))
    }

    private lateinit var dialog: Dialog

    open fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size)
        for (i in 0 until bytes.indices.last-1 step 3) {
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

    fun onScanResultSuccess(rawText: String){

        if(isQRDialogShowing) return

        isQRDialogShowing = true

        val hString = bytesToHex(
            rawText.toByteArray(StandardCharsets.UTF_8))

        runOnUiThread {
            dialog = Dialog(this, R.style.Theme_Dialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.scan_result_dialog)
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            val tabLayout: TabLayout = dialog.findViewById(R.id.encoding_tabs)

            val textView = dialog.findViewById<View>(R.id.scan_result_text) as TextView

            tabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when(tab?.text.toString()){
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
                    Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("text",
                    textView.text)
                clipboardManager.setPrimaryClip(clipData)

                Toast.makeText(this, "Copied to QR text to" +
                        " clipboard!",
                    Toast.LENGTH_LONG).show()
            }

            val sButton: ImageButton = dialog.findViewById(
                R.id.share_qr_text)
            sButton.setOnClickListener {
                val sIntent = Intent(Intent.ACTION_SEND)
                sIntent.type = "text/plain"
                sIntent.putExtra(Intent.EXTRA_TEXT, textView.text)
                startActivity(Intent.createChooser(sIntent,
                    "Share QR text via"))
            }

            dialog.setOnDismissListener {
                isQRDialogShowing = false
                config.startCamera(true)
            }

            config.cameraProvider?.unbindAll()

            dialog.show()
        }
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap
    {
        return BlurBitmap.get(bitmap)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        dbTapGestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) return true else if (event.action == MotionEvent.ACTION_UP) {
            if (isZooming) {
                isZooming = false
                return true
            }
            val x = event.x
            val y = event.y
            val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(), previewView.height.toFloat()
            )
            val autoFocusPoint = factory.createPoint(x, y)
            animateFocusRing(x, y)
            config.camera!!.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    autoFocusPoint,
                    FocusMeteringAction.FLAG_AF
                ).disableAutoCancel().build()
            )
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
        if(view!=null){
            view.animate().cancel()
            view.animate()
                .rotation(angle)
                .setDuration(400)
                .setInterpolator(LinearInterpolator())
                .start()
        }
    }

    override fun onOrientationChange(orientation: Int) {
        var rotation = orientation

        val tr = when (orientation) {
            in 45..134 -> Surface.ROTATION_270
            in 135..224 -> Surface.ROTATION_180
            in 225..314 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

        config.imageCapture?.targetRotation = tr
//        config.iAnalyzer?.targetRotation = tr

        val d = abs(flashPager.rotation - rotation)
        if (d >= 90) rotation = 360 - rotation
        rotateView(flashPager, rotation.toFloat())
        rotateView(flipCameraCircle, rotation.toFloat())
        rotateView(captureModeView, rotation.toFloat())
        rotateView(thirdOption, rotation.toFloat())
    }

    companion object {
        private const val TAG = "GOCam"
        private const val autoCenterFocusDuration = 2000L
        private val hexArray = "0123456789ABCDEF".toCharArray()
    }
}