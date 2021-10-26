package app.grapheneos.camera.config

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
import android.view.animation.Animation

import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.webkit.MimeTypeMap
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
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@SuppressLint("ApplySharedPref")
class CamConfig(private val mActivity: MainActivity) : SettingsConfig() {

    enum class Grid {
        NONE,
        THREE_BY_THREE,
        FOUR_BY_FOUR,
        GOLDEN_RATIO
    }

    var gridType: Grid = Grid.NONE
        set(value) {
            val editor = commonPref.edit()
            editor.putInt("grid", Grid.values().indexOf(value))
            editor.apply()

            field = value
        }

    var camera: Camera? = null

    var cameraProvider: ProcessCameraProvider? = null
    private lateinit var extensionsManager: ExtensionsManager

    var imageCapture: ImageCapture? = null
        private set

    var preview: Preview? = null
        private set

    var lensFacing = CameraSelector.LENS_FACING_BACK

    private var cameraMode = ExtensionMode.NONE

    private lateinit var cameraSelector: CameraSelector

    var videoQuality: Int = 0
        get() {
            return if (modePref.contains("video_quality")){
                mActivity.settingsDialog.titleToQuality(
                    modePref.getString("video_quality", "")!!
                )
            } else {
                QualitySelector.QUALITY_FHD
            }
        }
        set(value) {
            val option = mActivity.settingsDialog.videoQualitySpinner.selectedItem as String

            val editor = modePref.edit()
            editor.putString("video_quality", option)
            editor.commit()

            field = value
        }

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

    var latestFile: File? = null

    var flashMode: Int
        get() = if (imageCapture != null) imageCapture!!.flashMode else ImageCapture.FLASH_MODE_OFF

        set(flashMode) {

            if(::modePref.isInitialized){
                val editor = modePref.edit()
                editor.putInt("flash_mode", flashMode)
                editor.commit()

                Log.i(TAG, "Selected mode: (${getCurrentModeText()})" +
                        " ${modePref.getInt("flash_mode", -1)}")
            }

            imageCapture!!.flashMode = flashMode
            mActivity.settingsDialog.updateFlashMode()
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

    val mPlayer : TunePlayer = TunePlayer(mActivity)

    private var modeText : String = "CAMERA"

    var focusTimeout = 5L
        set(value) {
            val option =  if(value==0L) {
                "Off"
            } else {
                "${value}s"
            }

            val editor = commonPref.edit()
            editor.putString("focus_timeout", option)
            editor.apply()

            field = value
        }

    var enableCameraSounds : Boolean
        get() {
            return mActivity.settingsDialog.csSwitch.isChecked
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean("camera_sounds", value)
            editor.apply()

            mActivity.settingsDialog.csSwitch.isChecked = value
        }

    var requireLocation : Boolean
        get() {
            return mActivity.settingsDialog.locToggle.isChecked
        }
        set(value) {
            val editor = modePref.edit()
            editor.putBoolean("location", value)
            editor.apply()

            mActivity.settingsDialog.locToggle.isChecked = value
        }

    var selfIlluminate : Boolean
        get() {
            return mActivity.settingsDialog.selfIlluminationToggle.isChecked
        }
        set(value) {
            val editor = modePref.edit()
            editor.putBoolean("self_illumination", value)
            editor.commit()

            mActivity.settingsDialog.selfIlluminationToggle.isChecked = value
        }

    private val commonPref =
        mActivity.getSharedPreferences("commons", Context.MODE_PRIVATE)

    private lateinit var modePref : SharedPreferences

    private fun updatePrefMode() {
        val modeText = getCurrentModeText()
        modePref = mActivity.getSharedPreferences(modeText, Context.MODE_PRIVATE)
    }

    fun reloadSettings() {

        if(!modePref.contains("flash_mode")) {
            // pref config needs to be created

            val sEditor = modePref.edit()

            if (isVideoMode) {
                mActivity.settingsDialog.reloadQualities()
                val option = mActivity.settingsDialog.videoQualitySpinner.selectedItem as String
                sEditor.putString("video_quality", option)
            }

            if(lensFacing == CameraSelector.LENS_FACING_FRONT) {
                sEditor.putBoolean("self_illumination", false)
            }

            sEditor.putInt("flash_mode", ImageCapture.FLASH_MODE_OFF)

            sEditor.putBoolean("location", false)

            // apply is async and commit waits until the data is written
            sEditor.commit()

        } else if (isVideoMode) {
            modePref.getString("video_quality", null)?.let {
                mActivity.settingsDialog.reloadQualities(it)
            }
        }

        flashMode = modePref.getInt("flash_mode", ImageCapture.FLASH_MODE_OFF)
        requireLocation = modePref.getBoolean("location", false)
        selfIlluminate = modePref.getBoolean("self_illumination", false)

        mActivity.settingsDialog.showOnlyRelevantSettings()
    }

    @SuppressLint("ApplySharedPref")
    fun loadSettings(){

        // Create common config. if it's not created
        if(!commonPref.contains("camera_sounds")) {

            val editor = commonPref.edit()

            editor.putBoolean("camera_sounds", true)

            // Index for Grid.values() Default: NONE
            editor.putInt("grid", 0)

            editor.putString("focus_timeout", "5s")

            editor.putBoolean("emphasis_on_quality", true)

            editor.commit()
        }

        mActivity.settingsDialog.csSwitch.isChecked = commonPref.getBoolean("camera_sounds", true)

        gridType = Grid.values()[commonPref.getInt("grid", 0)]
        mActivity.settingsDialog.updateGridToggleUI()

        commonPref.getString("focus_timeout", "5s")?.let {
            mActivity.settingsDialog.updateFocusTimeout(it)
        }

        if (emphasisQuality) {
            mActivity.settingsDialog.cmRadioGroup.check(R.id.quality_radio)
        } else {
            mActivity.settingsDialog.cmRadioGroup.check(R.id.latency_radio)
        }
    }

    var emphasisQuality: Boolean
        get() {
            return commonPref.getBoolean("emphasis_on_quality", true)
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean("emphasis_on_quality", value)
            editor.commit()
        }

    fun getCurrentModeText() : String {

        val vp = if (isVideoMode) {
            "VIDEO"
        } else {
            "PHOTO"
        }

        return "$modeText-$vp"
    }

    fun updatePreview() {
        val lastModifiedFile = latestFile ?: return
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

    val latestMediaFile: File?
        get() {
            if (latestFile != null && latestFile!!.exists())
                return latestFile
            val dir = parentDir
            val files = dir!!.listFiles { file: File ->
                if (!file.isFile) return@listFiles false
                val ext = file.extension
                ext == "jpg" || ext == "png" || ext == "mp4"
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


    fun switchCameraMode() {
        isVideoMode = !isVideoMode
        startCamera(true)
    }


    private fun addToMediaStore(file: File, isVideo: Boolean): Uri? {

        val values: ContentValues = getContentValuesForData(file, isVideo)

        var uri: Uri? = null

        try {

            uri = if(isVideo)
                mActivity.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            else
                mActivity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Log.i(TAG, "Added image to media store!")
        } catch (th: Throwable) {
            if (uri != null) {
                mActivity.contentResolver.delete(uri, null, null)
            }

        }
        return uri
    }

    private fun getContentValuesForData(file: File, isVideo: Boolean): ContentValues {

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)

        val values = ContentValues()

        if (isVideo) {

            values.put(MediaStore.Video.Media.TITLE, file.nameWithoutExtension)
            values.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            values.put(MediaStore.Video.Media.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.DATE_TAKEN, file.lastModified())
                values.put(MediaStore.Video.Media.RELATIVE_PATH, file.parent)
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
            }

        } else {
            values.put(MediaStore.Images.Media.TITLE, file.nameWithoutExtension)
            values.put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.DATE_TAKEN, file.lastModified())
                values.put(MediaStore.Images.Media.RELATIVE_PATH, file.parent)
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
            }
        }

        return values
    }

    fun toggleFlashMode() {
        if (camera!!.cameraInfo.hasFlashUnit()) {

            flashMode = when(flashMode){
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
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
    @JvmOverloads
    fun startCamera(forced: Boolean = false) {
        if (!forced && camera != null) return

        updatePrefMode()
        if (mActivity.settingsDialog.torchToggle.isChecked) {
            mActivity.settingsDialog.torchToggle.isChecked = false
        }

        val rotation = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            val display = mActivity.display
            display?.rotation ?: @Suppress("DEPRECATION")
            mActivity.windowManager.defaultDisplay.rotation
        } else {
            // We don't really have any option here, but this initialization
            // ensures that the app doesn't break later when the below
            // deprecated option gets removed post Android R
            @Suppress("DEPRECATION")
            mActivity.windowManager.defaultDisplay.rotation
        }

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

                videoCapture =
                    VideoCapture.withOutput(
                        Recorder.Builder()
                            .setQualitySelector(QualitySelector.of(videoQuality))
                            .build()
                    )

                useCaseGroupBuilder.addUseCase(videoCapture!!)
            }

            if(mActivity !is VideoCaptureActivity) {
                imageCapture = builder
                    .setCaptureMode(
                        if(emphasisQuality) {
                            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        } else {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        }
                    )
                    .setTargetRotation(imageCapture?.targetRotation
                        ?: rotation)
                    .setTargetAspectRatio(
                        if(isVideoMode) {
                            AspectRatio.RATIO_16_9
                        } else {
                            AspectRatio.RATIO_4_3
                        }
                    )
                    .setFlashMode(flashMode)
                    .build()

                useCaseGroupBuilder.addUseCase(imageCapture!!)
            }
        }

        preview = Preview.Builder()
            .setTargetRotation(preview?.targetRotation
                ?: rotation)
            .setTargetAspectRatio(
                if(isVideoMode) {
                    AspectRatio.RATIO_16_9
                } else {
                    AspectRatio.RATIO_4_3
                }
            )
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
        animation.duration = 200
        animation.interpolator = LinearInterpolator()
        animation.repeatMode = Animation.REVERSE

        mActivity.mainOverlay.setImageResource(android.R.color.black)

        animation.setAnimationListener(
            object: Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                    mActivity.mainOverlay.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(p0: Animation?) {
                    mActivity.mainOverlay.visibility = View.INVISIBLE
                    mActivity.mainOverlay.setImageResource(android.R.color.transparent)
                    mActivity.updateLastFrame()
                }

                override fun onAnimationRepeat(p0: Animation?) {}

            }
        )

        mPlayer.playShutterSound()
        mActivity.mainOverlay.startAnimation(animation)
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

        this.modeText = modeText

        cameraMode = ExtensionMode.NONE

        cameraMode = if (modeText == "CAMERA"){
            ExtensionMode.NONE
        } else {
            extensionModes.indexOf(modeText)
        }

        mActivity.cancelFocusTimer()

        isQRMode = modeText == "QR SCAN"

        if (isQRMode){
            mActivity.qrOverlay.visibility = View.VISIBLE
            mActivity.threeButtons.visibility = View.INVISIBLE
            mActivity.captureModeView.visibility = View.INVISIBLE
            mActivity.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        } else {
            mActivity.qrOverlay.visibility = View.INVISIBLE
            mActivity.threeButtons.visibility = View.VISIBLE
            mActivity.captureModeView.visibility = View.VISIBLE
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
                throw Exception(
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
