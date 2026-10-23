package app.grapheneos.camera.capturer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Continuous low-resolution YUV mosaic; the original JPEG output remains unchanged. */
class PanoramaCapturer(private val activity: MainActivity): SensorEventListener {
    private data class CapturedFrame(val file: File, val heading: Float, val rotation: Int,
                                     val width: Int, val height: Int)
    private class Session(val id: String, val jpegQuality: Int, val highestResolution: Boolean,
                          val fullCircle: Boolean) {
        val frames = ArrayList<CapturedFrame>()
        val timeline = PanoramaSampleTimeline()
        val displayProgress = PanoramaDisplayProgress()
        var mosaic: PanoramaLiveMosaicWorker? = null
        var streamMosaic: PanoramaStreamMosaicWorker? = null
        var lastStreamHeading: Float? = null
        var lastStreamAt = 0L
        var streamSamples = 0
        var streamSkipped = 0L
        var jpegFallbackStopped = false
        val pendingStreamPreview = java.util.concurrent.atomic.AtomicReference<
            Pair<Bitmap, PanoramaIncrementalLayout.Stats>?>(null)
        val streamUiPosted = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile var poseHistory: List<PanoramaVisualInertialFusion.Estimate> = emptyList()
        /** Only accessed by the serial CameraX analysis executor. */
        val fusion = PanoramaVisualInertialFusion()
        var fusionSource = PanoramaVisualInertialFusion.Source.INITIAL
        var fusionConfidence = 0f
        var fusionClockPaired = false
        var lastFusionLog = 0L
        var lastTelemetryLogAt = 0L
        var lastTelemetryFrameCount = 0L
        var previewCallbacks = 0L
        var previewThrottleSkips = 0L
        var capturing=false
        var finishRequested=false
        var processing=false
        var canceled=false
        var firstHeading:Float?=null
        var lastFrameHeading:Float?=null
        var direction=0
        var verticalDrift=0f
        var level=true
        var previewReliable=false
        var lastGuideLogAt=0L
        var lastGuideLevel=true
        var lastGapWarning=0L
        var lastFlowLog=0L
        var sourceBytes=0L
    }

    private val sensorManager=activity.getSystemService(SensorManager::class.java)
    private val rotationSensor=sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor=sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometerSensor=sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator=activity.getSystemService(Vibrator::class.java)
    private val audio=activity.getSystemService(AudioManager::class.java)
    private val analysisWorker=Executors.newSingleThreadExecutor()
    private val ioWorker=Executors.newSingleThreadExecutor()
    private val tracker=PanoramaFlowTracker()
    private val relativePose=PanoramaRelativePose()
    private var analysisUseCase: ImageAnalysis?=null
    private var analysisDisabled=false
    @Volatile private var active:Session?=null
    private var fullCircleSelected = false
    val isFullCircleSelected: Boolean get() = fullCircleSelected
    val isCaptureInProgress: Boolean get() = active != null

    /** Called by the More menu before capture; ordinary panorama stays 180°. */
    fun configureFullCircle(enabled:Boolean):Boolean {
        if (active != null) return false
        fullCircleSelected = enabled
        strip?.clear()
        strip?.configureLimit(if (enabled) MAX_CIRCLE_FRAMES else MAX_FRAMES)
        strip?.updateAngularProgress(0f, 0, if (enabled) 360 else 180)
        return true
    }
    private var strip:PanoramaProgressStrip?=null
    /** Only the panorama shutter icon rotates while the background stitcher works. */
    private var shutterAnimator:ObjectAnimator?=null

    private fun startShutterSpinner() {
        if (activity.camConfig.currentMode != CameraMode.PANORAMA || shutterAnimator != null) return
        activity.captureButton.isEnabled = false
        activity.captureButton.setImageResource(R.drawable.panorama_processing_arrow)
        activity.captureButton.contentDescription = activity.getString(R.string.panorama_processing)
        shutterAnimator = ObjectAnimator.ofFloat(activity.captureButton, View.ROTATION,
            0f, 360f).apply {
            duration = 1050L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopShutterSpinner() {
        shutterAnimator?.cancel()
        shutterAnimator = null
        activity.captureButton.rotation = 0f
    }
    private var lastRawHeading:Float?=null
    @Volatile private var accumulatedHeading=0f
    private var lastCaptureAt=0L
    private var lastAnalysisAt=0L

    private val completion:(String,android.net.Uri?,String)->Unit={ id,uri,error ->
        val session=active
        if (session?.id == id && session.processing) {
            if (uri != null) {
                activity.imageCapturer.onImageSaverSuccess(
                    CapturedItem(ITEM_TYPE_IMAGE,id,uri,"PANO_${id}.jpg"))
                feedback(true)
                val estimated = error.substringAfter("sensorFallbacks=", "0")
                    .substringBefore('/').toIntOrNull() ?: 0
                if (estimated > 0)
                    activity.showMessage("Panorama saved; $estimated transitions estimated from sensors (check seams)")
                else activity.showMessage(R.string.panorama_saved)
            } else {
                activity.showMessage("Panorama failed: ${error.take(170)}")
            }
            reset(session,deleteCache=false)
        }
    }
    init { PanoramaProcessingService.onFinished=completion }

    fun createAnalysis():ImageAnalysis? {
        if (analysisDisabled) return null
        val analysis=ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(activity.previewView.display?.rotation ?: Surface.ROTATION_0)
            .setResolutionSelector(ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(
                    Size(1024,768),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        analysis.setAnalyzer(analysisWorker,::analyzePreview)
        analysisUseCase=analysis
        return analysis
    }
    fun disableAnalysisAfterBindFailure():Boolean {
        if (analysisDisabled) return false
        analysisDisabled=true
        analysisUseCase?.clearAnalyzer()
        analysisUseCase=null
        Log.w(TAG,"Triple use-case bind rejected: panorama still shutter available without optical analysis")
        return true
    }

    private fun analyzePreview(image:ImageProxy) {
        try {
            val session=active ?: return
            if (session.canceled || session.finishRequested || image.format!=ImageFormat.YUV_420_888) return
            session.previewCallbacks++
            val now=SystemClock.uptimeMillis()
            if (now-lastAnalysisAt<130L) {
                session.previewThrottleSkips++
                return
            }
            lastAnalysisAt=now
            val plane=image.planes[0]
            val sample=PanoramaLumaSampler.sample(
                plane.buffer,image.width,image.height,plane.rowStride,plane.pixelStride,
                image.imageInfo.rotationDegrees,PanoramaFlowTracker.WIDTH,PanoramaFlowTracker.HEIGHT)
            val motion=try {tracker.track(sample)} catch(error:RuntimeException) {
                Log.w(TAG,"Optical guidance unavailable, gyro-only guidance active",error)
                tracker.reset();null
            }
            val accepted=session.timeline.addFrame(
                timestampNs=image.imageInfo.timestamp,
                receiveTimeNs=SystemClock.elapsedRealtimeNanos(),
                width=PanoramaFlowTracker.WIDTH,height=PanoramaFlowTracker.HEIGHT,
                rotationDegrees=image.imageInfo.rotationDegrees,luma=sample,
                opticalReliable=motion?.reliable ?: false,
                opticalInlierFraction=motion?.inlierFraction ?: 0f,
                opticalSharpness=motion?.sharpness ?: 0f,
                trackedFeatures=motion?.tracked ?: 0,
                dx=motion?.dx ?: 0f,dy=motion?.dy ?: 0f
            )
            if (accepted) {
                // This estimator never blocks shutter release or overrides the
                // capture decisions until its calibration is verified on Pixel devices.
                val frame=session.timeline.newestFrame()
                if (frame != null && active === session && !session.canceled) {
                    val sensorMatches=session.timeline.matchSensors(frame.timestampNs,55_000_000L)
                    val estimate=session.fusion.process(frame,sensorMatches)
                    session.poseHistory=session.fusion.recentEstimates()
                    session.fusionSource=estimate.source
                    session.fusionConfidence=estimate.confidence
                    session.fusionClockPaired=estimate.synchronizedClocks
                    if (now-session.lastFusionLog>=1200L) {
                        session.lastFusionLog=now
                        Log.i(TAG,"Panorama v2 fusion session=${session.id} " +
                            "source=${estimate.source} confidence=${estimate.confidence} " +
                            "clockPaired=${estimate.synchronizedClocks} " +
                            "dx=${estimate.dx} dy=${estimate.dy} " +
                            "yawDelta=${estimate.headingDeltaRadians ?: "N/A"} " +
                            "scale=${estimate.calibrationPixelsPerRadian ?: "UNCALIBRATED"} " +
                            "reason=${estimate.reasons}")
                    }
                }
            }
            // Copy a small YUV color sample while ImageProxy is alive.
            // Submission is non-blocking and the worker coalesces all pending frames.
            // No pixel buffer or CameraX object survives past image.close().
            val liveHeading = accumulatedHeading
            if (active === session && !session.finishRequested &&
                PanoramaStreamPolicy.accept(liveHeading, session.lastStreamHeading,
                    now, session.lastStreamAt, session.streamSamples)) {
                try {
                    val rotated = image.imageInfo.rotationDegrees % 180 != 0
                    val color = PanoramaYuvColorSampler.sample(image.width, image.height,
                        image.imageInfo.rotationDegrees,
                        PanoramaYuvColorSampler.Plane(image.planes[0].buffer,
                            image.planes[0].rowStride, image.planes[0].pixelStride),
                        PanoramaYuvColorSampler.Plane(image.planes[1].buffer,
                            image.planes[1].rowStride, image.planes[1].pixelStride),
                        PanoramaYuvColorSampler.Plane(image.planes[2].buffer,
                            image.planes[2].rowStride, image.planes[2].pixelStride),
                        if (rotated) PanoramaYuvColorSampler.HEIGHT else PanoramaYuvColorSampler.WIDTH,
                        if (rotated) PanoramaYuvColorSampler.WIDTH else PanoramaYuvColorSampler.HEIGHT)
                    session.streamMosaic?.submit(color, liveHeading,
                        image.imageInfo.rotationDegrees)
                    session.lastStreamHeading = liveHeading
                    session.lastStreamAt = now
                    session.streamSamples++
                } catch (error: RuntimeException) {
                    session.streamSkipped++
                    if (session.streamSkipped == 1L || session.streamSkipped % 30L == 0L)
                        Log.w(TAG, "Panorama YUV color sampling skipped", error)
                }
            }
            if (now-session.lastTelemetryLogAt>=2000L) {
                val previousLogAt=session.lastTelemetryLogAt
                val snapshot=session.timeline.stats()
                val fps=if (previousLogAt > 0L && now > previousLogAt)
                    (snapshot.framesAccepted-session.lastTelemetryFrameCount)*1000.0 /
                        (now-previousLogAt) else 0.0
                session.lastTelemetryLogAt=now
                session.lastTelemetryFrameCount=snapshot.framesAccepted
                val latest=snapshot.lastFrameTimestampNs
                val match=latest?.let {session.timeline.matchSensors(it)}
                val sensorGapMs=match?.rotationGapNs?.div(1_000_000.0)
                Log.i(TAG,"Panorama v2 timeline session=${session.id} " +
                    "fps=$fps callbacks=${session.previewCallbacks} " +
                    "throttleSkips=${session.previewThrottleSkips} " +
                    "frames=${snapshot.framesBuffered} evicted=${snapshot.framesEvicted} " +
                    "sensors=${snapshot.sensorSamplesBuffered} " +
                    "frameBytes=${snapshot.frameBytes} " +
                    "frameTimestampRejects=${snapshot.frameTimestampsRejected} " +
                    "sensorTimestampRejects=${snapshot.sensorTimestampsRejected} " +
                    "rotationGapMs=${sensorGapMs ?: "UNPAIRED"} " +
                    "gyroPaired=${match?.gyro!=null} gravityPaired=${match?.accelerometer!=null} " +
                    "fusion=${session.fusionSource} " +
                    "fusionConfidence=${session.fusionConfidence} " +
                    "clockValidated=${session.fusionClockPaired} " +
                    "streamSamples=${session.streamSamples} streamSkipped=${session.streamSkipped}")
            }
            if (motion!=null) {
                val log=now-session.lastFlowLog>1500L
                if (log) {
                    session.lastFlowLog=now
                    Log.i(TAG,"Panorama FLOW dx=${motion.dx} dy=${motion.dy} tracked=${motion.tracked} " +
                        "inlier=${motion.inlierFraction} sharpness=${motion.sharpness} reliable=${motion.reliable}")
                }
                activity.runOnUiThread {
                    if (active === session && !session.canceled) {
                        session.previewReliable=motion.reliable
                        strip?.updateOpticalTracking(motion.reliable)
                    }
                }
            }
        } catch(error:RuntimeException) {
            Log.w(TAG,"Panorama preview analysis failed",error)
        } finally { image.close() }
    }

    fun onModeEnter() {
        if (activity.camConfig.currentMode!=CameraMode.PANORAMA) return
        if (strip==null) {
            strip=PanoramaProgressStrip(activity).apply { visibility=View.GONE }
            val density=activity.resources.displayMetrics.density
            activity.previewContainer.addView(strip,ConstraintLayout.LayoutParams(
                0,(86*density).roundToInt()).apply {
                startToStart=activity.previewView.id
                endToEnd=activity.previewView.id
                topToTop=activity.previewView.id
                setMargins((14*density).roundToInt(),(60*density).roundToInt(),
                    (14*density).roundToInt(),0)
            })
        }
        if (active?.processing == true) startShutterSpinner()
        strip?.visibility=View.VISIBLE
        if (active == null) strip?.updateAngularProgress(0f, 0, if (fullCircleSelected) 360 else 180)
    }

    fun toggle() {
        val session=active
        if (session==null) start() else if(!session.processing) finish(session)
    }

    private fun start() {
        if (activity.camConfig.currentMode!=CameraMode.PANORAMA ||
            activity.camConfig.camera==null || !activity.camConfig.canTakePicture ||
            activity.camConfig.imageCapture==null) return
        if (activity.camConfig.lensFacing!=CameraSelector.LENS_FACING_BACK) {
            activity.showMessage(R.string.panorama_rear_required);return
        }
        if (rotationSensor==null) {
            activity.showMessage(R.string.panorama_sensor_unavailable);return
        }
        val session=Session(
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date()),
            activity.camConfig.photoQuality.coerceIn(1,100),
            activity.camConfig.selectHighestResolution, fullCircleSelected)
        active=session
        session.mosaic = PanoramaLiveMosaicWorker(session.id) { bitmap, stats ->
            activity.runOnUiThread {
                if (active === session && !session.canceled && !session.processing) {
                    strip?.updateLivePreview(bitmap, stats) ?: bitmap.recycle()
                } else bitmap.recycle()
            }
        }
        session.streamMosaic = PanoramaStreamMosaicWorker(session.id) { bitmap, stats ->
            // UI mailbox is also single-slot: old rendered bitmaps are recycled if
            // the main thread cannot keep up. No unbounded runOnUiThread backlog.
            session.pendingStreamPreview.getAndSet(bitmap to stats)?.first?.recycle()
            postStreamPreview(session)
        }
        analysisWorker.execute {tracker.reset();lastAnalysisAt=0L}
        relativePose.reset();lastRawHeading=null;accumulatedHeading=0f;lastCaptureAt=0L
        strip?.clear()
        strip?.configureLimit(if (session.fullCircle) MAX_CIRCLE_FRAMES else MAX_FRAMES)
        strip?.updateAngularProgress(0f, 0, if (session.fullCircle) 360 else 180)
        strip?.visibility=View.VISIBLE
        stopShutterSpinner()
        sensorManager?.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME)
        gyroSensor?.let {sensorManager?.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        accelerometerSensor?.let {
            sensorManager?.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)
        }
        activity.setCaptureButtonIcon(R.drawable.cancel,R.string.panorama_finish)
        activity.cbText.visibility=View.VISIBLE
        updateGuide(session)
        feedback(false)
        Log.i(TAG,"Panorama v2 phase 4 session=${session.id}, continuous YUV mosaic enabled; " +
            "source=CameraX original JPEG (unchanged); flow=${!analysisDisabled}; " +
            "gyro=${gyroSensor!=null} accelerometer=${accelerometerSensor!=null}")
        takeFrame(session)
    }

    private fun postStreamPreview(session: Session) {
        if (!session.streamUiPosted.compareAndSet(false, true)) return
        activity.runOnUiThread {
            try {
                val item = session.pendingStreamPreview.getAndSet(null)
                if (item != null) {
                    if (active === session && !session.canceled && !session.processing) {
                        strip?.updateStreamPreview(item.first, item.second) ?: item.first.recycle()
                        // Once real YUV previews arrive, retire the expensive
                        // JPEG-only mosaic. JPEG files and final stitcher stay intact.
                        if (!session.jpegFallbackStopped) {
                            session.jpegFallbackStopped = true
                            session.mosaic?.close()
                            session.mosaic = null
                        }
                    } else item.first.recycle()
                }
            } finally {
                session.streamUiPosted.set(false)
                if (session.pendingStreamPreview.get() != null) postStreamPreview(session)
            }
        }
    }

    private fun takeFrame(session:Session) {
        if (active !== session || session.canceled || session.capturing ||session.processing ||
            session.finishRequested || activity.camConfig.currentMode!=CameraMode.PANORAMA) return
        val capture=activity.camConfig.imageCapture ?:return
        session.capturing=true
        lastCaptureAt=SystemClock.uptimeMillis()
        val heading=accumulatedHeading
        try {
            capture.takePicture(activity.mainExecutor,object:ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image:ImageProxy) {
                    try {
                        if(image.format!=ImageFormat.JPEG) throw IOException("Unexpected image format ${image.format}")
                        val data=image.planes[0].buffer
                        val jpeg=ByteArray(data.remaining());data.get(jpeg)
                        val rotation=image.imageInfo.rotationDegrees
                        val captureTimestampNs=image.imageInfo.timestamp
                        // Use the closest time-synchronized pose from the bounded
                        // analysis history; uncertain/outdated poses are not used.
                        val closestPose=session.poseHistory.minByOrNull {
                            kotlin.math.abs(it.timestampNs - captureTimestampNs)
                        }?.takeIf {
                            kotlin.math.abs(it.timestampNs - captureTimestampNs) <= 150_000_000L
                        }
                        val width=image.width;val height=image.height
                        val index=session.frames.size+1
                        ioWorker.execute {
                            var target:File?=null
                            var thumb:Bitmap?=null
                            try {
                                val file=File(File(activity.cacheDir,"panorama-v119/${session.id}"),
                                    "frame_${index.toString().padStart(2,'0')}.jpg")
                                target=file
                                file.parentFile?.mkdirs()
                                file.writeBytes(jpeg)
                                val bitmap=thumbnail(jpeg,rotation)
                                    ?: throw IOException("Cannot decode JPEG keyframe")
                                thumb=bitmap
                                val ready=CapturedFrame(file,heading,rotation,width,height)
                                activity.runOnUiThread {
                                    session.capturing=false
                                    if(active !== session ||session.canceled) {
                                        file.delete();bitmap.recycle();return@runOnUiThread
                                    }
                                    session.frames.add(ready)
                                    session.sourceBytes+=jpeg.size
                                    strip?.addFrame(bitmap)
                                    bitmap.recycle()
                                    // The worker owns only a disk path and builds its reduced
                                    // preview asynchronously, off the CameraX/IO/UI threads.
                                    session.mosaic?.submit(file, heading, rotation, closestPose)
                                    if(session.firstHeading==null) session.firstHeading=heading
                                    session.lastFrameHeading=heading
                                    if(session.direction==0 && session.frames.size>1) {
                                        session.direction=if(heading >= session.firstHeading!!) 1 else -1
                                    }
                                    val selectedSweep=abs(heading-(session.firstHeading ?: heading))
                                    Log.i(TAG,"Panorama KEYFRAME #${session.frames.size} " +
                                        "sensorHeading=${Math.toDegrees(heading.toDouble()).roundToInt()} " +
                                        "sensorCoverage=${Math.toDegrees(selectedSweep.toDouble()).roundToInt()} " +
                                        "JPEG=${width}x$height bytes=${jpeg.size} flowReliable=${session.previewReliable}; " +
                                        "v2Tracking=${session.fusionSource} " +
                                        "v2Confidence=${session.fusionConfidence}; " +
                                        "mode=${if(session.previewReliable) "optical-guided" else "gyro-bridge"}; " +
                                        "imageCoverage=ESTIMATED until sequential alignment")
                                    updateGuide(session)
                                    val sessionByteLimit = if(session.fullCircle)
                                        MAX_CIRCLE_BYTES else MAX_SESSION_BYTES
                                    if(session.sourceBytes >= sessionByteLimit) {
                                        Log.w(TAG,"Source byte budget reached: ${session.sourceBytes}; " +
                                            "finishing partial real coverage; never claiming 180°")
                                        activity.showMessage("Panorama capture memory limit reached; joining captured views")
                                    }
                                    if(session.finishRequested ||session.frames.size >=
                                        (if (session.fullCircle) MAX_CIRCLE_FRAMES else MAX_FRAMES) ||
                                        session.sourceBytes>=sessionByteLimit ||
                                        PanoramaSweepPolicy.mayAutoFinishSelected(
                                            session.firstHeading,session.lastFrameHeading,
                                            if (session.fullCircle) PanoramaSweepPolicy.FULL_CIRCLE_RADIANS
                                            else PanoramaSweepPolicy.AUTO_FINISH_RADIANS)) finish(session)
                                    else captureIfMoved(session)
                                }
                            } catch(error:Exception) {
                                target?.delete();thumb?.recycle()
                                Log.e(TAG,"Panorama native JPEG preservation failed",error)
                                activity.runOnUiThread { fail(session) }
                            }
                        }
                    } catch(error:Exception) {
                        Log.e(TAG,"Unable to copy panorama JPEG",error)
                        fail(session)
                    } finally {image.close()}
                }
                override fun onError(exception:ImageCaptureException) {
                    Log.e(TAG,"Panorama still capture failed",exception)
                    fail(session)
                }
            })
        } catch(error:RuntimeException) {Log.e(TAG,"Camera unavailable",error);fail(session)}
    }

    private fun thumbnail(jpeg:ByteArray,degrees:Int):Bitmap? {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(jpeg,0,jpeg.size,bounds)
        if(bounds.outWidth<=0 ||bounds.outHeight<=0)return null
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>300)sample*=2
        val original=BitmapFactory.decodeByteArray(jpeg,0,jpeg.size,
            BitmapFactory.Options().apply {inSampleSize=sample}) ?:return null
        if (degrees%360==0)return original
        val m=Matrix().apply {postRotate(degrees.toFloat())}
        val rotated=Bitmap.createBitmap(original,0,0,original.width,original.height,m,true)
        if(rotated !== original) original.recycle()
        return rotated
    }

    private fun captureIfMoved(session:Session) {
        if(active !== session ||session.capturing ||session.finishRequested ||session.processing)return
        val last=session.lastFrameHeading ?:return
        val delta=accumulatedHeading-last
        session.direction=PanoramaSweepPolicy.direction(session.direction,delta,session.level)
        if (PanoramaSweepPolicy.shouldCapture(session.direction,delta,session.level,
                SystemClock.uptimeMillis()-lastCaptureAt,session.previewReliable)) {
            if(abs(delta)>GAP_WARNING_RADIANS &&
                SystemClock.uptimeMillis()-session.lastGapWarning>2000L) {
                session.lastGapWarning=SystemClock.uptimeMillis()
                Log.w(TAG,"Panorama large SENSOR gap ${Math.toDegrees(abs(delta).toDouble())}°; " +
                    "retaining frame rather than deadlocking. Optical overlap still unverified")
            }
            takeFrame(session)
        }
    }

    private fun finish(session:Session) {
        if(active !== session ||session.processing ||session.canceled)return
        session.finishRequested=true
        if(session.capturing)return
        if (session.fullCircle && PanoramaSweepPolicy.selectedSensorCoverage(
                session.firstHeading, session.lastFrameHeading) <
                PanoramaSweepPolicy.FULL_CIRCLE_MIN_RADIANS) {
            session.finishRequested=false
            if(session.frames.size>=MAX_CIRCLE_FRAMES ||
                session.sourceBytes>=MAX_CIRCLE_BYTES) {
                activity.showMessage("360° incomplete: capture safety limit reached, no circular photo saved")
                reset(session)
                return
            }
            activity.showMessage("360°: continue rotating until you return to the starting view")
            return
        }
        sensorManager?.unregisterListener(this)
        if(session.frames.size<2) {
            activity.showMessage(R.string.panorama_need_two_frames)
            reset(session);return
        }
        session.processing=true
        // A single system-respecting haptic CONFIRM exactly when recording ends:
        // the last JPEG is already on disk; stitching has not started yet.
        // Unlike direct vibrator calls this honours system haptic preferences.
        activity.captureButton.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
        session.streamMosaic?.close()
        session.streamMosaic = null
        session.mosaic?.close()
        session.mosaic = null
        session.pendingStreamPreview.getAndSet(null)?.first?.recycle()
        activity.captureButton.isEnabled=false
        // The common shutter counter is drawn *inside* the 84dp button.
        // Showing an entire sentence there overlaps the button and other UI.
        activity.cbText.visibility=View.INVISIBLE
        startShutterSpinner()
        val frames=session.frames.toList()
        val selected=abs((session.lastFrameHeading ?:0f)-(session.firstHeading ?:0f))
        val sweepDegrees=Math.toDegrees(selected.toDouble()).roundToInt()
        Log.i(TAG,"Panorama v2 timeline final stats: ${session.timeline.stats()} " +
            "callbacks=${session.previewCallbacks} throttleSkips=${session.previewThrottleSkips} " +
            "fusion=${session.fusionSource} confidence=${session.fusionConfidence} " +
            "clockValidated=${session.fusionClockPaired}")
        Log.i(TAG,"Panorama processing: frames=${frames.size}, " +
            "selectedSensorSweep=${sweepDegrees}°, measured image coverage UNVERIFIED")
        try {
            PanoramaProcessingService.begin(activity,session.id,
                frames.map {it.file.absolutePath},frames.map {it.rotation}.toIntArray(),
                frames.map {it.heading}.toFloatArray(),
                session.jpegQuality,session.highestResolution,session.fullCircle)
        } catch(error:Exception) {
            Log.e(TAG,"Unable to start Panorama foreground worker",error)
            activity.showMessage("Unable to start panorama processing: ${error.message}")
            reset(session)
        }
    }

    private fun updateGuide(session:Session) {
        if(active !== session)return
        activity.cbText.text=session.frames.size.toString()
        val first=session.firstHeading
        if(first!=null) {
            strip?.updateMovement(session.direction,
                abs(accumulatedHeading-first)/PanoramaSweepPolicy.STEP_RADIANS)
            strip?.updateSelectedCoverage(Math.toDegrees(
                PanoramaSweepPolicy.selectedSensorCoverage(first,session.lastFrameHeading)
                    .toDouble()).roundToInt())
        }
        val displayed = session.displayProgress.update(session.firstHeading,
            accumulatedHeading, session.direction,
            if (session.fullCircle) PanoramaSweepPolicy.FULL_CIRCLE_RADIANS
            else PanoramaSweepPolicy.AUTO_FINISH_RADIANS)
        strip?.updateAngularProgress(displayed.fraction, displayed.degrees,
            if (session.fullCircle) 360 else 180)
        strip?.updateAlignment(session.verticalDrift,session.level)
    }

    private fun feedback(completed:Boolean) {
        val ringer=audio?.ringerMode ?:return
        if(ringer==AudioManager.RINGER_MODE_SILENT)return
        if(ringer==AudioManager.RINGER_MODE_NORMAL)activity.camConfig.mPlayer.playShutterSound()
        val effect=if(completed) VibrationEffect.createWaveform(longArrayOf(0,35,85,90),-1)
            else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        vibrator?.vibrate(effect)
    }
    private fun fail(session:Session) {
        if(active !== session)return
        activity.showMessage(R.string.panorama_failed)
        reset(session)
    }
    private fun reset(session:Session,deleteCache:Boolean=true) {
        if(active !== session)return
        sensorManager?.unregisterListener(this)
        session.canceled=true
        session.mosaic?.close()
        session.mosaic = null
        session.streamMosaic?.close()
        session.streamMosaic = null
        session.pendingStreamPreview.getAndSet(null)?.first?.recycle()
        if(deleteCache && !session.processing)
            File(activity.cacheDir,"panorama-v119/${session.id}").deleteRecursively()
        session.frames.clear()
        session.timeline.clear()
        active=null
        strip?.clear();strip?.visibility=View.GONE
        stopShutterSpinner()
        activity.captureButton.isEnabled=true
        if(activity.camConfig.currentMode==CameraMode.PANORAMA) {
            activity.setCaptureButtonIcon(R.drawable.camera_shutter,R.string.capture)
            activity.cbText.visibility=View.INVISIBLE
            onModeEnter()
        }
    }
    fun onModeExit() {
        fullCircleSelected = false
        active?.let { if(!it.processing)reset(it) }
        strip?.visibility=View.GONE
        stopShutterSpinner()
        if (active?.processing == true) activity.captureButton.isEnabled = true
    }
    override fun onSensorChanged(event:SensorEvent) {
        val session=active ?:return
        if(session.canceled ||session.finishRequested || session.processing)return
        val kind=when(event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> PanoramaSampleTimeline.SensorKind.ROTATION_VECTOR
            Sensor.TYPE_GYROSCOPE -> PanoramaSampleTimeline.SensorKind.GYROSCOPE
            Sensor.TYPE_ACCELEROMETER -> PanoramaSampleTimeline.SensorKind.ACCELEROMETER
            else -> return
        }
        session.timeline.addSensor(event.timestamp,kind,event.values)
        // Preserve rotation-vector guidance. Raw IMU values are recorded,
        // NOT integrated into poses until camera/sensor clocks are validated.
        if(event.sensor.type!=Sensor.TYPE_ROTATION_VECTOR)return
        val matrix=FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix,event.values)
        val pose=relativePose.update(matrix)
        val last=lastRawHeading
        if(last!=null &&pose.reliable) accumulatedHeading+=angularDifference(pose.heading,last)
        if(pose.reliable)lastRawHeading=pose.heading
        session.verticalDrift=pose.vertical
        val pitch=if(session.level)0.14f else 0.10f
        val roll=if(session.level)0.18f else 0.13f
        session.level=pose.reliable &&abs(pose.vertical)<=pitch &&abs(pose.bank)<=roll
        val now=SystemClock.uptimeMillis()
        if(session.lastGuideLevel!=session.level &&now-session.lastGuideLogAt>=300L) {
            session.lastGuideLevel=session.level;session.lastGuideLogAt=now
            Log.i(TAG,"Panorama guide ${if(session.level) "YELLOW" else "RED"}: " +
                "gyroHeading=${Math.toDegrees(accumulatedHeading.toDouble()).roundToInt()} " +
                "level=${session.level} flowReliable=${session.previewReliable}")
        }
        updateGuide(session)
        captureIfMoved(session)
    }
    private fun angularDifference(angle:Float,baseline:Float):Float {
        var difference=angle-baseline
        val pi=Math.PI.toFloat()
        while(difference>pi)difference-=2*pi
        while(difference < -pi)difference+=2*pi
        return difference
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    fun close() {
        onModeExit()
        if(PanoramaProcessingService.onFinished === completion)
            PanoramaProcessingService.onFinished=null
        strip?.let {activity.previewContainer.removeView(it)}
        strip=null

        analysisUseCase?.clearAnalyzer();analysisUseCase=null
        analysisWorker.shutdown();ioWorker.shutdown()
    }
    companion object {
        private const val TAG="PanoramaCapturer"
        private const val GAP_WARNING_RADIANS=0.34f
        private const val MAX_FRAMES=PanoramaProgressStrip.MAX_FRAMES
        private const val MAX_CIRCLE_FRAMES=60
        private const val MAX_SESSION_BYTES=1_200_000_000L
        private const val MAX_CIRCLE_BYTES=2_400_000_000L
    }
}
