package app.grapheneos.camera.config

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation

import android.view.animation.AlphaAnimation
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.AspectRatio
import androidx.camera.core.UseCaseGroup
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import app.grapheneos.camera.R
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.util.concurrent.Executors
import kotlin.math.roundToInt


class CamConfig(private val mActivity: MainActivity) : SettingsConfig() {

    enum class Grid {
        NONE,
        THREE_BY_THREE,
        FOUR_BY_FOUR,
        GOLDEN_RATIO
    }

    var gridType: Grid = Grid.NONE

    var camera: Camera? = null

    var cameraProvider: ProcessCameraProvider? = null
    private lateinit var extensionsManager: ExtensionsManager

    var imageCapture: ImageCapture? = null
        private set

    var preview: Preview? = null
        private set

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var cameraMode = ExtensionMode.NONE

    private lateinit var cameraSelector: CameraSelector

    var videoQuality: QualitySelector = Recorder.DEFAULT_QUALITY_SELECTOR

    private val cameraExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    var isVideoMode = false
        private set

    var isQRMode = false
        private set

    var videoCapture: VideoCapture<Recorder>? = null

    private var qrAnalyzer: QRAnalyzer? = null

    private var iAnalyzer: ImageAnalysis? = null

    var aspectRatio = AspectRatio.RATIO_4_3

    private var latestFile: File? = null

    var lastMediaUri: Uri? = null

    var flashMode: Int
        get() = if (imageCapture != null) imageCapture!!.flashMode else ImageCapture.FLASH_MODE_OFF
        set(flashMode) {
            imageCapture!!.flashMode = flashMode
        }

    val isFlashAvailable: Boolean
        get() = camera!!.cameraInfo.hasFlashUnit()

    val parentDirPath: String
        get() = parentDir!!.absolutePath

    private val parentDir: File?
        get() {
            val dirs = mActivity.externalMediaDirs
            var parentDir: File? = null
            for (dir in dirs) {
                if (dir != null) {
                    parentDir = dir
                    break
                }
            }
            if (parentDir != null) {
                parentDir = File(
                    parentDir.absolutePath,
                    mActivity.resources.getString(R.string.app_name)
                )
                if (parentDir.mkdirs()) {
                    Log.i(TAG, "Parent directory was successfully created")
                }
            }
            return parentDir
        }

    fun updatePreview() {
        val lastModifiedFile = latestMediaFile ?: return
        if (lastModifiedFile.extension == "mp4") {
            try {
                mActivity.imagePreview.setImageBitmap(
                    getVideoThumbnail(lastModifiedFile.absolutePath)
                )
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
        } else {
            mActivity.imagePreview.setImageURI(
                Uri.parse(lastModifiedFile.absolutePath)
            )
        }
    }

    fun setLatestFile(latestFile: File?) {
        this.latestFile = latestFile
    }

    val latestMediaFile: File?
        get() {
            if (latestFile != null && mediaExists(latestFile!!))
                return latestFile
            val dir = parentDir
            val files = dir!!.listFiles { file: File ->
                if (!file.isFile) return@listFiles false
                val ext = file.extension
                if(ext == "jpg" || ext == "png" || ext == "mp4"){
                    mediaExists(file)
                } else {
                    false
                }
            }
            if (files == null || files.isEmpty()) return null
            var lastModifiedFile = files[0]
            for (file in files) {
                if (lastModifiedFile.lastModified() < file.lastModified())
                    lastModifiedFile = file
            }
            latestFile = lastModifiedFile
            updatePreview()
            return latestFile
        }

    private fun mediaExists(file: File) : Boolean {

        if(!file.exists()) return false

        val projection = emptyArray<String>()

        val mediaUri: Uri = if (file.extension == "mp4") {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        mActivity.contentResolver.query(
            mediaUri,
            projection,
            MediaStore.Images.ImageColumns.DISPLAY_NAME + "=?",
            arrayOf(file.name),
            null
        ).use {
            return@mediaExists it?.count==1
        }

    }

    fun switchCameraMode() {
        isVideoMode = !isVideoMode
        startCamera(true)
    }


    fun toggleFlashMode() {
        if (camera!!.cameraInfo.hasFlashUnit()) {

            when(flashMode){

                ImageCapture.FLASH_MODE_OFF -> {
                    mActivity.settingsDialog.flashToggle.setImageResource(R.drawable.flash_on_circle)
                    flashMode = ImageCapture.FLASH_MODE_ON
                }

                ImageCapture.FLASH_MODE_ON -> {
                    mActivity.settingsDialog.flashToggle.setImageResource(R.drawable.flash_auto_circle)
                    flashMode = ImageCapture.FLASH_MODE_AUTO
                }

                ImageCapture.FLASH_MODE_AUTO -> {
                    mActivity.settingsDialog.flashToggle.setImageResource(R.drawable.flash_off_circle)
                    flashMode = ImageCapture.FLASH_MODE_OFF
                }
            }

//            flashMode =
//                if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_AUTO else imageCapture!!.flashMode + 1
        } else {
            Toast.makeText(
                mActivity, "Flash is unavailable" +
                        " for the current mode.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun toggleAspectRatio() {
        aspectRatio = if (aspectRatio==AspectRatio.RATIO_16_9) {
            AspectRatio.RATIO_4_3
        } else {
            AspectRatio.RATIO_16_9
        }
        startCamera(true)
    }

    fun toggleCameraSelector() {
        lensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera(true)
    }

    fun initializeCamera() {
        if (cameraProvider != null) {
            startCamera()
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mActivity)
        val extensionsManagerFuture = ExtensionsManager.getInstance(mActivity)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            if (::extensionsManager.isInitialized) {
                startCamera()
            } else {
                extensionsManagerFuture.addListener({
                    extensionsManager = extensionsManagerFuture.get()
                    startCamera()
                }, ContextCompat.getMainExecutor(mActivity))
            }
        }, ContextCompat.getMainExecutor(mActivity))
    }

    // Start the camera with latest hard configuration
    @SuppressLint("RestrictedApi")
    @JvmOverloads
    fun startCamera(forced: Boolean = false) {
        if (!forced && camera != null) return

        if (mActivity.isDestroyed || mActivity.isFinishing) return

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val builder = ImageCapture.Builder()

        // To use the last frame instead of showing a blank screen when
        // the camera that is being currently used gets unbind
        mActivity.updateLastFrame()

        // Unbind/close all other camera(s) [if any]
        cameraProvider!!.unbindAll()

        if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                cameraMode)) {
            cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraProvider!!, cameraSelector, cameraMode
            )
        } else {
            Log.i(TAG, "The current mode isn't available for this device ")
//            Toast.makeText(mActivity, "The current mode isn't available for this device",
//                Toast.LENGTH_LONG).show()
        }

        val useCaseGroupBuilder = UseCaseGroup.Builder()

        if (isQRMode) {
            qrAnalyzer = QRAnalyzer(mActivity)
            mActivity.startFocusTimer()
            iAnalyzer =
                ImageAnalysis.Builder()
                    .setTargetResolution(Size(960, 960))
                    .build()
            iAnalyzer!!.setAnalyzer(cameraExecutor, qrAnalyzer!!)
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            useCaseGroupBuilder.addUseCase(iAnalyzer!!)

        } else {
            if (isVideoMode || mActivity is VideoCaptureActivity) {

                // Forcing 16:9 for now as 4:3 is not supported for output
                mActivity.settingsDialog.aRToggle.isChecked = true
                aspectRatio = AspectRatio.RATIO_16_9

                videoCapture =
                    VideoCapture.withOutput(
                        Recorder.Builder()
                            .setQualitySelector(videoQuality)
                            .build()
                    )

                useCaseGroupBuilder.addUseCase(videoCapture!!)
            }

            if(mActivity !is VideoCaptureActivity) {
                imageCapture = builder
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(mActivity.windowManager.defaultDisplay.rotation)
                    .setTargetAspectRatio(aspectRatio)
                    .setFlashMode(flashMode)
                    .build()

                flashMode = ImageCapture.FLASH_MODE_OFF

                useCaseGroupBuilder.addUseCase(imageCapture!!)
            }
        }

        preview = Preview.Builder()
            .setTargetRotation(mActivity.windowManager.defaultDisplay.rotation)
            .setTargetAspectRatio(aspectRatio)
            .build()

        useCaseGroupBuilder.addUseCase(preview!!)

        preview!!.setSurfaceProvider(mActivity.previewView.surfaceProvider)

        camera = cameraProvider!!.bindToLifecycle(
            mActivity, cameraSelector,
            useCaseGroupBuilder.build()
        )

        loadTabs()

        camera!!.cameraInfo.zoomState.observe(mActivity, {
            if (it.linearZoom!=0f) {
                mActivity.zoomBar.updateThumb()
            }
        })

        mActivity.exposureBar.setExposureConfig(camera!!.cameraInfo.exposureState)

        // Focus camera on touch/tap
        mActivity.previewView.setOnTouchListener(mActivity)
//        if (!isFlashAvailable) {
//            mActivity.flashPager.currentItem = ImageCapture.FLASH_MODE_OFF
//            flashMode = ImageCapture.FLASH_MODE_OFF
//        }
    }

    fun snapPreview() {
        val animation: Animation = AlphaAnimation(1f, 0f)
        animation.duration = 100
        animation.interpolator = AccelerateDecelerateInterpolator()
        animation.repeatMode = Animation.REVERSE
        mActivity.imagePreview.startAnimation(animation)
    }

    private fun loadTabs() {

        val modes = getAvailableModes()
        val cModes = mActivity.tabLayout.getAllModes()

        var mae = true

        if (modes.size==cModes.size) {
            for (index in 0 until modes.size) {
                if (modes[index]!=cModes[index]) {
                    mae = false
                    break
                }
            }
        } else mae = false

        if (mae) return

        Log.i(TAG, "Refreshing tabs...")

        mActivity.tabLayout.removeAllTabs()

        modes.forEach { mode ->
            mActivity.tabLayout.newTab().let {
                mActivity.tabLayout.addTab(it.setText(mode), false)
                if (mode=="CAMERA") {
                    it.select()
                }
            }
        }
    }

    private fun getAvailableModes(): ArrayList<String> {
        val modes = arrayListOf<String>()

        if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                ExtensionMode.NIGHT)) {
            modes.add("NIGHT SIGHT")
        }

        if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                ExtensionMode.BOKEH)) {
            modes.add("PORTRAIT")
        }

        if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                ExtensionMode.HDR)) {
            modes.add("HDR")
        }

        if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                ExtensionMode.FACE_RETOUCH)) {
            modes.add("FACE RETOUCH")
        }

        if (!isVideoMode){
            modes.add("QR SCAN")
        }

        val mid = (modes.size/2f).roundToInt()
        modes.add(mid, "CAMERA")

        return modes
    }

    fun switchMode(modeText: String){

        cameraMode = ExtensionMode.NONE

        cameraMode = if (modeText == "CAMERA"){
            ExtensionMode.NONE
        } else {
            extensionModes.indexOf(modeText)
        }

        mActivity.cancelFocusTimer()

        isQRMode = modeText=="QR SCAN"

        if (isQRMode){
            mActivity.qrOverlay.visibility = View.VISIBLE
            mActivity.threeButtons.visibility = View.INVISIBLE
            mActivity.captureModeView.visibility = View.INVISIBLE
            mActivity.settingsIcon.visibility = View.INVISIBLE
            mActivity.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        } else {
            mActivity.qrOverlay.visibility = View.INVISIBLE
            mActivity.threeButtons.visibility = View.VISIBLE
            mActivity.captureModeView.visibility = View.VISIBLE
            mActivity.settingsIcon.visibility = View.VISIBLE
            mActivity.previewView.scaleType = PreviewView.ScaleType.FIT_START
        }

        startCamera(true)
    }

    companion object {
        private const val TAG = "CamConfig"
        private val extensionModes = arrayOf("CAMERA", "PORTRAIT", "HDR", "NIGHT SIGHT",
            "FACE RETOUCH", "AUTO")

        @JvmStatic
        @Throws(Throwable::class)
        fun getVideoThumbnail(p_videoPath: String?): Bitmap {

            val mBitmap: Bitmap
            var mMediaMetadataRetriever: MediaMetadataRetriever? = null

            try {
                mMediaMetadataRetriever = MediaMetadataRetriever()
                mMediaMetadataRetriever.setDataSource(p_videoPath)
                mBitmap = mMediaMetadataRetriever.frameAtTime!!
            } catch (m_e: Exception) {
                throw Throwable(
                    "Exception in retrieveVideoFrameFromVideo(String p_videoPath)"
                            + m_e.message
                )
            } finally {
                if (mMediaMetadataRetriever != null) {
                    mMediaMetadataRetriever.release()
                    mMediaMetadataRetriever.close()
                }
            }
            return mBitmap
        }
    }
}
