package app.grapheneos.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.Button
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.GroupableFeatures
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.internal.muxer.MediaMuxerImpl
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.portrait.PortraitFrontResolutionPreference
import app.grapheneos.camera.ui.FoldTabMode
import app.grapheneos.camera.ui.CameraShortcutMode
import app.grapheneos.camera.ui.ModeTabAction
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.MoreSettings
import app.grapheneos.camera.ui.activities.SecureActivity
import app.grapheneos.camera.ui.activities.SecureMainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import app.grapheneos.camera.ui.showIgnoringShortEdgeMode
import app.grapheneos.camera.util.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

// note that enum constant name is used as a name of a SharedPreferences instance
enum class CameraMode(val extensionMode: Int, val uiName: Int) {
    QR_SCAN(ExtensionMode.NONE, R.string.qr_scan_mode),
    AUTO(ExtensionMode.AUTO, R.string.auto_mode),
    FACE_RETOUCH(ExtensionMode.FACE_RETOUCH, R.string.face_retouch_mode),
    PORTRAIT(ExtensionMode.NONE, R.string.portrait_mode), // standalone offline Portrait AI
    NIGHT(ExtensionMode.NIGHT, R.string.night_mode),
    HDR(ExtensionMode.HDR, R.string.hdr_mode),
    CAMERA(ExtensionMode.NONE, R.string.camera),
    PRO(ExtensionMode.NONE, R.string.pro_mode),
    BURST(ExtensionMode.NONE, R.string.burst_mode),
    PANORAMA(ExtensionMode.NONE, R.string.panorama_mode),
    VIDEO(ExtensionMode.NONE, R.string.video),
    SLOW_MOTION(ExtensionMode.NONE, R.string.slow_motion_mode),
    HYPERLAPSE(ExtensionMode.NONE, R.string.hyperlapse_mode),
    TIMELAPSE_PHOTO(ExtensionMode.NONE, R.string.timelapse_photo_mode),
}

@SuppressLint("UnsafeOptInUsageError")
class CamConfig(private val mActivity: MainActivity) {

    enum class GridType {
        NONE,
        THREE_BY_THREE,
        FOUR_BY_FOUR,
        GOLDEN_RATIO
    }

    object SettingValues {

        object Key {
            const val SELF_ILLUMINATION = "self_illumination"
            const val GEO_TAGGING = "geo_tagging"
            const val FLASH_MODE = "flash_mode"
            const val GRID = "grid"
            const val REOPEN_LAST_USED_MODE = "reopen_last_used_mode"
            const val LAST_USED_MODE = "last_used_mode"

            // obsolete, split into WAIT_FOR_FOCUS_LOCK and PHOTO_QUALITY
            const val EMPHASIS_ON_QUALITY = "emphasis_on_quality"
            const val FOCUS_TIMEOUT = "focus_timeout"
            const val VIDEO_QUALITY = "video_quality"
            const val ASPECT_RATIO = "aspect_ratio"
            const val INCLUDE_AUDIO = "include_audio"
            const val ENABLE_EIS = "enable_eis"
            const val SCAN = "scan"
            const val SCAN_ALL_CODES = "scan_all_codes"
            const val SAVE_IMAGE_AS_PREVIEW = "save_image_as_preview"
            const val SAVE_VIDEO_AS_PREVIEW = "save_video_as_preview"

            const val STORAGE_LOCATION = "storage_location"
            const val PREVIOUS_SAF_TREES = "previous_saf_trees"

            const val LAST_CAPTURED_ITEM_TYPE = "last_captured_item_type"
            const val LAST_CAPTURED_ITEM_DATE_STRING = "last_captured_item_date_string"
            const val LAST_CAPTURED_ITEM_URI = "last_captured_item_uri"

            const val PHOTO_QUALITY = "photo_quality"

            const val REMOVE_EXIF_AFTER_CAPTURE = "remove_exif_after_capture"

            const val GYROSCOPE_SUGGESTIONS = "gyroscope_suggestions"

            const val CAMERA_SOUNDS = "camera_sounds"
            const val CAPTURE_HAPTICS = "capture_haptics"

            const val ENABLE_ZSL = "enable_zsl"

            const val SELECT_HIGHEST_RESOLUTION = "select_highest_resolution"

            const val WAIT_FOR_FOCUS_LOCK = "wait_for_focus_lock"

            const val SELF_TIMER_DURATION = "self_timer_duration"
        }

        object Default {

            val GRID_TYPE = GridType.NONE
            const val GRID_TYPE_INDEX = 0

            const val ASPECT_RATIO = AspectRatio.RATIO_4_3

            val VIDEO_QUALITY = Quality.HIGHEST

            const val SELF_ILLUMINATION = false

            const val GEO_TAGGING = false

            const val FLASH_MODE = ImageCapture.FLASH_MODE_OFF

            const val FOCUS_TIMEOUT = "5s"

            const val INCLUDE_AUDIO = true

            const val ENABLE_EIS = true

            const val SCAN_ALL_CODES = false

            const val SAVE_IMAGE_AS_PREVIEW = true

            const val SAVE_VIDEO_AS_PREVIEW = true

            const val STORAGE_LOCATION = ""

            const val PHOTO_QUALITY = 95

            const val REMOVE_EXIF_AFTER_CAPTURE = true

            const val GYROSCOPE_SUGGESTIONS = false

            const val CAMERA_SOUNDS = true

            const val ENABLE_ZSL = false

            const val SELECT_HIGHEST_RESOLUTION = false

            const val WAIT_FOR_FOCUS_LOCK = false

            const val SELF_TIMER_DURATION = 0
        }
    }

    companion object {
        private const val TAG = "CamConfig"

        private const val PREVIEW_SNAP_DURATION = 200L
        private const val PREVIEW_SL_OVERLAY_DUR = 200L

        const val DEFAULT_LENS_FACING = CameraSelector.LENS_FACING_BACK

        val commonFormats = arrayOf(
            BarcodeFormat.AZTEC,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.PDF_417,
        )

        val imageCollectionUri: Uri = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )!!

        val videoCollectionUri: Uri = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )!!

        val DEFAULT_CAMERA_MODE = CameraMode.CAMERA

        const val COMMON_SHARED_PREFS_NAME = "commons"

        val FRONT_CAMERA_SELECTOR = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        val REAR_CAMERA_SELECTOR = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Cache camera extension availability by lens and extension mode.
        private val extensionUsability = HashMap<Pair<Int, Int>, Boolean>()

        // Include every relevant capture setting in the cached snapshot probe key.
        private val snapshotDropReason = HashMap<SnapshotProbeKey, String?>()

        // The provider the verdicts above were probed through. A different instance means the
        // camera stack was reinitialized and none of them describe it any more.
        private var probedCameraProvider: ProcessCameraProvider? = null
    }

    private data class SnapshotProbeKey(
        val lensFacing: Int,
        val videoQuality: Quality,
        val usesFeatureGroup: Boolean,
        val captureMode: Int,
        val selectHighestResolution: Boolean,
    )

    var camera: Camera? = null

    // Cache zoom information; vendor extensions can make CameraInfo reads slow.
    var zoomState: ZoomState? = null
        private set

    private var zoomStateSource: LiveData<ZoomState>? = null

    private val zoomStateObserver = Observer<ZoomState> {
        zoomState = it
        if (it.linearZoom != 0f || it.zoomRatio != 1f) {
            mActivity.zoomBar.updateThumb()
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val attachZoomState = Runnable {
        if (mActivity.isDestroyed || mActivity.isFinishing) return@Runnable

        zoomStateSource = camera?.cameraInfo?.zoomState?.also {
            it.observe(mActivity, zoomStateObserver)
        }

        zoomState = zoomStateSource?.value
    }

    var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null

    var imageCapture: ImageCapture? = null
        private set

    var preview: Preview? = null

    val allowedFormats: ArrayList<BarcodeFormat> = arrayListOf()

    private val cameraExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    var videoCapture: VideoCapture<Recorder>? = null

    private var qrAnalyzer: QRAnalyzer? = null

    var iAnalyzer: ImageAnalysis? = null

    @set:VisibleForTesting
    var mPlayer = TunePlayer(mActivity)

    // note that Activities which implement SecureActivity interface (meaning they are accessible
    // from the lock screen) are forced to override getSharedPreferences()
    // and return an instance of in-memory EphemeralSharedPrefs, which are based on "real" prefs,
    // but never modify them
    val commonPref: SharedPreferences =
        mActivity.getSharedPreferences(COMMON_SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var modePref: SharedPreferences

    var lastCapturedItem: CapturedItem? = null

    init {
        if (mActivity !is SecureActivity) {
            CapturedItems.init(mActivity, this)
            fetchLastCapturedItemFromSharedPrefs()
        }
    }

    fun fetchLastCapturedItemFromSharedPrefs() {
        val type = commonPref.getInt(SettingValues.Key.LAST_CAPTURED_ITEM_TYPE, -1)
        val dateStr = commonPref.getString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, null)
        val uri = commonPref.getString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, null)

        var item: CapturedItem? = null
        if (dateStr != null && uri != null) {
            val skip = type == ITEM_TYPE_IMAGE && mActivity is VideoOnlyActivity
            if (!skip) {
                item = CapturedItem(type, dateStr, Uri.parse(uri))
            }
        }
        lastCapturedItem = item
    }


    var isVideoMode = false
        private set
        get() {
            return field ||
                    mActivity is VideoCaptureActivity ||
                    mActivity is VideoOnlyActivity
        }

    val canTakePicture: Boolean
        get() {
            return imageCapture != null
        }

    var isQRMode = false
        private set

    val isFlashAvailable: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() ?: false

    var isTorchOn: Boolean = false
        get() {
            return camera?.cameraInfo?.torchState?.value == TorchState.ON
        }
        set(value) {
            field = if (isFlashAvailable) {
                camera?.cameraControl?.enableTorch(value)
                value
            } else {
                false
            }
        }

    var currentMode: CameraMode = DEFAULT_CAMERA_MODE
        private set

    /** Independent of the existing per-mode camera setting persistence. */
    /** Independent of ringer mode and of the existing shutter sound setting. */
    var captureHaptics: Boolean
        get() = commonPref.getBoolean(SettingValues.Key.CAPTURE_HAPTICS, true)
        set(enabled) {
            commonPref.edit().putBoolean(SettingValues.Key.CAPTURE_HAPTICS, enabled).apply()
        }

    var reopenLastUsedMode: Boolean
        get() = commonPref.getBoolean(SettingValues.Key.REOPEN_LAST_USED_MODE, false)
        set(enabled) {
            commonPref.edit().putBoolean(SettingValues.Key.REOPEN_LAST_USED_MODE, enabled)
                .apply()
            if (enabled && mActivity.javaClass == MainActivity::class.java) {
                saveCurrentModeForNextLaunch()
            }
        }

    private fun saveCurrentModeForNextLaunch() {
        commonPref.edit().putString(SettingValues.Key.LAST_USED_MODE, currentMode.name).apply()
    }

    /** Only called once for a normal cold launch, never for secure or external capture. */
    fun restoreLastUsedMode() {
        if (mActivity.javaClass != MainActivity::class.java || !reopenLastUsedMode) return
        val modeName = commonPref.getString(SettingValues.Key.LAST_USED_MODE, null) ?: return
        val mode = CameraMode.entries.firstOrNull { it.name == modeName } ?: return
        if (mode != DEFAULT_CAMERA_MODE) switchMode(mode)
    }

    var aspectRatio: Int
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

    private var cameraSelector: CameraSelector = CameraSelector.Builder()
        .requireLensFacing(DEFAULT_LENS_FACING)
        .build()

    var gridType: GridType = SettingValues.Default.GRID_TYPE
        set(value) {
            val editor = commonPref.edit()
            editor.putInt(SettingValues.Key.GRID, GridType.values().indexOf(value))
            editor.apply()

            field = value
        }

    var videoQuality: Quality = SettingValues.Default.VIDEO_QUALITY
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

            modePref.edit {
                putString(videoQualityKey, option)
            }

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
                modePref.edit {
                    putInt(SettingValues.Key.FLASH_MODE, flashMode)
                }
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
            return commonPref.getBoolean(
                SettingValues.Key.CAMERA_SOUNDS,
                SettingValues.Default.CAMERA_SOUNDS
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.CAMERA_SOUNDS, value)
            editor.apply()
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
                    mActivity.setFlipCameraIcon(
                        R.drawable.cancel, R.string.stop_scanning_all_formats
                    )
                    mActivity.qrScanToggles.visibility = View.GONE
                } else {
                    mActivity.setFlipCameraIcon(
                        R.drawable.auto, R.string.scan_all_formats
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

    var enableEIS: Boolean
        get() {
            return mActivity.settingsDialog.enableEISToggle.isChecked
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.ENABLE_EIS, value)
            editor.apply()

            mActivity.settingsDialog.enableEISToggle.isChecked = value
        }

    var enableZsl: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.ENABLE_ZSL,
                SettingValues.Default.ENABLE_ZSL
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.ENABLE_ZSL, value)
            editor.apply()
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

    var saveVideoAsPreviewed: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.SAVE_VIDEO_AS_PREVIEW,
                SettingValues.Default.SAVE_VIDEO_AS_PREVIEW
            )
        }
        set(value) {
            val editor = commonPref.edit()
            editor.putBoolean(SettingValues.Key.SAVE_VIDEO_AS_PREVIEW, value)
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
            val cur = storageLocation
            if (cur != SettingValues.Default.STORAGE_LOCATION) {
                CapturedItems.savePreviousSafTree(Uri.parse(cur), commonPref)
            }

            val editor = commonPref.edit()
            editor.putString(SettingValues.Key.STORAGE_LOCATION, value)
            editor.apply()

            // Strictly after the write: the tree being picked only becomes tracked once it is the
            // stored location, and re-picking a tree that savePreviousSafTree() just pushed off the
            // tail of the tracked list would otherwise have its grant revoked out from under it.
            CapturedItems.releaseUntrackedSafTrees(mActivity, commonPref)
        }

    var photoQuality: Int
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

    var removeExifAfterCapture: Boolean
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

    var gSuggestions: Boolean
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

    val isZslSupported: Boolean by lazy {
        camera!!.cameraInfo.isZslSupported
    }

    // Whether the EIS toggle has anything to act on. Stabilization is only ever requested through
    // the feature group (see startCamera), which in turn needs the platform to be able to verify
    // feature combinations, so a camera that can stabilize is not on its own enough: where the
    // group cannot be used nothing applies EIS, and offering the toggle there is offering a
    // control that does nothing.
    fun canApplyVideoStabilization(): Boolean {
        return canVerifyFeatureCombinations() && isVideoStabilizationSupported()
    }

    private fun isVideoStabilizationSupported(): Boolean {
        // The setting is available when either preview or video stabilization is supported.
        return isPreviewStabilizationSupported() || isRecorderStabilizationSupported()
    }

    private fun isPreviewStabilizationSupported(): Boolean {
        return Preview.getPreviewCapabilities(getCurrentCameraInfo()).isStabilizationSupported
    }


    private fun isRecorderStabilizationSupported(): Boolean {
        return Recorder.getVideoCapabilities(getCurrentCameraInfo()).isStabilizationSupported
    }

    fun shouldShowGyroscope(): Boolean {
        return isInPhotoMode && gSuggestions
    }

    private val isInPhotoMode: Boolean
        get() {
            return !(isQRMode || isVideoMode)
        }

    val isInCaptureMode: Boolean
        get() {
            return mActivity is CaptureActivity
        }

    private fun saveLastCapturedItem(item: CapturedItem, editor: SharedPreferences.Editor) {
        editor.putInt(SettingValues.Key.LAST_CAPTURED_ITEM_TYPE, item.type)
        editor.putString(SettingValues.Key.LAST_CAPTURED_ITEM_DATE_STRING, item.dateString)
        editor.putString(SettingValues.Key.LAST_CAPTURED_ITEM_URI, item.uri.toString())
    }

    fun updateLastCapturedItem(item: CapturedItem) {
        commonPref.edit {
            saveLastCapturedItem(item, this)
        }

        if (mActivity is SecureMainActivity) {
            // previous call updated ephemeral SharedPreferences that won't be accessible by the
            // "regular" MainActivity
            mActivity.applicationContext.getSharedPreferences(
                COMMON_SHARED_PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit {
                saveLastCapturedItem(item, this)
            }
        }

        lastCapturedItem = item
    }

    var requireLocation: Boolean = false
        get() {
            return mActivity.settingsDialog.locToggle.isChecked
        }
        set(value) {
            mActivity.locationCamConfigChanged(value)

            // A permission result is delivered before the first onResume of an activity the system
            // recreated, so this can run before startCamera() has picked the prefs for a mode
            if (::modePref.isInitialized) {
                modePref.edit {
                    putBoolean(SettingValues.Key.GEO_TAGGING, value)
                }
            }

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
            modePref.edit {
                putBoolean(SettingValues.Key.SELF_ILLUMINATION, value)
            }

            mActivity.settingsDialog.selfIlluminationToggle.isChecked = value
            mActivity.settingsDialog.selfIllumination()
        }

    private fun getString(@StringRes id: Int) = mActivity.getString(id)

    fun setQRScanningFor(format: String, selected: Boolean) {

        val formatSRep = "${SettingValues.Key.SCAN}_$format"

        commonPref.edit {
            putBoolean(formatSRep, selected)
        }

        if (selected) {
            if (BarcodeFormat.valueOf(format) !in allowedFormats) {
                allowedFormats.add(BarcodeFormat.valueOf(format))
            }
        } else {
            if (allowedFormats.size == 1) {
                mActivity.showMessage(
                    getString(R.string.no_barcode_selected)
                )
            } else {
                allowedFormats.remove(BarcodeFormat.valueOf(format))
            }
        }

        qrAnalyzer?.refreshHints()
    }

    fun reloadSettings() {
        // pref config needs to be created
        modePref.edit {
            if (!modePref.contains(SettingValues.Key.FLASH_MODE)) {
                putInt(SettingValues.Key.FLASH_MODE, SettingValues.Default.FLASH_MODE)
            }

            if (!modePref.contains(SettingValues.Key.GEO_TAGGING)) {
                putBoolean(SettingValues.Key.GEO_TAGGING, SettingValues.Default.GEO_TAGGING)
            }

            if (isVideoMode) {
                mActivity.settingsDialog.reloadQualities()
            }

            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                if (!modePref.contains(SettingValues.Key.SELF_ILLUMINATION)) {
                    putBoolean(
                        SettingValues.Key.SELF_ILLUMINATION,
                        SettingValues.Default.SELF_ILLUMINATION
                    )
                }
            }
        }

        flashMode = modePref.getInt(
            SettingValues.Key.FLASH_MODE,
            SettingValues.Default.FLASH_MODE
        )

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        requireLocation = modePref.getBoolean(
            SettingValues.Key.GEO_TAGGING,
            SettingValues.Default.GEO_TAGGING
        ) && !(mActivity.applicationContext as App).shouldAskForLocationPermission()

        selfIlluminate = modePref.getBoolean(
            SettingValues.Key.SELF_ILLUMINATION,
            SettingValues.Default.SELF_ILLUMINATION
        )

        mActivity.settingsDialog.showOnlyRelevantSettings()
    }

    fun loadSettings() {

        // Create common config. if it's not created
        val editor = commonPref.edit()

        if (!commonPref.contains(SettingValues.Key.CAMERA_SOUNDS)) {
            editor.putBoolean(SettingValues.Key.CAMERA_SOUNDS, SettingValues.Default.CAMERA_SOUNDS)
        }

        // Preserve video-preview settings on installations created before this option existed.
        if (commonPref.contains(SettingValues.Key.SAVE_IMAGE_AS_PREVIEW)) {
            if (!commonPref.contains(SettingValues.Key.SAVE_VIDEO_AS_PREVIEW)) {
                editor.putBoolean(
                    SettingValues.Key.SAVE_VIDEO_AS_PREVIEW,
                    false
                )
            }
        } else {
            editor.putBoolean(
                SettingValues.Key.SAVE_IMAGE_AS_PREVIEW,
                SettingValues.Default.SAVE_IMAGE_AS_PREVIEW
            )

            editor.putBoolean(
                SettingValues.Key.SAVE_VIDEO_AS_PREVIEW,
                SettingValues.Default.SAVE_VIDEO_AS_PREVIEW
            )
        }

        if (!commonPref.contains(SettingValues.Key.GRID)) {
            // Index for Grid.values() Default: NONE
            editor.putInt(SettingValues.Key.GRID, SettingValues.Default.GRID_TYPE_INDEX)
        }

        if (!commonPref.contains(SettingValues.Key.FOCUS_TIMEOUT)) {
            editor.putString(SettingValues.Key.FOCUS_TIMEOUT, SettingValues.Default.FOCUS_TIMEOUT)
        }

        migrateFromLegacyPhotoQuality()

        if (!commonPref.contains(SettingValues.Key.INCLUDE_AUDIO)) {
            editor.putBoolean(
                SettingValues.Key.INCLUDE_AUDIO,
                SettingValues.Default.INCLUDE_AUDIO
            )
        }

        if (!commonPref.contains(SettingValues.Key.ENABLE_EIS)) {
            editor.putBoolean(
                SettingValues.Key.ENABLE_EIS,
                SettingValues.Default.ENABLE_EIS
            )
        }

        if (!commonPref.contains(SettingValues.Key.ASPECT_RATIO)) {
            editor.putInt(
                SettingValues.Key.ASPECT_RATIO,
                SettingValues.Default.ASPECT_RATIO
            )
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


        editor.apply()

        gridType = GridType.values()[commonPref.getInt(
            SettingValues.Key.GRID,
            SettingValues.Default.GRID_TYPE_INDEX
        )]

        mActivity.settingsDialog.updateGridToggleUI()

        commonPref.getString(SettingValues.Key.FOCUS_TIMEOUT, SettingValues.Default.FOCUS_TIMEOUT)
            ?.let {
                mActivity.settingsDialog.updateFocusTimeout(it)
            }

        aspectRatio = commonPref.getInt(
            SettingValues.Key.ASPECT_RATIO,
            SettingValues.Default.ASPECT_RATIO
        )

        includeAudio = commonPref.getBoolean(
            SettingValues.Key.INCLUDE_AUDIO,
            SettingValues.Default.INCLUDE_AUDIO
        )

        enableEIS = commonPref.getBoolean(
            SettingValues.Key.ENABLE_EIS,
            SettingValues.Default.ENABLE_EIS
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

                if (format == BarcodeFormat.QR_CODE) {
                    mActivity.qrToggle.isSelected = true
                }

                if (format == BarcodeFormat.AZTEC) {
                    mActivity.azToggle.isSelected = true
                }

                if (format == BarcodeFormat.PDF_417) {
                    mActivity.cBToggle.isSelected = true
                }

                if (format == BarcodeFormat.DATA_MATRIX) {
                    mActivity.dmToggle.isSelected = true
                }
            }
        }

        qrAnalyzer?.refreshHints()
    }

    var waitForFocusLock: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.WAIT_FOR_FOCUS_LOCK,
                SettingValues.Default.WAIT_FOR_FOCUS_LOCK
            )
        }
        set(value) {
            commonPref.edit {
                putBoolean(SettingValues.Key.WAIT_FOR_FOCUS_LOCK, value)
            }
        }

    var selectHighestResolution: Boolean
        get() {
            return commonPref.getBoolean(
                SettingValues.Key.SELECT_HIGHEST_RESOLUTION,
                SettingValues.Default.SELECT_HIGHEST_RESOLUTION
            )
        }
        set(value) {
            commonPref.edit {
                putBoolean(SettingValues.Key.SELECT_HIGHEST_RESOLUTION, value)
            }
        }

    fun migrateFromLegacyPhotoQuality() {
        // If emphasis on quality/optimization was previously set by the user
        if (commonPref.contains(SettingValues.Key.EMPHASIS_ON_QUALITY)) {
            // If the photo quality key has not previously been set
            if (!commonPref.contains(SettingValues.Key.PHOTO_QUALITY)) {
                val optimizeForQuality =
                    commonPref.getBoolean(SettingValues.Key.EMPHASIS_ON_QUALITY, false)

                photoQuality = if (optimizeForQuality) {
                    100
                } else {
                    95
                }
            }

            // Remove the key to avoid re-execution of the above code
            commonPref.edit {
                remove(SettingValues.Key.EMPHASIS_ON_QUALITY)
            }
        }

        if (photoQuality == 0) {
            photoQuality = 95;
        }
    }


    fun toggleTorchState() {
        isTorchOn = !isTorchOn
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
                getString(R.string.flash_unavailable_in_selected_mode)
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

    private fun getCurrentCameraInfo(): CameraInfo {
        return cameraProvider!!.getCameraInfo(cameraSelector)
    }

    fun toggleCameraSelector() {

        // A failed resolution negotiation on one camera must not disable max
        // resolution when returning to another front camera.
        portraitFrontTargetPreferenceEnabled = true

        // Manually switch to the opposite lens facing
        lensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK


        // Test whether the new lens facing is supported by the current device
        // If it is supported then restart the camera with the new configuration
        if (isLensFacingSupported(lensFacing)) {
            startCamera(true)
        } else {
            // Else revert back to the old facing (while displaying an error message
            // to the user)
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                mActivity.showMessage(getString(R.string.rear_camera_unavailable))
                CameraSelector.LENS_FACING_FRONT
            } else {
                mActivity.showMessage(getString(R.string.front_camera_unavailable))
                CameraSelector.LENS_FACING_BACK
            }
        }

    }

    fun initializeCamera(forced: Boolean = false) {
        if (cameraProvider != null) {
            startCamera(forced = forced)
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(mActivity)

        cameraProviderFuture.addListener(fun() {
            val provider: ProcessCameraProvider
            try {
                provider = cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                mActivity.showMessage(mActivity.getString(R.string.camera_provider_init_failure))
                return
            }

            if (provider !== probedCameraProvider) {
                // A different provider instance means the camera stack was reinitialized:
                // extension verdicts probed through the previous instance (including bind-time
                // blacklists, see startCamera) describe vendor state that no longer exists.
                extensionUsability.clear()
                snapshotDropReason.clear()
                probedCameraProvider = provider
            }
            cameraProvider = provider

            // Manually switch to the other lens facing (if the default lens facing isn't
            // supported for the current device)
            if (!isLensFacingSupported(lensFacing)) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }

            // Initialize camera extensions off the UI thread; the vendor probe can block.
            thread {
                try {
                    val extensionsManagerFuture =
                        ExtensionsManager.getInstanceAsync(mActivity, provider)

                    extensionsManagerFuture.addListener({
                        try {
                            extensionsManager = extensionsManagerFuture.get()
                        } catch (e: ExecutionException) {
                            mActivity.showMessage(mActivity.getString(R.string.extensions_manager_init_failure))
                        }
                        startCamera(forced = forced)
                    }, ContextCompat.getMainExecutor(mActivity))
                } catch (e: Exception) {
                    // If extension setup fails, start the camera without extensions on the main thread.
                    Log.e(TAG, "Extensions manager initialization failed", e)
                    ContextCompat.getMainExecutor(mActivity).execute {
                        mActivity.showMessage(mActivity.getString(R.string.extensions_manager_init_failure))
                        startCamera(forced = forced)
                    }
                }
            }

        }, ContextCompat.getMainExecutor(mActivity))
    }

    // Probe vendor extensions before offering a mode. Cache confirmed results, but retry transient errors.
    // Updates to the cache return to the main thread.
    private fun probeExtension(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        extensionMode: Int,
    ): Boolean? {
        // Vendor extension checks use binder; treat query failures as retryable.
        return try {
            when {
                !em.isExtensionAvailable(selector, extensionMode) -> false
                else -> {
                    provider.getCameraInfo(
                        em.getExtensionEnabledCameraSelector(selector, extensionMode)
                    )
                    true
                }
            }
        } catch (e: UnsupportedOperationException) {
            // The signature of a vendor extender that advertises the mode and then throws from
            // its own init (Pixels: "Framework size list map not supported in pixel path").
            // Nothing about it changes within a process lifetime, so this verdict is safe to
            // remember.
            Log.w(TAG, "Extension mode $extensionMode is advertised but unusable here", e)
            false
        } catch (e: Exception) {
            // Anything else may be transient — the camera service restarting, the camera briefly
            // held by another process. Fail this probe but leave the cache alone so the mode is
            // offered again once the underlying condition clears.
            Log.w(TAG, "Probing extension mode $extensionMode failed, will retry later", e)
            null
        }
    }

    private fun isExtensionUsable(
        selector: CameraSelector,
        lensFacing: Int,
        extensionMode: Int,
        probeOnMiss: Boolean = true,
    ): Boolean {
        if (extensionMode == ExtensionMode.NONE) return true

        val em = extensionsManager ?: return false
        val provider = cameraProvider ?: return false

        val key = lensFacing to extensionMode
        extensionUsability[key]?.let { return it }

        if (!probeOnMiss) return false

        // Do not duplicate a vendor probe while the background tab refresh is running.
        if (extensionProbesInFlight) return false

        val verdict = probeExtension(provider, em, selector, extensionMode) ?: return false
        extensionUsability[key] = verdict
        return verdict
    }

    // Use feature groups only when the API and camera HAL can verify combinations.
    // Otherwise keep the selected video quality through the regular CameraX setters.
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun canVerifyFeatureCombinations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false

        val cameraInfo = try {
            cameraProvider?.getCameraInfo(cameraSelector) ?: return false
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to resolve camera info for feature combination gate", exception)
            return false
        }

        val queryVersion = try {
            Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
            )
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Camera info carries no camera2 characteristics", exception)
            null
        }

        return (queryVersion ?: 0) >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    // Maps the user-chosen video quality to the equivalent groupable feature, for use when a
    // feature group is passed to SessionConfig (see startCamera). Quality.HIGHEST has no
    // groupable equivalent and is resolved to the highest quality the current camera supports,
    // mirroring what QualitySelector.from(Quality.HIGHEST) would have selected.
    private fun videoQualityAsGroupableFeature(): GroupableFeature? {
        val quality = if (videoQuality == Quality.HIGHEST) {
            val cameraInfo = try {
                cameraProvider?.getCameraInfo(cameraSelector) ?: return null
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Unable to resolve camera info for quality lookup", exception)
                return null
            }
            Recorder.getVideoCapabilities(cameraInfo)
                .getSupportedQualities(DynamicRange.SDR)
                .firstOrNull() ?: return null
        } else {
            videoQuality
        }

        return when (quality) {
            Quality.UHD -> GroupableFeatures.UHD_RECORDING
            Quality.FHD -> GroupableFeatures.FHD_RECORDING
            Quality.HD -> GroupableFeatures.HD_RECORDING
            Quality.SD -> GroupableFeatures.SD_RECORDING
            else -> {
                // Not fatal: startCamera() then requests no quality feature at all and the
                // quality is left to Recorder's default selector. Worth a log because it means
                // the user's explicit choice is silently not being asked for.
                Log.w(TAG, "No groupable feature equivalent for video quality $quality")
                null
            }
        }
    }

    // Use the same quality labels as the settings screen.
    private fun describeQualityFeature(feature: GroupableFeature): String? = when (feature) {
        GroupableFeatures.UHD_RECORDING -> "2160p (UHD)"
        GroupableFeatures.FHD_RECORDING -> "1080p (FHD)"
        GroupableFeatures.HD_RECORDING -> "720p (HD)"
        GroupableFeatures.SD_RECORDING -> "480p (SD)"
        else -> null
    }

    // Show a feature warning only when the verdict changes for a camera.
    private val lastReportedDivergence = HashMap<Int, String>()

    // Report unsupported user-selected options after CameraX resolves the feature group.
    // Use the requested camera and settings, since this callback is asynchronous.
    private fun onFeaturesSelected(
        boundLensFacing: Int,
        requested: List<GroupableFeature>,
        qualityFeature: GroupableFeature?,
        selected: Set<GroupableFeature>,
    ) {
        // Log the resolved features. Only a video quality downgrade triggers a warning;
        // stabilization may be dropped when CameraX prioritizes the selected quality.
        Log.i(TAG, "Requested $requested but got $selected")

        val qualityLabel = qualityFeature?.let { describeQualityFeature(it) }
        // Only warn about a rejected quality with a known user-facing label.
        val droppedQuality = qualityLabel?.takeIf { qualityFeature !in selected }

        // Only notify the user when the requested video quality cannot be selected.
        val message = droppedQuality?.let {
            mActivity.getString(R.string.quality_unsupported, it)
        }

        if (message == null) {
            lastReportedDivergence.remove(boundLensFacing)
            return
        }

        if (message != lastReportedDivergence[boundLensFacing]) {
            lastReportedDivergence[boundLensFacing] = message
            mActivity.showMessage(message)
        }
    }

    private fun isLensFacingSupported(lensFacing: Int): Boolean {
        var tCameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        if (currentMode.extensionMode != ExtensionMode.NONE) {
            extensionsManager?.let { em ->
                if (!isExtensionUsable(tCameraSelector, lensFacing, currentMode.extensionMode))
                    return false

                try {
                    tCameraSelector = em.getExtensionEnabledCameraSelector(
                        tCameraSelector,
                        currentMode.extensionMode
                    )
                } catch (e: IllegalArgumentException) {
                    return false
                }
            }
        }

        return cameraProvider?.hasCamera(tCameraSelector) ?: false
    }

    // Inspect the JPEG streams of the *selected* front camera. Sensor marketing megapixels
    // are not guaranteed to be exposed as Camera2/CameraX JPEG sizes (especially on foldables).
    // Log both the advertised choices and the eventual CameraX binding for device tests.
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun preferredPortraitFrontJpegSize(): Size? {
        val cameraInfo = try {
            cameraProvider?.getCameraInfo(cameraSelector)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "PortraitFrontRes: selected camera info unavailable", exception)
            null
        } ?: return null

        return try {
            val c2 = Camera2CameraInfo.from(cameraInfo)
            val available = c2.getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )?.getOutputSizes(ImageFormat.JPEG).orEmpty()
            val selected = PortraitFrontResolutionPreference.chooseNativeJpegSize(
                available.map { it.width to it.height }
            )
            val listing = available.sortedByDescending { it.width.toLong() * it.height }
                .joinToString { "${it.width}x${it.height}" }
            Log.i(TAG, "PortraitFrontRes cameraId=${c2.cameraId} nativeJPEG=[$listing] " +
                "aspect=unrestricted(max native JPEG pixels) " +
                "requested=${selected?.let { "${it.first}x${it.second}" } ?: "CameraX highest"}")
            selected?.let { Size(it.first, it.second) }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "PortraitFrontRes: cannot inspect native JPEG stream sizes", exception)
            null
        }
    }

    private var portraitFrontTargetPreferenceEnabled = true
    private var portraitFrontExplicitTargetActive = false

    // Start the camera with latest hard configuration
    @SuppressLint("RestrictedApi")
    fun startCamera(forced: Boolean = false) {
        // Concurrent cameras own the EXISTING preview while Dual Photo/Video is selected.
        if (mActivity.isIntegratedDualActive()) return
        if ((!forced && camera != null) || cameraProvider == null) return

        // Cancel any pending capture requests
        mActivity.imageCapturer.cancelPendingCaptureRequest()

        mActivity.exposureBar.hidePanel()
        modePref = mActivity.getSharedPreferences(currentMode.name, Context.MODE_PRIVATE)

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

        // Test whether the current lens facing is supported by the current device
        // If not then silently switch to the other lens facing
        // (Snackbar/popup message can be shown before startCamera is called
        // in specific cases of explicitly switching to another side or if
        // the camera is expected)
        if (!isLensFacingSupported(lensFacing)) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        }


        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        portraitFrontExplicitTargetActive = false
        val builder = ImageCapture.Builder()
        if (currentMode == CameraMode.SLOW_MOTION) {
            imageCapture = null
        }

        // To use the last frame instead of showing a blank screen when
        // the camera that is being currently used gets unbind
        mActivity.updateLastFrame()

        // Unbind/close all other camera(s) [if any]
        cameraProvider?.unbindAll()

        val extMode = currentMode.extensionMode
        var appliedExtension: Pair<Int, Int>? = null
        if (extMode != ExtensionMode.NONE) {
            val em = extensionsManager
            if (em != null && isExtensionUsable(cameraSelector, lensFacing, extMode)) {
                appliedExtension = lensFacing to extMode
                cameraSelector = em.getExtensionEnabledCameraSelector(cameraSelector, extMode)
            } else {
                Log.e(TAG, "Mode $currentMode isn't available for this device")
            }
        }

        val useCasesList = arrayListOf<UseCase>()

        val aspectRatioStrategy = AspectRatioStrategy(
            aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO
        )

        // CameraX rejects regular quality/stabilization setters when a feature group is active.
        // Select the quality through that group, or use the standard setters without a group.
        val usesFeatureGroup = isVideoMode && currentMode != CameraMode.SLOW_MOTION &&
            enableEIS && canApplyVideoStabilization()
        val videoQualityFeature: GroupableFeature? = when {
            usesFeatureGroup -> videoQualityAsGroupableFeature()
            else -> null
        }

        val captureMode = when {
            waitForFocusLock -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            enableZsl -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }

        if (isQRMode) {
            val analyzer = QRAnalyzer(mActivity)
            val strategy = ResolutionStrategy(
                Size(960, 960),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
            val mIAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(strategy).build()
                )
                .setOutputImageRotationEnabled(true)
                .build()
            qrAnalyzer = analyzer
            mActivity.startFocusTimer()
            iAnalyzer = mIAnalyzer
            mIAnalyzer.setAnalyzer(cameraExecutor, analyzer)
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(
                    if (isLensFacingSupported(CameraSelector.LENS_FACING_BACK)) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        mActivity.showMessage(R.string.qr_rear_camera_unavailable)
                        CameraSelector.LENS_FACING_FRONT
                    }
                )
                .build()
            useCasesList.add(mIAnalyzer)

        } else {
            if (isVideoMode) {

                mActivity.micOffIcon.visibility = when {
                    currentMode == CameraMode.SLOW_MOTION && includeAudio -> View.GONE
                    currentMode == CameraMode.SLOW_MOTION -> View.VISIBLE
                    includeAudio -> View.GONE
                    else -> View.VISIBLE
                }

                val recorderBuilder = Recorder.Builder()

                // Use the platform muxer to avoid 4K audio queue stalls on Tensor devices.
                recorderBuilder.setMuxerFactory { MediaMuxerImpl() }

                if (currentMode == CameraMode.SLOW_MOTION) {
                    val info = try {
                        cameraProvider!!.getCameraInfo(cameraSelector)
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Unable to query high-speed camera info", error)
                        mActivity.showMessage(R.string.slow_motion_unavailable)
                        switchMode(CameraMode.CAMERA)
                        return
                    }
                    val qualities = try {
                        Recorder.getHighSpeedVideoCapabilities(info)
                            ?.getSupportedQualities(DynamicRange.SDR).orEmpty()
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Unable to query high-speed video profiles", error)
                        emptyList()
                    }
                    if (qualities.isEmpty()) {
                        mActivity.showMessage(R.string.slow_motion_unavailable)
                        switchMode(CameraMode.CAMERA)
                        return
                    }
                    val requestedFps = commonPref.getInt("slow_motion_preferred_fps", 120)
                    val fpsProfiles = supportedSlowMotionProfiles(info)
                    val preferredQuality = fpsProfiles[requestedFps]
                        ?: fpsProfiles[120]
                    val quality = preferredQuality ?: when {
                        Quality.FHD in qualities -> Quality.FHD
                        Quality.HD in qualities -> Quality.HD
                        else -> qualities.first()
                    }
                    recorderBuilder.setQualitySelector(QualitySelector.from(quality))
                } else if (!usesFeatureGroup) {
                    recorderBuilder.setQualitySelector(QualitySelector.from(videoQuality))
                }

                val videoCaptureBuilder = VideoCapture.Builder(recorderBuilder.build())

                // Keep the selected recording quality when EIS cannot be negotiated safely.

                if (currentMode != CameraMode.SLOW_MOTION &&
                    mActivity.camConfig.saveVideoAsPreviewed) {
                    videoCaptureBuilder.setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                }

                videoCapture = videoCaptureBuilder.build()

                useCasesList.add(videoCapture!!)
            }

            if (!mActivity.requiresVideoModeOnly && currentMode != CameraMode.SLOW_MOTION) {
                imageCapture = builder.let {
                    it.setCaptureMode(
                        if (currentMode == CameraMode.BURST || currentMode == CameraMode.PANORAMA ||
                            currentMode == CameraMode.HYPERLAPSE || currentMode == CameraMode.TIMELAPSE_PHOTO ||
                            currentMode == CameraMode.PRO)
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        else captureMode
                    )


                    it.setTargetRotation(
                        imageCapture?.targetRotation
                            ?: rotation
                    )

                    val selfiePortrait = currentMode == CameraMode.PORTRAIT &&
                        lensFacing == CameraSelector.LENS_FACING_FRONT
                    // Never apply the ordinary 4:3 or 16:9 capture *preference* to Portrait
                    // selfies: CameraX ranks aspect ratio ahead of requested size and would
                    // reject higher-pixel JPEG sizes with a native non-standard aspect (e.g.
                    // Pixel Fold's 3840x2600 vs 3264x2448). Keep the Preview preference intact.
                    val resolutionSelectorBuilder = ResolutionSelector.Builder()
                    if (currentMode != CameraMode.PANORAMA || !selectHighestResolution) {
                        // Panorama now follows the SAME regular aspect-ratio preference
                        // when "Use highest photo resolution" is disabled.
                        if (!selfiePortrait || !portraitFrontTargetPreferenceEnabled)
                            resolutionSelectorBuilder.setAspectRatioStrategy(aspectRatioStrategy)
                    }

                    if (currentMode == CameraMode.PANORAMA && selectHighestResolution) {
                        // Highest camera-supported JPEG pixels, not a Pixel-model table.
                        // The live ImageAnalysis stream remains independent and low-res.
                        resolutionSelectorBuilder.setAllowedResolutionMode(
                            ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                        resolutionSelectorBuilder.setResolutionFilter { sizes, _ ->
                            sizes.sortedWith(compareByDescending<Size> {
                                it.width.toLong() * it.height.toLong()
                            }.thenByDescending { maxOf(it.width, it.height) })
                        }
                        resolutionSelectorBuilder.setResolutionStrategy(
                            ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    }
                    if (selfiePortrait) {
                        resolutionSelectorBuilder.setAllowedResolutionMode(
                            ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                        )
                        if (portraitFrontTargetPreferenceEnabled) {
                            // Track this even if Camera2 can't expose its JPEG list, so that
                            // an unsupported CameraX highest-resolution bind also retries.
                            portraitFrontExplicitTargetActive = true
                            // CameraX's final ResolutionFilter runs after its aspect-ratio
                            // ranking. Sort the *available*, still-bindable sizes globally by
                            // original pixel count, without inventing an unsupported size.
                            resolutionSelectorBuilder.setResolutionFilter { sizes, _ ->
                                sizes.sortedWith(compareByDescending<Size> {
                                    it.width.toLong() * it.height.toLong()
                                }.thenByDescending { maxOf(it.width, it.height) })
                            }
                            resolutionSelectorBuilder.setResolutionStrategy(
                                ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                            )
                            preferredPortraitFrontJpegSize()?.let { nativeSize ->
                                resolutionSelectorBuilder.setResolutionStrategy(
                                    ResolutionStrategy(nativeSize,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                                )
                            }
                        }
                    } else if (selectHighestResolution && currentMode != CameraMode.BURST &&
                        currentMode != CameraMode.PANORAMA && currentMode != CameraMode.HYPERLAPSE &&
                        currentMode != CameraMode.TIMELAPSE_PHOTO) {
                        resolutionSelectorBuilder.setAllowedResolutionMode(
                            ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                        )
                    }

                    it.setResolutionSelector(resolutionSelectorBuilder.build())

                    it.setFlashMode(if (currentMode == CameraMode.PANORAMA || currentMode == CameraMode.HYPERLAPSE ||
                        currentMode == CameraMode.TIMELAPSE_PHOTO || currentMode == CameraMode.PRO)
                        ImageCapture.FLASH_MODE_OFF else flashMode)

                    // Panorama JPEG keyframes follow the global "Image Quality" setting.
                    it.setJpegQuality(if (currentMode == CameraMode.PANORAMA)
                        photoQuality.coerceIn(1, 100) else photoQuality)

                    it.build()
                }

                useCasesList.add(imageCapture!!)
            }
        }

        val previewBuilder = Preview.Builder().setTargetRotation(
            preview?.targetRotation ?: rotation
        )
        if (currentMode != CameraMode.SLOW_MOTION) {
            previewBuilder.setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatioStrategy).build()
            )
        }

        // Disable default preview stabilization outside feature groups to preserve framing.
        when {
            usesFeatureGroup || currentMode == CameraMode.SLOW_MOTION -> {}

            else -> previewBuilder.setPreviewStabilizationEnabled(false)
        }

        preview = previewBuilder.build().also {
            useCasesList.add(it)
            it.surfaceProvider = mActivity.previewView.surfaceProvider
        }

        // Pro tools use a bounded, latest-frame-only analysis stream when explicitly enabled.
        if (currentMode == CameraMode.PRO) {
            mActivity.proAnalysisUseCase()?.let(useCasesList::add)
        }

        // Panorama alone receives a low-resolution, latest-frame-only motion stream.
        // JPEG capture and the existing Preview retain binding priority.
        if (currentMode == CameraMode.PANORAMA) {
            mActivity.panoramaAnalysisUseCase()?.let(useCasesList::add)
        }

        // A second direct CameraX preview for the outer display, not a captured bitmap.
        // Bound to the SAME camera and SAME activity lifecycle as the inner preview.
        // Only plain Camera mode is used to avoid vendor extension/recording conflicts.
        val foldOuterPreview = mActivity.foldSecondaryPreviewView()?.takeIf {
            currentMode == CameraMode.CAMERA
        }?.let { outer ->
            Preview.Builder()
                .setTargetRotation(outer.display?.rotation ?: rotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatioStrategy).build()
                )
                .build().also { second ->
                    second.surfaceProvider = outer.surfaceProvider
                    useCasesList.add(second)
                }
        }

        mActivity.forceUpdateOrientationSensor()

        // CameraX resolves the first supported feature combination. Prioritize the selected
        // recording quality, then preview stabilization, then video stabilization.
        val preferredFeatures = arrayListOf<GroupableFeature>()

        if (usesFeatureGroup) {
            videoQualityFeature?.let { preferredFeatures.add(it) }
            preferredFeatures.add(GroupableFeature.PREVIEW_STABILIZATION)
            preferredFeatures.add(GroupableFeatures.VIDEO_STABILIZATION)
        }

        // Check whether the camera supports video snapshots before binding. Cache the verdict
        // for the current lens, quality and capture options.
        val snapshotUseCase = imageCapture
        if (currentMode == CameraMode.VIDEO && snapshotUseCase != null) {
            val probeKey = SnapshotProbeKey(
                lensFacing, videoQuality, usesFeatureGroup, captureMode, selectHighestResolution
            )

            if (!snapshotDropReason.containsKey(probeKey)) {
                val cameraInfo = try {
                    cameraProvider?.getCameraInfo(cameraSelector)
                } catch (exception: IllegalArgumentException) {
                    Log.e(TAG, "Failed to query camera info", exception)
                    mActivity.showMessage(mActivity.getString(R.string.bind_failure))
                    return
                }

                fun isSupported(useCases: List<UseCase>, features: Set<GroupableFeature>) =
                    cameraInfo?.isSessionConfigSupported(
                        SessionConfig(useCases = useCases, requiredFeatureGroup = features)
                    ) == true

                // Check snapshots together with the selected video quality. Prefer that quality
                // when the camera cannot support both at once.
                snapshotDropReason[probeKey] = when {
                    videoQualityFeature == null -> when {
                        isSupported(useCasesList, emptySet()) -> null
                        else -> "Video, photo and preview can't be bound together on this camera"
                    }

                    isSupported(useCasesList, setOf(videoQualityFeature)) -> null
                    isSupported(useCasesList - snapshotUseCase, setOf(videoQualityFeature)) -> {
                        "This camera can't record at the selected video quality with in-video " +
                                "snapshots enabled"
                    }

                    isSupported(useCasesList, emptySet()) -> null
                    else -> "Video, photo and preview can't be bound together on this camera"
                }
            }

            val dropReason = snapshotDropReason[probeKey]
            if (dropReason != null) {
                Log.i(TAG, "$dropReason; disabling snapshots while recording")
                useCasesList.remove(snapshotUseCase)
                imageCapture = null
            }
        }

        try {
            if (currentMode == CameraMode.SLOW_MOTION) {
                val info = cameraProvider!!.getCameraInfo(cameraSelector)
                val capture = videoCapture ?: error("Missing high-speed video capture")
                val config = HighSpeedVideoSessionConfig.Builder(capture)
                    .setPreview(requireNotNull(preview))
                    .setSlowMotionEnabled(true)
                val ranges = info.getSupportedFrameRateRanges(config.build())
                    .filter { it.lower == it.upper && it.upper >= 120 }
                val wanted = commonPref.getInt("slow_motion_preferred_fps", 120)
                val rate = ranges.firstOrNull { it.upper == wanted }
                    ?: ranges.firstOrNull { it.upper == 120 }
                    ?: ranges.filter { it.upper <= 240 }.maxByOrNull { it.upper }
                    ?: ranges.minByOrNull { it.upper }
                if (rate == null) {
                    mActivity.showMessage(R.string.slow_motion_unavailable)
                    switchMode(CameraMode.CAMERA)
                    return
                }
                activeSlowMotionFps = rate.upper
                camera = cameraProvider!!.bindToLifecycle(
                    mActivity, cameraSelector, config.setFrameRateRange(rate).build()
                )
                mActivity.showMessage(mActivity.getString(R.string.slow_motion_frame_rate, rate.upper))
            } else {
            val sessionConfig = SessionConfig(
                useCases = useCasesList,
                preferredFeatureGroup = preferredFeatures
            )

            if (preferredFeatures.isNotEmpty()) {
                val requested = preferredFeatures.toList()
                val boundLensFacing = lensFacing
                sessionConfig.setFeatureSelectionListener(
                    ContextCompat.getMainExecutor(mActivity)
                ) { selected ->
                    onFeaturesSelected(boundLensFacing, requested, videoQualityFeature, selected)
                }
            }

            camera = cameraProvider!!.bindToLifecycle(
                mActivity, cameraSelector,
                sessionConfig
            )
            }
        } catch (exception: RuntimeException) {
            if (currentMode == CameraMode.PORTRAIT &&
                lensFacing == CameraSelector.LENS_FACING_FRONT &&
                portraitFrontExplicitTargetActive &&
                (exception is IllegalArgumentException || exception is UnsupportedOperationException)) {
                // The HAL can advertise a still size that cannot be bound together with Preview.
                // Fall back to CameraX's own ranking rather than breaking Portrait entirely.
                Log.w(TAG, "PortraitFrontRes explicit still size cannot bind; reverting to " +
                    "CameraX automatic resolution selection", exception)
                portraitFrontTargetPreferenceEnabled = false
                portraitFrontExplicitTargetActive = false
                startCamera(true)
                return
            }
            if (currentMode == CameraMode.PANORAMA &&
                (exception is IllegalArgumentException || exception is UnsupportedOperationException) &&
                mActivity.onPanoramaAnalysisBindFailure()) {
                Log.w(TAG, "Panorama analysis unsupported: retrying JPEG + preview", exception)
                startCamera(true)
                return
            }
            if (currentMode == CameraMode.PRO &&
                (exception is IllegalArgumentException || exception is UnsupportedOperationException) &&
                mActivity.onProAnalysisBindFailure()) {
                Log.w(TAG, "Pro analysis stream unsupported: retrying JPEG + preview", exception)
                mActivity.showMessage("Pro overlays are unavailable on this lens")
                startCamera(true)
                return
            }
            if (foldOuterPreview != null &&
                (exception is IllegalArgumentException || exception is UnsupportedOperationException)) {
                // Not all Camera2 device configurations permit Preview + Preview + JPEG.
                // Undo the extra stream, then rebind the ordinary camera without crashing.
                Log.e(TAG, "Two CameraX previews not supported; reverting to normal preview", exception)
                mActivity.onFoldSecondaryPreviewBindFailure()
                startCamera(true)
                return
            }
            // Record permanent extension failures so the unavailable mode is hidden.
            val key = appliedExtension
            if (currentMode == CameraMode.SLOW_MOTION &&
                (exception is IllegalArgumentException || exception is UnsupportedOperationException)) {
                Log.e(TAG, "High-speed session is unsupported", exception)
                mActivity.showMessage(R.string.slow_motion_unavailable)
                switchMode(CameraMode.CAMERA)
                return
            }
            if (key == null) {
                // No extension in play: an IllegalArgumentException is a plain unsupported
                // configuration (reported and swallowed); anything else is a real bug that must
                // stay visible.
                if (exception is IllegalArgumentException) {
                    Log.e(TAG, "Failed to bind use cases", exception)
                    mActivity.showMessage(mActivity.getString(R.string.bind_failure))
                    return
                }
                throw exception
            }

            // Treat known permanent vendor extension failures as unsupported modes.
            // Propagate unexpected binding failures.
            if (exception !is UnsupportedOperationException && exception !is IllegalArgumentException) {
                throw exception
            }

            Log.e(TAG, "Extension mode $extMode failed to bind; disabling it", exception)
            extensionUsability[key] = false
            mActivity.showMessage(mActivity.getString(R.string.extension_mode_unavailable))

            // Select the active fallback mode when an extension cannot bind.
            switchMode(DEFAULT_CAMERA_MODE)
            return
        }

        if (currentMode == CameraMode.PORTRAIT &&
            lensFacing == CameraSelector.LENS_FACING_FRONT) {
            val bound = imageCapture?.resolutionInfo?.resolution
            Log.i(TAG, "PortraitFrontRes CameraX bound ImageCapture=" +
                "${bound?.width ?: "unknown"}x${bound?.height ?: "unknown"}, " +
                "explicitTarget=$portraitFrontExplicitTargetActive")
        }

        loadTabs()
        mActivity.onProCameraBound()

        // Every bind hands out a fresh LiveData in extension modes, and the old observer would
        // otherwise stay attached: after a handful of mode switches a single zoom step redrew the
        // thumb once per bind that had ever happened.
        zoomStateSource?.removeObserver(zoomStateObserver)
        zoomStateSource = null
        zoomState = null
        // Reading the new one is what costs ~100 ms behind an extension, and nothing before the
        // next message needs it: the bar below draws a freshly bound camera's 1.0x either way, and
        // every other reader is a gesture.
        handler.removeCallbacks(attachZoomState)
        handler.post(attachZoomState)

        mActivity.zoomBar.updateThumb(false)

        camera?.cameraInfo?.exposureState?.let { mActivity.exposureBar.setExposureConfig(it) }

        mActivity.settingsDialog.torchToggle.isChecked = false

        // Focus camera on touch/tap
        mActivity.previewView.setOnTouchListener(mActivity)
        camera?.cameraInfo?.let { mActivity.previewView.applyPreviewRatio(aspectRatio, it) }

        if (isInPhotoMode) {
            mActivity.sensorNotifier?.forceUpdateGyro()
        } else {
            mActivity.gCircleFrame.visibility = View.GONE
        }
    }

    fun snapPreview() {

        if (selfIlluminate) {

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
                    }

                    override fun onAnimationRepeat(p0: Animation?) {}

                }
            )

            mActivity.mainOverlay.startAnimation(animation)
        }
    }

    // probeOnMiss is false because tab refreshes must never pay for a vendor probe on the main
    // thread (see loadTabs); an unprobed mode is left out of the tabs for now, exactly like a
    // transiently-failed probe always was, and comes back on the refresh that follows its probe.
    private fun availableModes(): Set<CameraMode> {
        return CameraMode.entries.filter {
            when (it) {
                CameraMode.CAMERA, CameraMode.VIDEO, CameraMode.PORTRAIT -> true
                CameraMode.PRO -> mActivity !is SecureMainActivity
                CameraMode.SLOW_MOTION, CameraMode.HYPERLAPSE,
                CameraMode.TIMELAPSE_PHOTO, CameraMode.BURST, CameraMode.PANORAMA ->
                    mActivity !is SecureMainActivity
                CameraMode.QR_SCAN -> mActivity !is SecureMainActivity
                else -> {
                    check(it.extensionMode != ExtensionMode.NONE)
                    isExtensionUsable(
                        FRONT_CAMERA_SELECTOR, CameraSelector.LENS_FACING_FRONT,
                        it.extensionMode, probeOnMiss = false
                    ) || isExtensionUsable(
                        REAR_CAMERA_SELECTOR, CameraSelector.LENS_FACING_BACK,
                        it.extensionMode, probeOnMiss = false
                    )
                }
            }
        }.toSet()
    }

    // Read cached extension availability without making vendor calls on the UI thread.
    private fun unprobedExtensions(): List<Pair<CameraSelector, Pair<Int, Int>>> {
        if (extensionsManager == null || cameraProvider == null) return emptyList()

        val result = arrayListOf<Pair<CameraSelector, Pair<Int, Int>>>()
        for (mode in CameraMode.entries) {
            if (mode.extensionMode == ExtensionMode.NONE) continue
            for ((selector, lensFacing) in arrayOf(
                FRONT_CAMERA_SELECTOR to CameraSelector.LENS_FACING_FRONT,
                REAR_CAMERA_SELECTOR to CameraSelector.LENS_FACING_BACK,
            )) {
                val key = lensFacing to mode.extensionMode
                if (extensionUsability[key] == null) {
                    result.add(selector to key)
                }
            }
        }
        return result
    }

    private var extensionProbesInFlight = false

    private fun loadTabs() {
        if (!mActivity.shouldShowCameraModeTabs()) {
            return
        }

        // Run vendor extension probes off the UI thread and update tabs when they finish.
        val pending = unprobedExtensions()
        if (pending.isEmpty()) {
            buildTabs()
            return
        }

        if (extensionProbesInFlight) return
        val provider = cameraProvider ?: return
        val em = extensionsManager ?: return
        extensionProbesInFlight = true

        thread {
            val verdicts = HashMap<Pair<Int, Int>, Boolean?>()
            for ((selector, key) in pending) {
                verdicts[key] = probeExtension(provider, em, selector, key.second)
            }

            ContextCompat.getMainExecutor(mActivity).execute {
                extensionProbesInFlight = false
                if (mActivity.isDestroyed || mActivity.isFinishing) return@execute

                if (probedCameraProvider !== provider) {
                    // Discard probe results if the camera provider changed during the check.
                    loadTabs()
                    return@execute
                }

                for ((key, verdict) in verdicts) {
                    // Keep negative verdicts recorded during concurrent camera binding.
                    if (verdict != null && extensionUsability[key] == null) {
                        extensionUsability[key] = verdict
                    }
                }
                buildTabs()
            }
        }
    }

    fun refreshFoldModeTabs() = buildTabs(force = true)

    private val modePrefs by lazy {
        mActivity.getSharedPreferences("camera_mode_order", Context.MODE_PRIVATE)
    }
    private var lastTabSnapshot: List<String>? = null

    private fun modeKey(mode: Any): String = when (mode) {
        is CameraMode -> "camera:${mode.name}"
        is FoldTabMode -> "fold:${mode.name}"
        is CameraShortcutMode -> "shortcut:${mode.name}"
        else -> error("Unexpected camera mode")
    }

    private fun savedModeOrder(): List<String> = modePrefs.getString("order", "")!!
        .split(',').filter { it.isNotBlank() }

    private fun hiddenModeKeys(): Set<String> {
        val ordinary = modePrefs.getStringSet("hidden", setOf("camera:PRO"))?.toSet().orEmpty()
        val shownShortcuts = modePrefs.getStringSet("shortcut_visible",emptySet())?.toSet().orEmpty()
        val unusedShortcuts = CameraShortcutMode.entries.map(::modeKey).toSet() - shownShortcuts
        return ordinary + unusedShortcuts
    }
    private fun availableModeTabs(): List<Any> =
        availableModes().toList() + mActivity.availableFoldTabModes() + CameraShortcutMode.entries

    fun pasteModeTab(source: Any, destination: Any, after: Boolean) {
        if (source is ModeTabAction || destination is ModeTabAction || source == destination) return
        val current = availableModeTabs()
            .map(::modeKey).toMutableList()
        val saved = savedModeOrder()
        current.sortWith(compareBy { key -> saved.indexOf(key).let {
            if (it == -1) Int.MAX_VALUE else it
        } })
        val sourceKey = modeKey(source)
        val destinationKey = modeKey(destination)
        val reordered = app.grapheneos.camera.ui.ModeEditorOrder.paste(
            current, sourceKey, destinationKey, after)
        val hidden = modePrefs.getStringSet("hidden",setOf("camera:PRO"))?.toMutableSet()
            ?: mutableSetOf()
        hidden.remove(sourceKey)
        val shown = modePrefs.getStringSet("shortcut_visible",emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (source is CameraShortcutMode) shown.add(sourceKey)
        modePrefs.edit().putString("order",reordered.joinToString(","))
            .putStringSet("hidden",hidden).putStringSet("shortcut_visible",shown).apply()
        buildTabs(force=true)
        mActivity.tabLayout.selectEditorMode(source)
    }

    fun reorderModeTabs(source: Any, destination: Any) {
        if (source is ModeTabAction || destination is ModeTabAction) return
        val current = availableModeTabs()
            .map(::modeKey).toMutableList()
        val saved = savedModeOrder()
        current.sortWith(compareBy { key -> saved.indexOf(key).let {
            if (it == -1) Int.MAX_VALUE else it
        } })
        val from = current.indexOf(modeKey(source))
        val to = current.indexOf(modeKey(destination))
        if (from < 0 || to < 0 || from == to) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        modePrefs.edit().putString("order", current.joinToString(",")).apply()
        buildTabs(force = true)
    }

    fun hideModeTab(mode: Any) {
        if (mode == CameraMode.CAMERA || mode is ModeTabAction) return
        // Never hide the active mode without first leaving it. In-progress captures are protected.
        if (mActivity.videoCapturer.isRecording || mActivity.isIntervalCaptureBusy()) return
        if (mode is FoldTabMode && mode == mActivity.foldSelectedMode) {
            mActivity.showMessage(mActivity.getString(R.string.mode_tab_leave_fold_first))
            return
        }
        if (mode == currentMode) switchMode(CameraMode.CAMERA)
        val next = modePrefs.getStringSet("hidden", setOf("camera:PRO"))?.toMutableSet()
            ?: mutableSetOf()
        val visibleShortcuts = modePrefs.getStringSet("shortcut_visible",emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (mode is CameraShortcutMode) visibleShortcuts.remove(modeKey(mode))
        else next.add(modeKey(mode))
        modePrefs.edit().putStringSet("hidden", next)
            .putStringSet("shortcut_visible",visibleShortcuts).apply()
        buildTabs(force = true)
    }

    private fun restoreModeTab(mode: Any) {
        val next = modePrefs.getStringSet("hidden", setOf("camera:PRO"))?.toMutableSet()
            ?: mutableSetOf()
        val visibleShortcuts = modePrefs.getStringSet("shortcut_visible",emptySet())?.toMutableSet()
            ?: mutableSetOf()
        next.remove(modeKey(mode))
        if (mode is CameraShortcutMode) visibleShortcuts.add(modeKey(mode))
        modePrefs.edit().putStringSet("hidden", next)
            .putStringSet("shortcut_visible",visibleShortcuts).apply()
        buildTabs(force = true)
    }

    /** 'More' holds hidden but available modes, never modes unsupported by this device. */
    private fun showHiddenModes() {
        if (mActivity.videoCapturer.isRecording || mActivity.isIntervalCaptureBusy()) return
        val hidden = hiddenModeKeys()
        val order = savedModeOrder()
        val items: List<Any> = availableModeTabs()
            .filter { modeKey(it) in hidden }
            .sortedWith(compareBy { order.indexOf(modeKey(it)).let { pos ->
                if (pos == -1) Int.MAX_VALUE else pos
            } })
        val dp = mActivity.resources.displayMetrics.density
        fun px(value: Float) = (value * dp + 0.5f).toInt()
        val rows = LinearLayout(mActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14f), px(8f), px(14f), px(8f))
        }
        val dialog = MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.mode_tab_more)
            .setView(ScrollView(mActivity).apply { addView(rows) })
            .setNegativeButton(R.string.cancel, null)
            .create()
        items.forEach { mode ->
            rows.addView(LinearLayout(mActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(mActivity).apply {
                    text = modeTabLabel(mode)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(Color.WHITE)
                    setPadding(px(12f), px(13f), px(12f), px(13f))
                    contentDescription = mActivity.getString(R.string.mode_tab_open, modeTabLabel(mode))
                    setOnClickListener {
                        dialog.dismiss()
                        if (mActivity.tabLayout.isEditingModes) {
                            restoreModeTab(mode)
                            mActivity.tabLayout.selectEditorMode(mode)
                            mActivity.tabLayout.scrollEditorToMode(mode)
                        } else {
                            when (mode) {
                                is CameraMode -> switchMode(mode)
                                is FoldTabMode -> mActivity.openFoldModeFromMore(mode)
                                is CameraShortcutMode -> mActivity.openCameraShortcut(mode)
                            }
                        }
                    }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(mActivity).apply {
                    text = "+"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    gravity = Gravity.CENTER
                    contentDescription = mActivity.getString(R.string.mode_tab_restore, modeTabLabel(mode))
                    setOnClickListener {
                        restoreModeTab(mode)
                        if (mActivity.tabLayout.isEditingModes) {
                            mActivity.tabLayout.selectEditorMode(mode)
                            mActivity.tabLayout.scrollEditorToMode(mode)
                        }
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(px(48f), px(48f)))
            })
        }
        dialog.show()
    }

    private fun modeTabLabel(mode: Any): String = when (mode) {
        // Never rename the ordinary 180° PANORAMA tab when the separate 360°
        // shortcut is active. They are distinct tabs with fixed labels.
        CameraMode.PANORAMA -> mActivity.getString(CameraMode.PANORAMA.uiName)
        is CameraMode -> mActivity.getString(mode.uiName)
        FoldTabMode.DUAL -> mActivity.getString(R.string.fold_tab_dual)
        FoldTabMode.REAR -> mActivity.getString(R.string.fold_tab_rear)
        CameraShortcutMode.DUAL_PHOTO -> mActivity.getString(R.string.dual_photo_mode)
        CameraShortcutMode.DUAL_VIDEO -> mActivity.getString(R.string.dual_video_mode)
        CameraShortcutMode.PANORAMA_360 -> mActivity.getString(R.string.panorama_360_mode)
        ModeTabAction.MORE -> mActivity.getString(R.string.mode_tab_more)
        else -> ""
    }

    private fun editorView(mode: Any): FrameLayout {
        val dp = mActivity.resources.displayMetrics.density
        fun px(value: Float) = (value * dp + 0.5f).toInt()
        val wrapper = FrameLayout(mActivity).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = px(9f).toFloat()
                setStroke(px(1.5f), Color.WHITE)
            }
            minimumHeight = px(46f)
        }
        val isHideable = mode != CameraMode.CAMERA && mode !is ModeTabAction
        wrapper.addView(TextView(mActivity).apply {
            text = modeTabLabel(mode)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setSingleLine(true)
            gravity = Gravity.CENTER
            setPadding(px(12f), px(13f), px(if (isHideable) 25f else 12f), px(9f))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(46f)))
        if (isHideable) {
            wrapper.addView(TextView(mActivity).apply {
                text = "×"
                contentDescription = mActivity.getString(R.string.mode_tab_hide)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(px(27f), px(27f), Gravity.TOP or Gravity.END))
        }
        return wrapper
    }

    private fun buildTabs(force: Boolean = false) {
        val tabLayout = mActivity.tabLayout
        val modes: List<Any> = availableModeTabs()
        val hidden = hiddenModeKeys()
        val order = savedModeOrder()
        // A Dual mode launched from More stays visible as the CURRENT camera mode,
        // even when its tab is normally hidden. Leaving it restores the user's layout.
        val selectedMode = mActivity.activeModeTabTag()
        // Shortcuts selected through More get a temporary visible tab while
        // active. In particular 360° must never masquerade as PANORAMA 180°.
        val visible = modes.filter { it == CameraMode.CAMERA || modeKey(it) !in hidden ||
            it == selectedMode }
            .sortedWith(compareBy { mode -> order.indexOf(modeKey(mode)).let {
                if (it == -1) Int.MAX_VALUE else it
            } })
        val editing = tabLayout.isEditingModes
        val snapshot = visible.map(::modeKey) + listOf("action:more", "editor:$editing", "selected:${modeKey(selectedMode)}")
        if (!force && lastTabSnapshot == snapshot) return
        lastTabSnapshot = snapshot
        val display: List<Any> = visible + ModeTabAction.MORE
        tabLayout.markTabsRebuilding()
        tabLayout.removeAllTabs()
        val height = (if (editing) 52 else 30) * mActivity.resources.displayMetrics.density
        tabLayout.layoutParams = tabLayout.layoutParams.apply { this.height = height.toInt() }
        tabLayout.setSelectedTabIndicator(
            if (editing) null else androidx.appcompat.content.res.AppCompatResources.getDrawable(
                mActivity, R.drawable.mode_indicator
            )
        )
        display.forEach { mode ->
            val tab = tabLayout.newTab()
            tab.text = modeTabLabel(mode)
            tab.tag = mode
            if (editing) tab.customView = editorView(mode)
            tab.view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP && !tabLayout.isEditingModes) {
                    when (mode) {
                        ModeTabAction.MORE -> showHiddenModes()
                        is CameraMode, is FoldTabMode -> mActivity.finalizeMode(tab)
                        is CameraShortcutMode -> mActivity.openCameraShortcut(mode)
                    }
                }
                false
            }
            // Selecting the CAMERA/PANORAMA backing mode as well as its active
            // shortcut made the strip jump to a seemingly random nearby tab.
            tabLayout.addTab(tab, mode == selectedMode)
        }
        tabLayout.canEditModes = {
            !mActivity.videoCapturer.isRecording && !mActivity.isIntegratedDualRecording() && !mActivity.isIntervalCaptureBusy()
        }
        tabLayout.onEditModeChanged = { enabled ->
            mActivity.setModeEditorVisible(enabled)
            buildTabs(force = true)
        }
        tabLayout.onTabReordered = { from, to -> reorderModeTabs(from, to) }
        tabLayout.onTabPaste = { from, to, after -> pasteModeTab(from, to, after) }
        tabLayout.onTabHidden = { hideModeTab(it) }
        tabLayout.onMoreClicked = { showHiddenModes() }
        if (!editing && tabLayout.getTabForTag(selectedMode) == null) {
            tabLayout.getTabForAction(ModeTabAction.MORE)?.let(tabLayout::goToTab)
        }
    }

    /** The offered high-speed speeds come from this camera/session, not a device-name guess. */
    var activeSlowMotionFps: Int = 120
        private set

    /** Check both high-speed video resolutions; 240 fps may be HD-only on some cameras. */
    private fun supportedSlowMotionProfiles(info: CameraInfo): Map<Int, Quality> {
        val previewCase = preview ?: return emptyMap()
        val availableQualities = try {
            Recorder.getHighSpeedVideoCapabilities(info)
                ?.getSupportedQualities(DynamicRange.SDR).orEmpty()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to inspect high-speed video qualities", error)
            return emptyMap()
        }
        val result = linkedMapOf<Int, Quality>()
        for (quality in listOf(Quality.FHD, Quality.HD)) {
            if (quality !in availableQualities) continue
            try {
                val capture = VideoCapture.Builder(
                    Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
                ).build()
                val session = HighSpeedVideoSessionConfig.Builder(capture)
                    .setPreview(previewCase).setSlowMotionEnabled(true).build()
                info.getSupportedFrameRateRanges(session)
                    .filter { it.lower == it.upper && it.upper in listOf(120, 240) }
                    .forEach { result.putIfAbsent(it.upper, quality) }
            } catch (error: RuntimeException) {
                Log.w(TAG, "High-speed capability query failed for $quality", error)
            }
        }
        return result
    }

    fun showSlowMotionFpsChoice() {
        if (currentMode != CameraMode.SLOW_MOTION || mActivity.videoCapturer.isRecording) return
        val info = try { cameraProvider?.getCameraInfo(cameraSelector) } catch (_: Exception) { null }
            ?: return
        val options = supportedSlowMotionProfiles(info).keys.sorted()
        if (options.isEmpty()) return
        val selected = options.indexOf(commonPref.getInt("slow_motion_preferred_fps", 120)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(mActivity).setTitle(R.string.slow_motion_fps_title)
            .setSingleChoiceItems(options.map { "${it} fps" }.toTypedArray(), selected) { dialog, index ->
                dialog.dismiss()
                if (currentMode != CameraMode.SLOW_MOTION) return@setSingleChoiceItems
                val choice = options[index]
                commonPref.edit().putInt("slow_motion_preferred_fps", choice).apply()
                startCamera(true)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    fun switchMode(mode: CameraMode) {
        // Restore the ordinary camera pipeline before switching to any normal mode.
        mActivity.leaveIntegratedDualForModeChange()
        if (currentMode == mode) {
            return
        }

        if (mActivity.isIntervalCaptureBusy()) return
        mActivity.stopBurstCapture(modeChanged = true)
        mActivity.stopPanoramaCapture()
        mActivity.closeFoldDualForModeChange()
        if (mode == CameraMode.PORTRAIT) portraitFrontTargetPreferenceEnabled = true
        currentMode = mode

        mActivity.cancelFocusTimer()

        isQRMode = mode == CameraMode.QR_SCAN

        isVideoMode = mode == CameraMode.VIDEO || mode == CameraMode.SLOW_MOTION

        if (isQRMode) {
            mActivity.qrOverlay.visibility = View.VISIBLE
            mActivity.thirdOption.visibility = View.INVISIBLE

            if (scanAllCodes) {
                mActivity.setFlipCameraIcon(
                    R.drawable.cancel, R.string.stop_scanning_all_formats
                )
                mActivity.qrScanToggles.visibility = View.GONE
            } else {
                mActivity.setFlipCameraIcon(
                    R.drawable.auto, R.string.scan_all_formats
                )
                mActivity.qrScanToggles.visibility = View.VISIBLE
            }

            mActivity.cancelButtonView.visibility = View.INVISIBLE

            mActivity.captureButton.setBackgroundResource(android.R.color.transparent)
            // Entering QR mode always leaves the torch off
            mActivity.setCaptureButtonIcon(R.drawable.torch_off_button, R.string.turn_torch_on)

            mActivity.micOffIcon.visibility = View.GONE
        } else {
            mActivity.qrOverlay.visibility = View.INVISIBLE
            mActivity.thirdOption.visibility = View.VISIBLE
            mActivity.setFlipCameraIcon(R.drawable.flip_camera, R.string.flip_camera)
            mActivity.cancelButtonView.visibility = View.VISIBLE

            mActivity.qrScanToggles.visibility = View.GONE

            mActivity.captureButton.setBackgroundResource(R.drawable.cbutton_bg)

            if (mode == CameraMode.VIDEO) {
                mActivity.setCaptureButtonIcon(R.drawable.recording, R.string.start_recording)
            } else {
                mActivity.setCaptureButtonIcon(R.drawable.camera_shutter, R.string.capture)
                mActivity.micOffIcon.visibility = View.GONE
            }
        }

        mActivity.updateSelfTimerBadge()

        startCamera(true)
        mActivity.onProModeChanged()
        // Store user mode selection only for a normal app launch, not lockscreen/capture intents.
        // If CameraX falls back to Camera during a failed bind, save the actual resulting mode.
        if (mActivity.javaClass == MainActivity::class.java && reopenLastUsedMode) {
            saveCurrentModeForNextLaunch()
        }
        if (currentMode == CameraMode.PANORAMA) mActivity.showPanoramaGuide()
        if (currentMode == CameraMode.SLOW_MOTION) {
            mActivity.captureButton.post { if (currentMode == CameraMode.SLOW_MOTION) showSlowMotionFpsChoice() }
        }

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (mActivity.shouldShowCameraModeTabs()) {
            (mActivity.tabLayout.getTabForTag(mActivity.activeModeTabTag())
                ?: mActivity.tabLayout.getTabForAction(ModeTabAction.MORE))?.let { tab ->
                mActivity.tabLayout.goToTab(tab)
            }
        }
    }

    fun showMoreOptionsForQR() {
        val builder = MaterialAlertDialogBuilder(mActivity)
        builder.setTitle(mActivity.resources.getString(R.string.more_options))

        val optionNames = arrayListOf<String>()
        val optionValues = arrayListOf<Boolean>()

        for (format in BarcodeFormat.entries) {

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

        builder.setMultiChoiceItems(
            optionNames.toArray(arrayOf<String>()),
            optionValues.toBooleanArray()
        ) { _, index, isChecked ->
            optionValues[index] = isChecked
        }

        // Add OK and Cancel buttons
        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->

            val allCommonFormatsDisabled = commonFormats.none {
                allowedFormats.contains(it)
            }

            // If all formats displayed outside the dialog are disabled (main QR scanner
            // UI)
            if (allCommonFormatsDisabled) {
                val noOptionWasChecked = optionValues.none { it }

                // If no option is selected within the check box too (implying no barcode format
                // is selected at all) - don't make apply the selction made by the user
                if (noOptionWasChecked) {
                    mActivity.showMessage(
                        getString(R.string.no_barcode_selected)
                    )
                    return@setPositiveButton
                }
            }

            commonPref.edit {
                for ((index, element) in optionNames.withIndex()) {

                    val optionName = element
                    val optionValue = optionValues[index]

                    val formatSRep = "${SettingValues.Key.SCAN}_$optionName"

                    val format = BarcodeFormat.valueOf(optionName)

                    if (optionValue) {
                        if (format !in allowedFormats)
                            allowedFormats.add(format)
                    } else {
                        allowedFormats.remove(format)
                    }

                    putBoolean(formatSRep, optionValue)
                }
            }

            qrAnalyzer?.refreshHints()
        }

        builder.setNegativeButton(R.string.cancel, null)

        // Create and show the alert dialog
        val dialog = builder.create()

        dialog.setOnShowListener {
            val button: Button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            button.setOnClickListener {

            }
        }

        dialog.showIgnoringShortEdgeMode()
    }

    fun onStorageLocationNotFound() {
        // Reverting back to DEFAULT_MEDIA_STORE_CAPTURE_PATH
        storageLocation = SettingValues.Default.STORAGE_LOCATION

        val builder = MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.folder_not_found)
            .setMessage(R.string.reverting_to_default_folder)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.more_settings) { _, _ ->
                MoreSettings.start(mActivity)
            }
        val alertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.showIgnoringShortEdgeMode()
    }
}
