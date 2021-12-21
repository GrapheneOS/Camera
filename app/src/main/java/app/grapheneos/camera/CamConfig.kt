package app.grapheneos.camera

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.capturer.VideoCapturer.Companion.isVideo
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureCaptureActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import com.google.zxing.BarcodeFormat
import java.util.concurrent.Executors
import android.widget.Button
import androidx.camera.video.Quality
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig

@SuppressLint("ApplySharedPref")
class CamConfig(private val mActivity: MainActivity) {

    enum class GridType {
        NONE,
        THREE_BY_THREE,
        FOUR_BY_FOUR,
        GOLDEN_RATIO
    }

    private object CameraModes {
        const val CAMERA = R.string.camera
        const val VIDEO = R.string.video
        const val PORTRAIT = R.string.portrait_mode
        const val HDR = R.string.hdr_mode
        const val NIGHT_SIGHT = R.string.night_sight_mode
        const val FACE_RETOUCH = R.string.face_retouch_mode
        const val AUTO = R.string.auto_mode
        const val QR_SCAN = R.string.qr_scan_mode
    }

    private object SettingValues {

        object Key {
            const val SELF_ILLUMINATION = "self_illumination"
            const val GEO_TAGGING = "geo_tagging"
            const val FLASH_MODE = "flash_mode"
            const val GRID = "grid"
            const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"
            const val FOCUS_TIMEOUT = "focus_timeout"
            const val CAMERA_SOUNDS = "camera_sounds"
            const val VIDEO_QUALITY = "video_quality"
            const val ASPECT_RATIO = "aspect_ratio"
            const val INCLUDE_AUDIO = "include_audio"
            const val SCAN = "scan"
            const val SCAN_ALL_CODES = "scan_all_codes"
            const val SAVE_IMAGE_AS_PREVIEW = "save_image_as_preview"

            const val STORAGE_LOCATION = "storage_location"
            const val MEDIA_URIS = "media_uri_s"

            const val PHOTO_QUALITY = "photo_quality"

            const val REMOVE_EXIF_AFTER_CAPTURE = "remove_exif_after_capture"

            const val GYROSCOPE_SUGGESTIONS = "gyroscope_suggestions"

            // const val IMAGE_FILE_FORMAT = "image_quality"
            // const val VIDEO_FILE_FORMAT = "video_quality"
        }

        object Default {

            val GRID_TYPE = GridType.NONE
            const val GRID_TYPE_INDEX = 0

            const val ASPECT_RATIO = AspectRatio.RATIO_4_3

            val VIDEO_QUALITY = Quality.UHD

            const val SELF_ILLUMINATION = false

            const val GEO_TAGGING = false

            const val FLASH_MODE = ImageCapture.FLASH_MODE_OFF

            const val EMPHASIS_ON_QUALITY = true

            const val FOCUS_TIMEOUT = "5s"

            const val CAMERA_SOUNDS = true

            const val INCLUDE_AUDIO = true

            const val SCAN_ALL_CODES = false

            const val SAVE_IMAGE_AS_PREVIEW = false

            const val STORAGE_LOCATION = ""
            const val MEDIA_URIS = ""

            const val PHOTO_QUALITY = 0

            const val REMOVE_EXIF_AFTER_CAPTURE = false

            const val GYROSCOPE_SUGGESTIONS = false

            // const val IMAGE_FILE_FORMAT = ""
            // const val VIDEO_FILE_FORMAT = ""
        }
    }

    companion object {
        private const val TAG = "CamConfig"

        private const val PREVIEW_SNAP_DURATION = 200L
        private const val PREVIEW_SL_OVERLAY_DUR = 200L

        const val DEFAULT_LENS_FACING = CameraSelector.LENS_FACING_BACK

        const val DEFAULT_EXTENSION_MODE = ExtensionMode.NONE

        val commonFormats = arrayOf(
            BarcodeFormat.AZTEC,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.PDF_417,
        )

        val extensionModes = arrayOf(
            CameraModes.CAMERA,
            CameraModes.PORTRAIT,
            CameraModes.HDR,
            CameraModes.NIGHT_SIGHT,
            CameraModes.FACE_RETOUCH,
            CameraModes.AUTO,
        )

        val imageCollectionUri: Uri = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )!!

        val videoCollectionUri: Uri = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )!!

        const val DEFAULT_CAMERA_MODE = CameraModes.CAMERA

        const val COMMON_SP_NAME = "commons"

        @JvmStatic
        @Throws(Throwable::class)
        fun getVideoThumbnail(context: Context, uri: Uri?): Bitmap {

            val mBitmap: Bitmap
            var mMediaMetadataRetriever: MediaMetadataRetriever? = null

            try {
                mMediaMetadataRetriever = MediaMetadataRetriever()
                mMediaMetadataRetriever.setDataSource(context, uri)
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

        const val PATH_SEPARATOR = ";"
    }

    var camera: Camera? = null

    var cameraProvider: ProcessCameraProvider? = null
    private lateinit var extensionsManager: ExtensionsManager

    var imageCapture: ImageCapture? = null
        private set

    private var preview: Preview? = null

    val allowedFormats : ArrayList<BarcodeFormat> = arrayListOf()

    private val cameraExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    var videoCapture: VideoCapture<Recorder>? = null

    private var qrAnalyzer: QRAnalyzer? = null

    var iAnalyzer: ImageAnalysis? = null

    var latestUri: Uri? = null

    val mPlayer: TunePlayer = TunePlayer(mActivity)

    private val commonPref = when (mActivity) {
        is SecureMainActivity -> {
            mActivity.getSharedPreferences(COMMON_SP_NAME
                    + mActivity.openedActivityAt, Context.MODE_PRIVATE)
        }
        is SecureCaptureActivity -> {
            mActivity.getSharedPreferences(COMMON_SP_NAME
                    + mActivity.openedActivityAt, Context.MODE_PRIVATE)
        }
        else -> {
            mActivity.getSharedPreferences(COMMON_SP_NAME, Context.MODE_PRIVATE)
        }
    }

    private lateinit var modePref: SharedPreferences


    var isVideoMode = false
        private set
        get() {
            return field ||
                    mActivity is VideoCaptureActivity ||
                    mActivity is VideoOnlyActivity
        }

    var isQRMode = false
        private set

    val isFlashAvailable: Boolean
        get() = camera!!.cameraInfo.hasFlashUnit()

    var isTorchOn : Boolean = false
        get(){
            return camera?.cameraInfo?.torchState?.value == TorchState.ON
        }
        set(value) {
            field = if(isFlashAvailable) {
                camera?.cameraControl?.enableTorch(value)
                value
            } else {
                false
            }
        }

    private var modeText: Int = DEFAULT_CAMERA_MODE

    var aspectRatio : Int
        get() {
            return when {
                isVideoMode -> {
                    AspectRatio.RATIO_16_9
                }
                isQRMode -> {
                    AspectRatio.RATIO_4_3
                }
                else -> {
                    commonPref.getInt(
                        SettingValues.Key.ASPECT_RATIO,
                        SettingValues.Default.ASPECT_RATIO
                    )
                }
            }
        }

        set(value) {
            val editor = commonPref.edit()
            editor.putInt(SettingValues.Key.ASPECT_RATIO, value)
            editor.apply()
        }

    var lensFacing = DEFAULT_LENS_FACING

    private var cameraMode = DEFAULT_EXTENSION_MODE

    private lateinit var cameraSelector: CameraSelector

    var gridType: GridType = SettingValues.Default.GRID_TYPE
        set(value) {
            val editor = commonPref.edit()
            editor.putInt(SettingValues.Key.GRID, GridType.values().indexOf(value))
            editor.apply()

            field = value
        }

    var videoQuality: Quality? = SettingValues.Default.VIDEO_QUALITY
        get() {
            return if (modePref.contains(videoQualityKey)) {
                mActivity.settingsDialog.titleToQuality(
                    modePref.getString(videoQualityKey, "")!!
                )
            } else {
                SettingValues.Default.VIDEO_QUALITY
            }
        }
        set(value) {
            val option = mActivity.settingsDialog.videoQualitySpinner.selectedItem as String

            val editor = modePref.edit()
            editor.putString(videoQualityKey, option)
            editor.commit()

            field = value
        }

    private val videoQualityKey: String
        get() {

            val pf = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                "FRONT"
            } else {
                "BACK"
            }

            return "${SettingValues.Key.VIDEO_QUALITY}_$pf"
        }

    var flashMode: Int
        get() = if (imageCapture != null) imageCapture!!.flashMode else
            SettingValues.Default.FLASH_MODE
        set(flashMode) {

            if (::modePref.isInitialized) {
                val editor = modePref.edit()
                editor.putInt(SettingValues.Key.FLASH_MODE, flashMode)
                editor.commit()
            }

            imageCapture?.flashMode = flashMode
            mActivity.settingsDialog.updateFlashMode()
        }

    var focusTimeout = 5L
        set(value) {
            val option = if (value == 0L) {
                "Off"
            } else {
                "${value}s"
            }

            val editor = commonPref.edit()
            editor.putString(SettingValues.Key.FOCUS_TIMEOUT, option)
            editor.apply()

            field = value
        }

    var enableCameraSounds: Boolean
        get() {
            return mActivity.settingsDialog.csSwitch.isChecked
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.CAMERA_SOUNDS, value)
            editor.apply()

            mActivity.settingsDialog.csSwitch.isChecked = value
        }

    var scanAllCodes: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.SCAN_ALL_CODES,
                SettingValues.Default.SCAN_ALL_CODES
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.SCAN_ALL_CODES, value)
            editor.apply()

            if (isQRMode) {
                if (value) {
                    mActivity.flipCamIcon.setImageResource(
                        R.drawable.cancel
                    )
                    mActivity.qrScanToggles.visibility = View.GONE
                } else {
                    mActivity.flipCamIcon.setImageResource(
                        R.drawable.auto
                    )
                    mActivity.qrScanToggles.visibility = View.VISIBLE
                }
            }

            qrAnalyzer?.refreshHints()
        }

    var includeAudio: Boolean
        get() {
            return mActivity.settingsDialog.includeAudioToggle.isChecked
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.INCLUDE_AUDIO, value)
            editor.apply()

            mActivity.settingsDialog.includeAudioToggle.isChecked = value
        }

    var saveImageAsPreviewed: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.SAVE_IMAGE_AS_PREVIEW,
                SettingValues.Default.SAVE_IMAGE_AS_PREVIEW
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.SAVE_IMAGE_AS_PREVIEW, value)
            editor.apply()
        }

    var storageLocation: String
        get() {
            return commonPref.getString(
                SettingValues.Key.STORAGE_LOCATION,
                SettingValues.Default.STORAGE_LOCATION
            )!!
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putString(SettingValues.Key.STORAGE_LOCATION, value)
            editor.apply()
        }

    val mediaUris : List<Uri>
        get() {
            val uriPaths = commonPref.getString(
                SettingValues.Key.MEDIA_URIS,
                SettingValues.Default.MEDIA_URIS
            )!!.split(PATH_SEPARATOR)

            val uris = uriPaths.map {
                Uri.parse(it)
            }

            return uris
        }

    var photoQuality : Int
        get() {
            return commonPref.getInt(
                SettingValues.Key.PHOTO_QUALITY,
                SettingValues.Default.PHOTO_QUALITY
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putInt(SettingValues.Key.PHOTO_QUALITY, value)
            editor.apply()
        }

    var removeExifAfterCapture : Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.REMOVE_EXIF_AFTER_CAPTURE,
                SettingValues.Default.REMOVE_EXIF_AFTER_CAPTURE
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(
                SettingValues.Key.REMOVE_EXIF_AFTER_CAPTURE,
                value
            )
            editor.apply()
        }

    var gSuggestions : Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.GYROSCOPE_SUGGESTIONS,
                SettingValues.Default.GYROSCOPE_SUGGESTIONS
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(
                SettingValues.Key.GYROSCOPE_SUGGESTIONS,
                value
            )
            editor.apply()
        }

    val isInCaptureMode : Boolean
        get() {
            return mActivity is CaptureActivity
        }

//    var imageFormat : String
//        get() {
//            return commonPref.getString(
//                SettingValues.Key.IMAGE_FILE_FORMAT,
//                SettingValues.Default.IMAGE_FILE_FORMAT
//            )!!
//        }
//        set(value) {
//            val editor = commonPref.edit()
//            editor.putString(
//                SettingValues.Key.IMAGE_FILE_FORMAT,
//                value
//            )
//            editor.apply()
//        }
//
//    var videoFormat : String
//        get() {
//            return commonPref.getString(
//                SettingValues.Key.VIDEO_FILE_FORMAT,
//                SettingValues.Default.VIDEO_FILE_FORMAT
//            )!!
//        }
//        set(value) {
//            val editor = commonPref.edit()
//            editor.putString(
//                SettingValues.Key.VIDEO_FILE_FORMAT,
//                value
//            )
//            editor.apply()
//        }

    @SuppressLint("MutatingSharedPrefs")
    fun addToGallery(uri: Uri) {

        val path = uri.toString()

        val uriPathData = commonPref.getString(
            SettingValues.Key.MEDIA_URIS,
            SettingValues.Default.MEDIA_URIS
        )!!

        val resultData = if (uriPathData.isEmpty()) {
            path
        } else {
            "$path$PATH_SEPARATOR$uriPathData"
        }

        camConfig.latestUri = uri

        val editor = commonPref.edit()
        editor.putString(SettingValues.Key.MEDIA_URIS, resultData)
        editor.commit()
    }

    @SuppressLint("MutatingSharedPrefs")
    fun removeFromGallery(uri: Uri) {

        val path = uri.toString()

        val uriPathData = commonPref.getString(
            SettingValues.Key.MEDIA_URIS,
            SettingValues.Default.MEDIA_URIS
        )!!

        val uriPaths = ArrayList<String>()
        uriPaths.addAll(uriPathData.split(PATH_SEPARATOR))
        uriPaths.remove(path)

        val editor = commonPref.edit()
        editor.putString(
            SettingValues.Key.MEDIA_URIS,
            uriPaths.joinToString(PATH_SEPARATOR)
        )
        editor.commit()
    }

    var requireLocation: Boolean = false
        get() {
            return mActivity.settingsDialog.locToggle.isChecked
        }
        set(value) {

            if (value) {
                // If location listener wasn't previously set
                if(!field) {
                    mActivity.locationListener.start()
                }
            } else {
                mActivity.locationListener.stop()
            }

            val editor = modePref.edit()
            editor.putBoolean(SettingValues.Key.GEO_TAGGING, value)
            editor.commit()

            mActivity.settingsDialog.locToggle.isChecked = value

            field = value
        }

    var selfIlluminate: Boolean
        get() {
            return modePref.getBoolean(
                SettingValues.Key.SELF_ILLUMINATION,
                SettingValues.Default.SELF_ILLUMINATION
            )
                    && lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        set(value) {
            val editor = modePref.edit()
            editor.putBoolean(SettingValues.Key.SELF_ILLUMINATION, value)
            editor.commit()

            mActivity.settingsDialog.selfIlluminationToggle.isChecked = value
            mActivity.settingsDialog.selfIllumination()
        }

    private fun updatePrefMode() {
        val modeText = getCurrentModeText()
        modePref = when (mActivity) {
            is SecureCaptureActivity -> {
                mActivity.getSharedPreferences(modeText + mActivity.openedActivityAt,
                    Context.MODE_PRIVATE)
            }
            is SecureMainActivity -> {
                mActivity.getSharedPreferences(modeText + mActivity.openedActivityAt,
                    Context.MODE_PRIVATE)
            }
            else -> {
                mActivity.getSharedPreferences(modeText, Context.MODE_PRIVATE)
            }
        }
    }

    fun setQRScanningFor(format: String, selected: Boolean) {

        val formatSRep = "${SettingValues.Key.SCAN}_$format"

        val editor = commonPref.edit()
        editor.putBoolean(
            formatSRep,
            selected
        )
        editor.commit()

        if(selected) {
            if (BarcodeFormat.valueOf(format) !in allowedFormats) {
                allowedFormats.add(BarcodeFormat.valueOf(format))
            }
        } else {
            if (allowedFormats.size == 1) {
                mActivity.showMessage(
                    "Please ensure that at least one barcode is " +
                            "selected in manual mode"
                )
            } else {
                allowedFormats.remove(BarcodeFormat.valueOf(format))
            }
        }

        qrAnalyzer?.refreshHints()
    }

    fun reloadSettings() {

        // pref config needs to be created
        val sEditor = modePref.edit()

        if (!modePref.contains(SettingValues.Key.FLASH_MODE)) {
            sEditor.putInt(SettingValues.Key.FLASH_MODE, SettingValues.Default.FLASH_MODE)
        }

        if (!modePref.contains(SettingValues.Key.GEO_TAGGING)) {
            sEditor.putBoolean(SettingValues.Key.GEO_TAGGING, SettingValues.Default.GEO_TAGGING)
        }

        if (isVideoMode) {

            if (!modePref.contains(videoQualityKey)) {
                mActivity.settingsDialog.reloadQualities()
                val option = mActivity.settingsDialog.videoQualitySpinner.selectedItem as String
                sEditor.putString(videoQualityKey, option)
            } else {
                modePref.getString(videoQualityKey, null)?.let {
                    mActivity.settingsDialog.reloadQualities(it)
                }
            }
        }

        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            if (!modePref.contains(SettingValues.Key.SELF_ILLUMINATION)) {
                sEditor.putBoolean(
                    SettingValues.Key.SELF_ILLUMINATION,
                    SettingValues.Default.SELF_ILLUMINATION
                )
            }
        }

        sEditor.commit()

        flashMode = modePref.getInt(
            SettingValues.Key.FLASH_MODE,
            SettingValues.Default.FLASH_MODE
        )

        requireLocation = modePref.getBoolean(
                SettingValues.Key.GEO_TAGGING,
                SettingValues.Default.GEO_TAGGING
            )

        selfIlluminate = modePref.getBoolean(
            SettingValues.Key.SELF_ILLUMINATION,
            SettingValues.Default.SELF_ILLUMINATION
        )

        mActivity.settingsDialog.showOnlyRelevantSettings()
    }

    @SuppressLint("ApplySharedPref")
    fun loadSettings() {

        // Create common config. if it's not created
        val editor = commonPref.edit()

        if (!commonPref.contains(SettingValues.Key.CAMERA_SOUNDS)) {
            editor.putBoolean(SettingValues.Key.CAMERA_SOUNDS, SettingValues.Default.CAMERA_SOUNDS)
        }

        if (!commonPref.contains(SettingValues.Key.SAVE_IMAGE_AS_PREVIEW)) {
            editor.putBoolean(
                SettingValues.Key.SAVE_IMAGE_AS_PREVIEW,
                SettingValues.Default.SAVE_IMAGE_AS_PREVIEW
            )
        }

        if (!commonPref.contains(SettingValues.Key.GRID)) {
            // Index for Grid.values() Default: NONE
            editor.putInt(SettingValues.Key.GRID, SettingValues.Default.GRID_TYPE_INDEX)
        }

        if (!commonPref.contains(SettingValues.Key.FOCUS_TIMEOUT)) {
            editor.putString(SettingValues.Key.FOCUS_TIMEOUT, SettingValues.Default.FOCUS_TIMEOUT)
        }

        if (!commonPref.contains(SettingValues.Key.EMPHASIS_ON_QUALITY)) {
            editor.putBoolean(
                SettingValues.Key.EMPHASIS_ON_QUALITY,
                SettingValues.Default.EMPHASIS_ON_QUALITY
            )
        }

        if (!commonPref.contains(SettingValues.Key.INCLUDE_AUDIO)) {
            editor.putBoolean(
                SettingValues.Key.INCLUDE_AUDIO,
                SettingValues.Default.INCLUDE_AUDIO
            )
        }

        if (!commonPref.contains(SettingValues.Key.ASPECT_RATIO)) {
            editor.putInt(SettingValues.Key.ASPECT_RATIO,
                SettingValues.Default.ASPECT_RATIO)
        }

        if (!commonPref.contains(SettingValues.Key.SCAN_ALL_CODES)) {
            editor.putBoolean(
                SettingValues.Key.SCAN_ALL_CODES,
                SettingValues.Default.SCAN_ALL_CODES
            )
        }

        val qrRep = "${SettingValues.Key.SCAN}_${BarcodeFormat.QR_CODE.name}"

        if (!commonPref.contains(qrRep)) {
            for (format in BarcodeFormat.values()) {
                val formatSRep = "${SettingValues.Key.SCAN}_${format.name}"

                editor.putBoolean(
                    formatSRep,
                    false
                )
            }

            editor.putBoolean(
                qrRep,
                true
            )
        }


        editor.commit()

        mActivity.settingsDialog.csSwitch.isChecked =
            commonPref.getBoolean(
                SettingValues.Key.CAMERA_SOUNDS,
                SettingValues.Default.CAMERA_SOUNDS
            )

        gridType = GridType.values()[commonPref.getInt(
            SettingValues.Key.GRID,
            SettingValues.Default.GRID_TYPE_INDEX
        )]

        mActivity.settingsDialog.updateGridToggleUI()

        commonPref.getString(SettingValues.Key.FOCUS_TIMEOUT, SettingValues.Default.FOCUS_TIMEOUT)
            ?.let {
                mActivity.settingsDialog.updateFocusTimeout(it)
            }

        if (emphasisQuality) {
            mActivity.settingsDialog.cmRadioGroup.check(R.id.quality_radio)
        } else {
            mActivity.settingsDialog.cmRadioGroup.check(R.id.latency_radio)
        }

        aspectRatio = commonPref.getInt(
            SettingValues.Key.ASPECT_RATIO,
            SettingValues.Default.ASPECT_RATIO
        )

        includeAudio = commonPref.getBoolean(
            SettingValues.Key.INCLUDE_AUDIO,
            SettingValues.Default.INCLUDE_AUDIO
        )

        allowedFormats.clear()

        for (format in BarcodeFormat.values()) {
            val formatSRep = "${SettingValues.Key.SCAN}_${format.name}"

            val isEnabled = commonPref.getBoolean(
                formatSRep,
                false
            )

            if (isEnabled) {
                if (format !in allowedFormats) {
                    allowedFormats.add(format)
                }

                if(format == BarcodeFormat.QR_CODE) {
                    mActivity.qrToggle.isSelected = true
                }

                if(format == BarcodeFormat.AZTEC) {
                    mActivity.azToggle.isSelected = true
                }

                if(format == BarcodeFormat.PDF_417) {
                    mActivity.cBToggle.isSelected = true
                }

                if(format == BarcodeFormat.DATA_MATRIX) {
                    mActivity.dmToggle.isSelected = true
                }
            }
        }

        qrAnalyzer?.refreshHints()
    }

    var emphasisQuality: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.EMPHASIS_ON_QUALITY,
                SettingValues.Default.EMPHASIS_ON_QUALITY
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.EMPHASIS_ON_QUALITY, value)
            editor.commit()
        }

    fun toggleTorchState() {
        isTorchOn = !isTorchOn
    }

    private fun getCurrentModeText(): String {

        val vp = if (isVideoMode) {
            "VIDEO"
        } else {
            "PHOTO"
        }

        return "$modeText-$vp"
    }

    fun updatePreview() {

        if (latestUri==null) return

        if (isVideo(latestUri!!)) {
            try {
                mActivity.imagePreview.setImageBitmap(
                    getVideoThumbnail(mActivity, latestUri)
                )
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
        } else {
            mActivity.imagePreview.setImageBitmap(null)
            mActivity.imagePreview.setImageURI(latestUri)
        }
    }

    val latestMediaFile: Uri?
        get() {
            if (latestUri != null) return latestUri

            if (mActivity is SecureMainActivity) {

                if (mActivity.capturedFilePaths.isNotEmpty()){
                    latestUri = Uri.parse(
                        mActivity.capturedFilePaths.last()
                    )
                }

            } else {
                var imageUri : Uri? = null
                var imageAddedOn : Int = -1

                if (mActivity !is VideoOnlyActivity) {
                    val imageCursor = mActivity.contentResolver.query(
                        imageCollectionUri,
                        arrayOf(
                            MediaStore.Images.ImageColumns._ID,
                            MediaStore.Images.ImageColumns.DATE_ADDED,
                        ),
                        null, null,
                        "${MediaStore.Images.ImageColumns.DATE_ADDED} DESC"
                    )

                    if (imageCursor!=null) {
                        if (imageCursor.moveToFirst()) {
                            imageUri = ContentUris
                                .withAppendedId(
                                    imageCollectionUri,
                                    imageCursor.getInt(0).toLong()
                                )

                            imageAddedOn = imageCursor.getInt(1)
                        }
                        imageCursor.close()
                    }
                }

                val videoCursor = mActivity.contentResolver.query(
                    videoCollectionUri,
                    arrayOf(
                        MediaStore.Video.VideoColumns._ID,
                        MediaStore.Video.VideoColumns.DATE_ADDED,
                    ),
                    null, null,
                    "${MediaStore.Video.VideoColumns.DATE_ADDED} DESC"
                )

                var videoUri : Uri? = null
                var videoAddedOn : Int = -1

                if (videoCursor!=null) {
                    if (videoCursor.moveToFirst()) {
                        videoUri = ContentUris
                            .withAppendedId(
                                videoCollectionUri,
                                videoCursor.getInt(0).toLong()
                            )

                        videoAddedOn = videoCursor.getInt(1)
                    }
                    videoCursor.close()
                }

                if (imageAddedOn == 0 && videoAddedOn == 0)
                    return null

                val mediaUri = if (imageAddedOn>videoAddedOn){
                    imageUri
                } else {
                    videoUri
                }

                latestUri = mediaUri
            }

            updatePreview()

            return latestUri
        }

    fun toggleFlashMode() {
        if (isFlashAvailable) {

            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }

        } else {
            mActivity.showMessage(
                "Flash is unavailable for the current mode."
            )
        }
    }

    fun toggleAspectRatio() {
        aspectRatio = if (aspectRatio == AspectRatio.RATIO_16_9) {
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

    fun initializeCamera(forced : Boolean = false) {
        if (cameraProvider != null) {
            startCamera(forced = forced)
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mActivity)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(mActivity, cameraProvider!!)

            extensionsManagerFuture.addListener({
                extensionsManager = extensionsManagerFuture.get()
                startCamera(forced = forced)
            }, ContextCompat.getMainExecutor(mActivity))

        }, ContextCompat.getMainExecutor(mActivity))
    }

    // Start the camera with latest hard configuration
    @JvmOverloads
    fun startCamera(forced: Boolean = false) {
        if ((!forced && camera != null) || cameraProvider==null) return

        mActivity.exposureBar.hidePanel()
        updatePrefMode()

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

        if (extensionsManager.isExtensionAvailable(cameraSelector, cameraMode)) {
            cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
                cameraSelector, cameraMode
            )
        } else {
            Log.i(TAG, "The current mode isn't available for this device ")
//            showMessage(mActivity, "The current mode isn't available for this device",
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
            if (isVideoMode) {

                mActivity.micOffIcon.visibility =
                    if (includeAudio) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }

                videoCapture =
                    VideoCapture.withOutput(
                        Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(videoQuality!!))
                            .build()
                    )

                useCaseGroupBuilder.addUseCase(videoCapture!!)
            }

            if (!mActivity.requiresVideoModeOnly) {
                imageCapture = builder.let {
                    it.setCaptureMode(
                        if (emphasisQuality) {
                            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        } else {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        }
                    )

                    it.setTargetRotation(
                        imageCapture?.targetRotation
                            ?: rotation
                    )

                    it.setTargetAspectRatio(aspectRatio)

                    it.setFlashMode(flashMode)

                    if (photoQuality != SettingValues.Default.PHOTO_QUALITY) {
                        it.setJpegQuality(photoQuality)
                    }

                    it.build()
                }

                useCaseGroupBuilder.addUseCase(imageCapture!!)
            }
        }

        preview = Preview.Builder()
            .setTargetRotation(
                preview?.targetRotation
                    ?: rotation
            )
            .setTargetAspectRatio(aspectRatio)
            .build()

        useCaseGroupBuilder.addUseCase(preview!!)

        preview!!.setSurfaceProvider(mActivity.previewView.surfaceProvider)

        mActivity.forceUpdateOrientationSensor()

        camera = cameraProvider!!.bindToLifecycle(
            mActivity, cameraSelector,
            useCaseGroupBuilder.build()
        )

        loadTabs()

        camera!!.cameraInfo.zoomState.observe(mActivity, {
            if (it.linearZoom != 0f) {
                mActivity.zoomBar.updateThumb()
            }
        })

        mActivity.zoomBar.updateThumb(false)

        mActivity.exposureBar.setExposureConfig(camera!!.cameraInfo.exposureState)

        mActivity.settingsDialog.torchToggle.isChecked = false

        // Focus camera on touch/tap
        mActivity.previewView.setOnTouchListener(mActivity)
    }

    fun snapPreview() {

        if (selfIlluminate) {

            mActivity.mainOverlay.layoutParams =
                (mActivity.mainOverlay.layoutParams as FrameLayout.LayoutParams).apply {
                    this.setMargins(
                        leftMargin,
                        0, // topMargin
                        rightMargin,
                        0 // bottomMargin
                    )
                }

            val animation: Animation = AlphaAnimation(0f, 0.8f)
            animation.duration = PREVIEW_SL_OVERLAY_DUR
            animation.interpolator = LinearInterpolator()
            animation.fillAfter = true

            mActivity.mainOverlay.setImageResource(android.R.color.white)

            animation.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                        mActivity.mainOverlay.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd(p0: Animation?) {}

                    override fun onAnimationRepeat(p0: Animation?) {}

                }
            )

            mActivity.mainOverlay.startAnimation(animation)

        } else {

            val animation: Animation = AlphaAnimation(1f, 0f)
            animation.duration = PREVIEW_SNAP_DURATION
            animation.interpolator = LinearInterpolator()
            animation.repeatMode = Animation.REVERSE

            mActivity.mainOverlay.setImageResource(android.R.color.black)

            animation.setAnimationListener(
                object : Animation.AnimationListener {
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

            mActivity.mainOverlay.startAnimation(animation)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadTabs() {

        val modes = getAvailableModes()
        val cModes = mActivity.tabLayout.getAllModes()

        var mae = true

        if (modes.size == cModes.size) {
            for (index in 0 until modes.size) {
                if (modes[index] != cModes[index]) {
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
                mActivity.tabLayout.addTab(it.apply {
                    setText(mode)
                    val tab = it
                    it.view.setOnTouchListener { _, e ->
                        if (e.action == MotionEvent.ACTION_UP) {
                            mActivity.finalizeMode(tab)
                        }
                        false
                    }
                    id = mode
                }, false)
                if (mode == DEFAULT_CAMERA_MODE) {
                    it.select()
                }
            }
        }
    }

    private fun getAvailableModes(): ArrayList<Int> {
        val modes = arrayListOf<Int>()

        if (mActivity !is SecureMainActivity) {
            modes.add(CameraModes.QR_SCAN)
        }

        if (extensionsManager.isExtensionAvailable(
                cameraSelector,
                ExtensionMode.NIGHT
            )
        ) {
            modes.add(CameraModes.NIGHT_SIGHT)
        }

        if (extensionsManager.isExtensionAvailable(
                cameraSelector,
                ExtensionMode.BOKEH
            )
        ) {
            modes.add(CameraModes.PORTRAIT)
        }

        if (extensionsManager.isExtensionAvailable(
                cameraSelector,
                ExtensionMode.HDR
            )
        ) {
            modes.add(CameraModes.HDR)
        }

        if (extensionsManager.isExtensionAvailable(
                cameraSelector,
                ExtensionMode.FACE_RETOUCH
            )
        ) {
            modes.add(CameraModes.FACE_RETOUCH)
        }

        modes.add(CameraModes.CAMERA)

        modes.add(CameraModes.VIDEO)

        return modes
    }

    fun switchMode(modeText: Int) {

        if (this.modeText == modeText) return

        this.modeText = modeText

        cameraMode = extensionModes.indexOf(modeText)

        mActivity.cancelFocusTimer()

        isQRMode = modeText == CameraModes.QR_SCAN

        isVideoMode = modeText == CameraModes.VIDEO

        if (isQRMode) {
            mActivity.qrOverlay.visibility = View.VISIBLE
            mActivity.thirdOption.visibility = View.INVISIBLE

            if (scanAllCodes) {
                mActivity.flipCamIcon.setImageResource(
                    R.drawable.cancel
                )
                mActivity.qrScanToggles.visibility = View.GONE
            } else {
                mActivity.flipCamIcon.setImageResource(
                    R.drawable.auto
                )
                mActivity.qrScanToggles.visibility = View.VISIBLE
            }

            mActivity.cancelButtonView.visibility = View.INVISIBLE
            mActivity.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

            mActivity.captureButton.setBackgroundResource(android.R.color.transparent)
            mActivity.captureButton.setImageResource(R.drawable.torch_off_button)

            mActivity.micOffIcon.visibility = View.GONE
        } else {
            mActivity.qrOverlay.visibility = View.INVISIBLE
            mActivity.thirdOption.visibility = View.VISIBLE
            mActivity.flipCamIcon.setImageResource(R.drawable.flip_camera)
            mActivity.cancelButtonView.visibility = View.VISIBLE
            mActivity.previewView.scaleType = PreviewView.ScaleType.FIT_START

            mActivity.qrScanToggles.visibility = View.GONE

            mActivity.captureButton.setBackgroundResource(R.drawable.cbutton_bg)

            if (isVideoMode) {
                mActivity.captureButton.setImageResource(R.drawable.recording)
            } else {
                mActivity.captureButton.setImageResource(R.drawable.camera_shutter)
                mActivity.micOffIcon.visibility = View.GONE
            }
        }

        mActivity.cbText.visibility = if (isVideoMode || mActivity.timerDuration == 0) {
             View.INVISIBLE
        } else  {
            View.VISIBLE
        }

        startCamera(true)
    }

    fun showMoreOptionsForQR() {
        val builder = AlertDialog.Builder(mActivity)
        builder.setTitle(mActivity.resources.getString(R.string.more_options))

        val optionNames = arrayListOf<String>()
        val optionValues = arrayListOf<Boolean>()

        for (format in BarcodeFormat.values()) {

            if (format in commonFormats) continue

            optionNames.add(format.name)

            val formatSRep = "${SettingValues.Key.SCAN}_$format"
            optionValues.add(
                commonPref.getBoolean(
                    formatSRep,
                    false
                )
            )
        }

        builder.setMultiChoiceItems(optionNames.toArray(arrayOf<String>()), optionValues.toBooleanArray()) { _, index, isChecked ->
            optionValues[index] = isChecked
        }

        // Add OK and Cancel buttons
        builder.setPositiveButton("OK") { _, _ ->

            val editor = commonPref.edit()

            for (index in 0 until optionNames.size) {

                val optionName = optionNames[index]
                val optionValue = optionValues[index]

                val formatSRep = "${SettingValues.Key.SCAN}_$optionName"

                val format = BarcodeFormat.valueOf(optionName)

                if (optionValue) {
                    allowedFormats.add(format)

                    editor.putBoolean(
                        formatSRep,
                        true
                    )
                } else if (format in allowedFormats) {
                    if (allowedFormats.size == 1) {
                        mActivity.showMessage(
                            "Please ensure that at least one barcode is " +
                                    "selected in manual mode"
                        )
                    } else {
                        allowedFormats.remove(format)

                        editor.putBoolean(
                            formatSRep,
                            false
                        )
                    }
                }
            }

            editor.commit()

            qrAnalyzer?.refreshHints()
        }

        builder.setNegativeButton("Cancel", null)

        // Create and show the alert dialog
        val dialog = builder.create()

        dialog.setOnShowListener {
            val button: Button = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL)
            button.setOnClickListener {

            }
        }

        dialog.show()
    }
}
