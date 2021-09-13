package app.grapheneos.camera.ui

import android.Manifest
import android.animation.Animator
import androidx.appcompat.app.AppCompatActivity
import android.view.View.OnTouchListener
import android.view.ScaleGestureDetector.OnScaleGestureListener
import app.grapheneos.camera.notifier.SensorOrientationChangeNotifier
import androidx.camera.view.PreviewView
import android.graphics.Bitmap
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.capturer.ImageCapturer
import app.grapheneos.camera.capturer.VideoCapturer
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import com.google.android.material.imageview.ShapeableImageView
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import app.grapheneos.camera.R
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.animation.ValueAnimator
import com.google.android.material.tabs.TabLayout
import androidx.camera.view.PreviewView.StreamState
import app.grapheneos.camera.adapter.FlashAdapter
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.AnimatorListenerAdapter
import android.content.DialogInterface
import android.net.Uri
import android.provider.Settings
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Allocation
import android.renderscript.Element
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnTouchListener, OnScaleGestureListener,
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
        var mediaId = ""
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val fileName = config.latestMediaFile!!.name
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
                val name =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DISPLAY_NAME))
                if (name == fileName) {
                    mediaId =
                        cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID))
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
        } else if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){

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
    }

    override fun onPause() {
        super.onPause()
        SensorOrientationChangeNotifier.getInstance(this)?.remove(this)
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
        val bitmap = config.latestPreview
        if (bitmap != null) imagePreview.setImageBitmap(bitmap)
        previewView = findViewById(R.id.camera)
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
        var selected: TabLayout.Tab?

        tabLayout.newTab().let { tabLayout.addTab(it.setText("Night Light")) } // NIGHT
        tabLayout.newTab().let { tabLayout.addTab(it.setText("Portrait")) } // BOKEH
        tabLayout.newTab().setText("Camera").also {
            selected = it
        }.let { tabLayout.addTab(it) } // AUTO
        tabLayout.newTab().let { tabLayout.addTab(it.setText("HDR")) } // HDR
        tabLayout.newTab().let { tabLayout.addTab(it.setText("Beauty")) } // Beauty
        //        tabLayout.addTab(tabLayout.newTab().setText("AR Effects"));

        selected?.select()
        timerView = findViewById(R.id.timer)
        val mainOverlay = findViewById<ImageView>(R.id.main_overlay)
        previewView.previewStreamState.observe(this, { state: StreamState ->
            if (state == StreamState.STREAMING) {
                mainOverlay.visibility = View.GONE
            } else {
                if (lastFrame != null) {
                    mainOverlay.setImageBitmap(blurRenderScript(lastFrame!!))
                    mainOverlay.visibility = View.VISIBLE
                }
            }
        })
        flipCameraCircle = findViewById(R.id.flip_camera_circle)
        flipCameraCircle.setOnClickListener { config.toggleCameraSelector() }
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
    }

    private fun blurRenderScript(smallBitmap: Bitmap): Bitmap {
        val defaultBitmapScale = 0.1f
        val width = (smallBitmap.width * defaultBitmapScale).roundToInt()
        val height = (smallBitmap.height * defaultBitmapScale).roundToInt()
        val inputBitmap = Bitmap.createScaledBitmap(smallBitmap, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)
        val renderScript = RenderScript.create(this)
        val theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        val tmpIn = Allocation.createFromBitmap(renderScript, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap)
        theIntrinsic.setRadius(4f)
        theIntrinsic.setInput(tmpIn)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)
        return outputBitmap
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
        view!!.animate()
            .rotation(angle)
            .setDuration(400)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    override fun onOrientationChange(orientation: Int) {
        var rotation = orientation
        val d = abs(flashPager.rotation - rotation)
        if (d >= 90) rotation = 360 - rotation
        rotateView(flashPager, rotation.toFloat())
        rotateView(flipCameraCircle, rotation.toFloat())
        rotateView(captureModeView, rotation.toFloat())
        rotateView(thirdOption, rotation.toFloat())
    }

    companion object {
        private const val TAG = "GOCam"
    }
}