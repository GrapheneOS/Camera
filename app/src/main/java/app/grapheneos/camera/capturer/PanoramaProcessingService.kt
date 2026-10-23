package app.grapheneos.camera.capturer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidxc.exifinterface.media.ExifInterface
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.R
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground stitch job: survives activity/mode changes; only explicit Cancel discards an output. */
internal class PanoramaProcessingService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val cancel = AtomicBoolean(false)
    private var processing = false
    private var sessionId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Panorama processing", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            if (intent.getStringExtra(ID) == sessionId) {
                cancel.set(true)
                notify("Canceling panorama…", false)
            }
            return START_NOT_STICKY
        }
        if (processing || intent?.action != ACTION_START) return START_NOT_STICKY
        val id = intent.getStringExtra(ID) ?: return START_NOT_STICKY
        val frames = intent.getStringArrayExtra(PATHS) ?: return START_NOT_STICKY
        val angles = intent.getIntArrayExtra(ANGLES) ?: return START_NOT_STICKY
        val headings = intent.getFloatArrayExtra(HEADINGS) ?: return START_NOT_STICKY
        val jpegQuality = intent.getIntExtra(JPEG_QUALITY, -1)
        val highestResolution = intent.getBooleanExtra(HIGHEST_RESOLUTION, false)
        val fullCircle = intent.getBooleanExtra(FULL_CIRCLE, false)
        if (frames.size < 2 || frames.size > (if (fullCircle) 60 else 30) || frames.size != angles.size ||
            frames.size != headings.size || headings.any { !it.isFinite() } ||
            !id.matches(Regex("[0-9_]+")) || jpegQuality !in 1..100) {
            Log.e(TAG, "Invalid panorama service inputs")
            stopSelf(startId); return START_NOT_STICKY
        }
        processing = true
        sessionId = id
        cancel.set(false)
        val initial = notification("Processing panorama…", true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, initial)
        executor.execute {
            execute(id, frames, angles, headings, jpegQuality, highestResolution, fullCircle, startId)
        }
        return START_NOT_STICKY
    }

    private fun execute(id: String, paths: Array<String>, angles: IntArray,
                        headings: FloatArray, jpegQuality: Int,
                        highestResolution: Boolean, fullCircle:Boolean, startId: Int) {
        val folder = File(cacheDir, "panorama-v119/$id")
        val output = File(folder, "composed.jpg")
        var uri: Uri? = null
        var failure = ""
        var successNote = ""
        try {
            // Reject crafted paths and missing frames; never read arbitrary app files from an Intent.
            val canonicalFolder = folder.canonicalPath + File.separator
            val inputs = paths.map { path ->
                File(path).also { file ->
                    if (!file.canonicalPath.startsWith(canonicalFolder) || !file.isFile || file.length() > 100_000_000L)
                        throw IOException("Invalid panorama source file")
                }
            }
            // Align and stitch the source JPEGs.
            notify("Aligning ${inputs.size} original photos (OpenCV)…", true)
            // Size the output to available heap memory.
            val maximumOutputPixels = PanoramaOutputBudget.forHeapBytes(
                Runtime.getRuntime().maxMemory())
            Log.i(TAG,"Panorama quality=$jpegQuality highest=$highestResolution " +
                "maxStitchPixels=$maximumOutputPixels heap=${Runtime.getRuntime().maxMemory()}")
            val frames = inputs.indices.map { index ->
                // Convert EXIF focal length to source-pixel coordinates.
                val focalPx = try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(inputs[index].absolutePath, bounds)
                    val equiv = ExifInterface(inputs[index]).getAttributeInt(
                        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                    PanoramaFocalCalibration.from35mmEquivalent(
                        bounds.outWidth, bounds.outHeight, equiv)
                } catch (error: Exception) {
                    Log.w(TAG, "No usable EXIF focal for frame ${index + 1}", error)
                    null
                }
                if (index == 0) Log.i(TAG, "Panorama focal source=" +
                    if (focalPx == null) "UNCALIBRATED approximation" else "EXIF")
                PanoramaModule.Frame(
                    inputs[index], angles[index], headings[index], focalPx ?: Float.NaN)
            }
            val stitched = OpenCvPanoramaEngine.join(
                frames, maxOutputPixels = maximumOutputPixels,
                canceled = { cancel.get() }, fullCircle = fullCircle)
            try {
                if (cancel.get()) throw IOException("Panorama canceled")
                output.outputStream().buffered().use { stream ->
                    if (!stitched.bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)) {
                        throw IOException("Panorama JPEG encoding failed")
                    }
                }
                val detail = "OpenCV-SIFT ${if (fullCircle) "360° cyclic" else "cylindrical"}, frames=${inputs.size}, " +
                    "result=${stitched.bitmap.width}x${stitched.bitmap.height}, " +
                    "jpegQuality=$jpegQuality highestSource=$highestResolution, " +
                    "sensorFallbacks=${stitched.gyroFallbackPairs}/${inputs.size - 1}"
                Log.i(TAG, "stitch completed: $detail")
                successNote = detail
            } finally {
                stitched.bitmap.recycle()
            }
            if (!cancel.get()) uri = saveOutput(output,id)
            else failure="Canceled by user"
        } catch (error: Throwable) {
            failure = error.message ?: error.javaClass.simpleName
            Log.e(TAG,"Panorama processing failed: $failure",error)
        } finally {
            folder.deleteRecursively()
            if (uri != null) {
                notify(if (successNote.contains("discarded")) "Panorama saved (incomplete last view)"
                    else "Panorama saved", false)
                Log.i(TAG, "Panorama saved")
            } else {
                notify(if (cancel.get()) "Panorama canceled" else "Panorama failed: ${failure.take(100)}", false)
            }
            val result = uri
            val message = if (result != null) successNote else failure
            Handler(Looper.getMainLooper()).post { onFinished?.invoke(id,result,message) }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
            processing=false
        }
    }

    private fun saveOutput(jpeg: File, id: String): Uri {
        if (!jpeg.isFile || jpeg.length()==0L) throw IOException("Empty panorama file")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"PANO_${id}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"DCIM/Panoramas/")
            put(MediaStore.MediaColumns.IS_PENDING,1)
            put(MediaStore.Images.Media.DATE_TAKEN,System.currentTimeMillis())
        }
        val uri=contentResolver.insert(CamConfig.imageCollectionUri,values)
            ?:throw IOException("Unable to create panorama MediaStore item")
        try {
            contentResolver.openOutputStream(uri)?.use { output -> jpeg.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Unable to write panorama")
            if (cancel.get()) throw IOException("Panorama was canceled")
            check(contentResolver.update(uri,ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING,0)
            },null,null)>=1) { "Unable to publish panorama" }
            return uri
        } catch (error: Throwable) {
            contentResolver.delete(uri,null,null)
            throw error
        }
    }

    private fun notification(text:String,active:Boolean): Notification {
        val builder=Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fold Camera · Panorama")
            .setContentText(text)
            .setCategory(if(active) Notification.CATEGORY_SERVICE else Notification.CATEGORY_STATUS)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
        if (active) {
            builder.setProgress(0,0,true)
            val intent=Intent(this,PanoramaProcessingService::class.java).apply {
                action=ACTION_CANCEL;putExtra(ID,sessionId)
            }
            val pending=PendingIntent.getService(this,119,intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pending
                ).build()
            )
        }
        return builder.build()
    }
    private fun notify(text:String,active:Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification(text,active))
    }
    override fun onDestroy() { super.onDestroy();executor.shutdown() }

    companion object {
        private const val TAG="PanoramaService"
        private const val CHANNEL="panorama_v119"
        private const val NOTIFICATION_ID=55119
        private const val ACTION_START="app.foldcamera.PANORAMA_STITCH"
        private const val ACTION_CANCEL="app.foldcamera.PANORAMA_CANCEL"
        private const val ID="panorama_id"
        private const val PATHS="panorama_paths"
        private const val ANGLES="panorama_rotations"
        private const val HEADINGS="panorama_headings"
        private const val JPEG_QUALITY="panorama_jpeg_quality"
        private const val HIGHEST_RESOLUTION="panorama_highest_resolution"
        private const val FULL_CIRCLE="panorama_full_circle"
        @Volatile var onFinished: ((String,Uri?,String)->Unit)?=null
        fun begin(context:Context,id:String,paths:List<String>,rotations:IntArray,
                  headings:FloatArray,jpegQuality:Int,highestResolution:Boolean,
                  fullCircle:Boolean=false) {
            ContextCompat.startForegroundService(context,Intent(context,PanoramaProcessingService::class.java).apply {
                action=ACTION_START
                putExtra(ID,id)
                putExtra(PATHS,paths.toTypedArray())
                putExtra(ANGLES,rotations)
                putExtra(HEADINGS,headings)
                putExtra(JPEG_QUALITY,jpegQuality)
                putExtra(HIGHEST_RESOLUTION,highestResolution)
                putExtra(FULL_CIRCLE,fullCircle)
            })
        }
    }
}
