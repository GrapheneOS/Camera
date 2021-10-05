package app.grapheneos.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation

import android.view.animation.AlphaAnimation
import app.grapheneos.camera.analyzer.QRAnalyzer
import java.util.concurrent.Executors
import kotlin.math.roundToInt


class CamConfig(private val mActivity: MainActivity) {

    var camera: Camera? = null

    var cameraProvider: ProcessCameraProvider? = null

    var imageCapture: ImageCapture? = null
        private set

    var preview: Preview? = null
        private set

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var cameraMode = ExtensionMode.NONE

    private lateinit var cameraSelector: CameraSelector

    private val extensionsManager by lazy {
        ExtensionsManager.getInstance(mActivity).get()
    }

    init {
        latestMediaFile
    }

    private val cameraExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    var isVideoMode = false
        private set

    var isQRMode = false
        private set

    var videoCapture: VideoCapture? = null

    private var qrAnalyzer: QRAnalyzer? = null

    private var iAnalyzer: ImageAnalysis? = null

    private var aspectRatio = AspectRatio.RATIO_16_9

    private var latestFile: File? = null

    private var flashMode: Int
        get() = if (imageCapture != null) imageCapture!!.flashMode else ImageCapture.FLASH_MODE_OFF
        set(flashMode) {
            imageCapture!!.flashMode = flashMode
        }
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
            if (latestFile != null) return latestFile
            val dir = parentDir
            val files = dir!!.listFiles { file: File ->
                if (!file.isFile) return@listFiles false
                val ext = file.extension
                ext == "jpg" || ext == "png" || ext == "mp4"
            }
            if (files == null || files.isEmpty()) return null
            var lastModifiedFile = files[0]
            for (file in files) {
                if (lastModifiedFile.lastModified() < file.lastModified()) lastModifiedFile = file
            }
            latestFile = lastModifiedFile
            return latestFile
        }

    fun switchCameraMode() {
        isVideoMode = !isVideoMode
        startCamera(true)
    }

    // Tells whether flash is available for the current mode
    private val isFlashAvailable: Boolean
        get() = camera!!.cameraInfo.hasFlashUnit()

    fun toggleFlashMode() {
        if (camera!!.cameraInfo.hasFlashUnit()) {
            flashMode =
                if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_AUTO else imageCapture!!.flashMode + 1
            mActivity.flashPager.currentItem = flashMode
        } else {
            Toast.makeText(
                mActivity, "Flash is unavailable" +
                        " for the current mode.",
                Toast.LENGTH_LONG
            ).show()
        }
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(
            mActivity
        )
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                startCamera()
            } catch (e: ExecutionException) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            } catch (e: InterruptedException) {
            }
        }, ContextCompat.getMainExecutor(mActivity))
    }

    // Start the camera with latest hard configuration
    @SuppressLint("RestrictedApi")
    @JvmOverloads
    fun startCamera(forced: Boolean = false) {
        if (!forced && camera != null) return

        if (mActivity.isDestroyed || mActivity.isFinishing) return

        preview = Preview.Builder()
            .setTargetRotation(mActivity.windowManager.defaultDisplay.rotation)
            .setTargetAspectRatio(aspectRatio)
            .build()

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val builder = ImageCapture.Builder()

        imageCapture = builder
            .setTargetRotation(mActivity.windowManager.defaultDisplay.rotation)
            .setTargetAspectRatio(aspectRatio)
            .setFlashMode(flashMode)
            .build()

        preview!!.setSurfaceProvider(mActivity.previewView.surfaceProvider)

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
            Log.i(TAG, "Auto mode isn't available for this device")
        }

        val useCaseGroupBuilder = UseCaseGroup.Builder()

        useCaseGroupBuilder.addUseCase(preview!!)

        if (isQRMode) {
            qrAnalyzer = QRAnalyzer(mActivity)
            mActivity.startFocusTimer()
            iAnalyzer =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(mActivity.previewView.width,
                        mActivity.previewView.height))
                    .build()
            iAnalyzer!!.setAnalyzer(cameraExecutor, qrAnalyzer!!)
            useCaseGroupBuilder.addUseCase(iAnalyzer!!)

        } else {
            if (isVideoMode) {
                videoCapture = VideoCapture
                    .Builder()
                    .setTargetAspectRatio(aspectRatio)
                    .build()

                useCaseGroupBuilder.addUseCase(videoCapture!!)
            }
            useCaseGroupBuilder.addUseCase(imageCapture!!)
        }

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
        if (!isFlashAvailable) {
            mActivity.flashPager.currentItem = ImageCapture.FLASH_MODE_OFF
            flashMode = ImageCapture.FLASH_MODE_OFF
        }
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

        if (modeText == "CAMERA"){
            if (extensionsManager.isExtensionAvailable(cameraProvider!!, cameraSelector,
                    ExtensionMode.AUTO)){
                cameraMode = ExtensionMode.AUTO
            }
        } else {
            cameraMode = extensionModes.indexOf(modeText)
        }

        mActivity.cancelFocusTimer()

        isQRMode = modeText=="QR SCAN"

        if (isQRMode){
            mActivity.qrOverlay.visibility = View.VISIBLE
            mActivity.threeButtons.visibility = View.INVISIBLE
            mActivity.flashPager.visibility = View.INVISIBLE
            mActivity.captureModeView.visibility = View.INVISIBLE
            mActivity.settingsIcon.visibility = View.INVISIBLE
        } else {
            mActivity.qrOverlay.visibility = View.INVISIBLE
            mActivity.threeButtons.visibility = View.VISIBLE
            mActivity.flashPager.visibility = View.VISIBLE
            mActivity.captureModeView.visibility = View.VISIBLE
            mActivity.settingsIcon.visibility = View.VISIBLE
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
