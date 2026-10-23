package app.grapheneos.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.constraintlayout.widget.ConstraintLayout
import app.grapheneos.camera.CameraMode
import app.grapheneos.camera.R
import app.grapheneos.camera.ui.activities.MainActivity
import java.util.concurrent.Executors
import kotlin.math.abs
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Camera2 interop is used only for a bound Pro camera.
 * The options are discarded when leaving Pro; JPEG capture remains the existing CameraX path.
 */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
class ProCameraModule(private val activity: MainActivity) {
    private val prefs = activity.getSharedPreferences("fold_camera_pro_v1", Context.MODE_PRIVATE)
    private val density = activity.resources.displayMetrics.density
    private fun dp(n: Int): Int = (n * density + .5f).toInt()
    private val strip = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(5), dp(7), dp(5), dp(7))
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
    }
    private val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val secondaryRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }
    private var compactControls: Boolean? = null
    private val footerBackground = View(activity).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val status = TextView(activity).apply {
        textSize = 11f
        setTextColor(Color.LTGRAY)
        gravity = Gravity.CENTER
    }
    private val controls = linkedMapOf<String, Button>()
    private val keys = listOf("ISO", "SPEED", "EV", "FOCUS", "WB", "METER")
    private val tools = Button(activity)
    private val controlPanel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(9), dp(6), dp(9), dp(6))
        visibility = View.GONE
        background = GradientDrawable().apply {
            setColor(0xEF1B1D25.toInt())
            cornerRadius = dp(14).toFloat()
        }
    }
    private val panelTitle = TextView(activity).apply {
        textSize = 14f
        setTextColor(Color.WHITE)
    }
    private val panelValue = TextView(activity).apply {
        textSize = 13f
        gravity = Gravity.END
        setTextColor(0xFFD4DAF9.toInt())
    }
    private val panelSlider = SeekBar(activity).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(0xFFC1CAEC.toInt())
        thumbTintList = ColorStateList.valueOf(0xFFC1CAEC.toInt())
    }
    private val panelOptions = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val optionScroller = HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        clipToPadding = false
        setPadding(dp(4), dp(4), dp(4), dp(4))
        addView(panelOptions)
    }
    private val panelAuto = Button(activity).apply {
        text = "AUTO"
        isAllCaps = false
        textSize = 11f
        minWidth = 0
        minimumWidth = 0
    }
    private val panelClose = Button(activity).apply {
        text = "×"
        isAllCaps = false
        textSize = 17f
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { closePanel() }
    }
    private var currentPanel: String? = null
    private var lastSliderApplied = 0L
    private fun styleControl(button: Button, selected: Boolean) {
        button.background = GradientDrawable().apply {
            setColor(if (selected) 0xFF464C65.toInt() else 0xFF2A2D35.toInt())
            cornerRadius = dp(8).toFloat()
        }
        button.setTextColor(Color.WHITE)
    }
    private val overlay = ProPreviewOverlay(activity)
    private var activeCamera: Camera? = null
    private var activeControl: Camera2CameraControl? = null
    private var characteristics: Camera2CameraInfo? = null
    private var isoRange: Range<Int>? = null
    private var shutterRange: Range<Long>? = null
    private var focusMax = 0f
    private var supportsManual = false
    private var supportsAeRegions = false
    private var supportsAeLock = false
    private var supportsAfRegions = false
    private var allowedAwb = intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
    private var exposureRange: Range<Int>? = null
    private var iso: Int? = null
    private var shutter: Long? = null
    private var focus: Float? = null
    private var ev: Int = 0
    private var kelvin: Int? = null
    private var awbMode: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO
    private var meter = 0
    private var locked = false
    private var peaking = false
    private var histogram = false
    private var zebra = false
    private var magnified = false
    private var bracketingEnabled = false
    private var bracketIndex = -1
    private var bracketIndices = intArrayOf()
    private var originalEv = 0
    private var exposureStepEv = 0f
    private val analysisExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "fold-pro-analysis").apply { isDaemon = true }
    }
    @Volatile private var lastAnalyzed = 0L

    init {
        keys.forEach { key ->
            val button = Button(activity).apply {
                textSize = 11f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(3), dp(2), dp(3), dp(2))
                setOnClickListener { openPanel(key) }
            }
            controls[key] = button
            styleControl(button, false)
        }
        tools.text = "TOOLS"
        tools.isAllCaps = false
        tools.textSize = 11f
        tools.minWidth = 0
        tools.minimumWidth = 0
        tools.setPadding(dp(3), dp(2), dp(3), dp(2))
        tools.setOnClickListener { openPanel("TOOLS") }
        styleControl(tools, false)
        styleControl(panelAuto, false)
        styleControl(panelClose, false)

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(panelTitle, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(panelValue, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(panelAuto, LinearLayout.LayoutParams(dp(64), dp(44)))
            addView(panelClose, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        controlPanel.addView(header, LinearLayout.LayoutParams(-1, dp(44)))
        controlPanel.addView(panelSlider, LinearLayout.LayoutParams(-1, dp(42)))
        controlPanel.addView(optionScroller, LinearLayout.LayoutParams(-1, -2))
        panelAuto.setOnClickListener { resetSelectedControl() }
        panelSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    panelValue.text = formatSliderValue(currentPanel, progress)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSliderApplied >= 180L) {
                        lastSliderApplied = now
                        commitSliderValue(currentPanel, progress, refreshPanel = false)
                    }
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                commitSliderValue(currentPanel, seekBar.progress)
            }
        })
        strip.addView(controlPanel, LinearLayout.LayoutParams(-1, -2))
        strip.addView(row, LinearLayout.LayoutParams(-1, -2))
        strip.addView(secondaryRow, LinearLayout.LayoutParams(-1, -2))
        strip.addView(status, LinearLayout.LayoutParams(-1, -2))

        // A stable footer keeps Pro controls above the shutter and mode selector on
        // both screens. It must not follow the preview's aspect-ratio-dependent edge.
        val root = (activity.previewContainer.parent as View).parent as RelativeLayout
        root.addView(footerBackground, 1, RelativeLayout.LayoutParams(-1, -1).apply {
            addRule(RelativeLayout.ALIGN_TOP, R.id.three_buttons)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        })
        root.addView(strip, RelativeLayout.LayoutParams(-1, -2).apply {
            addRule(RelativeLayout.ABOVE, R.id.three_buttons)
            bottomMargin = 0
        })
        root.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) arrangeControls(view.width)
        }
        arrangeControls(root.width.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.widthPixels)
        activity.previewContainer.addView(overlay, ConstraintLayout.LayoutParams(0, 0).apply {
            startToStart = R.id.preview
            endToEnd = R.id.preview
            topToTop = R.id.preview
            bottomToBottom = R.id.preview
        })
        overlay.visibility = View.GONE
        overlay.isClickable = false
        strip.bringToFront()
        reload()
    }

    private fun arrangeControls(widthPx: Int) {
        if (widthPx <= 0) return
        val compact = widthPx / density < 520f
        if (compactControls == compact) return
        compactControls = compact
        row.removeAllViews()
        secondaryRow.removeAllViews()
        secondaryRow.visibility = if (compact) View.VISIBLE else View.GONE
        (keys.mapNotNull(controls::get) + tools).forEachIndexed { index, button ->
            val target = if (compact && index >= 4) secondaryRow else row
            target.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(3), dp(2), dp(3), dp(2))
            })
        }
    }

    private fun reload() {
        iso = prefs.getInt("iso", -1).takeIf { it > 0 }
        shutter = prefs.getLong("shutter", -1).takeIf { it > 0 }
        focus = prefs.getFloat("focus", -1f).takeIf { it >= 0f }
        ev = prefs.getInt("ev", 0)
        kelvin = prefs.getInt("kelvin", -1).takeIf { it > 0 }
        awbMode = prefs.getInt("awb", CameraMetadata.CONTROL_AWB_MODE_AUTO)
        meter = prefs.getInt("meter", 0)
        peaking = prefs.getBoolean("peaking", false)
        histogram = prefs.getBoolean("histogram", false)
        zebra = prefs.getBoolean("zebra", false)
        locked = false
        magnified = false
        bracketingEnabled = prefs.getBoolean("bracket", false)
    }

    fun onCameraBound() {
        if (activity.camConfig.currentMode != CameraMode.PRO) {
            leave()
            return
        }
        val camera = activity.camConfig.camera ?: return
        if (camera !== activeCamera) {
            activeCamera = camera
            activeControl = try { Camera2CameraControl.from(camera.cameraControl) } catch (_: Exception) { null }
            characteristics = try { Camera2CameraInfo.from(camera.cameraInfo) } catch (_: Exception) { null }
        }
        val info = characteristics
        val capabilities = info?.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        supportsManual = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        isoRange = if (supportsManual) info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) else null
        shutterRange = if (supportsManual) info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) else null
        focusMax = info?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        supportsAeRegions = (info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0
        supportsAfRegions = (info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0
        supportsAeLock = info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
        allowedAwb = info?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?: intArrayOf(CameraMetadata.CONTROL_AWB_MODE_AUTO)
        exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
        exposureStepEv = camera.cameraInfo.exposureState.exposureCompensationStep.toFloat()
        // Saved manual values from another lens cannot be applied outside the new ranges.
        iso = iso?.takeIf { isoRange?.contains(it) == true && shutterRange != null }
        shutter = shutter?.takeIf { shutterRange?.contains(it) == true && isoRange != null }
        if (iso == null || shutter == null) { iso = null; shutter = null }
        focus = focus?.takeIf { focusMax > 0f && it <= focusMax }
        if (awbMode !in allowedAwb) awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO
        if (kelvin != null && awbMode == CameraMetadata.CONTROL_AWB_MODE_AUTO) {
            val supported = wbModes.filter { it.third > 0 && it.second in allowedAwb }
            if (supported.isNotEmpty()) awbMode = supported.minBy { abs(it.third - kelvin!!) }.second
            else kelvin = null
        }
        if (!supportsAeRegions) meter = 0
        val evAllowed = exposureRange
        if (evAllowed != null) ev = ev.coerceIn(evAllowed.lower, evAllowed.upper) else ev = 0
        strip.visibility = View.VISIBLE
        footerBackground.visibility = View.VISIBLE
        closePanel()
        overlay.visibility = if (peaking || histogram || zebra) View.VISIBLE else View.GONE
        overlay.setTypes(peaking, histogram, zebra)
        refreshLabels()
        apply()
    }

    fun onModeChanged() {
        if (activity.camConfig.currentMode != CameraMode.PRO) leave()
        else onCameraBound()
    }

    fun dispose() {
        leave()
        analysisExecutor.shutdownNow()
    }

    private fun leave() {
        bracketIndex = -1
        bracketIndices = intArrayOf()
        val old = activeControl
        activeControl = null
        activeCamera = null
        characteristics = null
        try { old?.clearCaptureRequestOptions() } catch (_: Exception) { }
        strip.visibility = View.GONE
        footerBackground.visibility = View.GONE
        closePanel()
        overlay.visibility = View.GONE
        if (magnified) activity.previewView.scaleX = 1f
        if (magnified) activity.previewView.scaleY = 1f
        magnified = false
        locked = false
    }

    private fun apply() {
        if (activity.camConfig.currentMode != CameraMode.PRO) return
        val builder = CaptureRequestOptions.Builder()
        val manual = iso != null && shutter != null && supportsManual
        if (manual) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso!!)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutter!!)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            if (locked && supportsAeLock) builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }
        if (focus != null && focusMax > 0f) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus!!)
        }
        // CameraX owns AF control whenever manual focus is not selected.
        // Never request AWB_OFF without a calibrated color-correction transform.
        // On devices without a reliable manual AWB pipeline, use advertised AWB modes.
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
        try { activeControl?.setCaptureRequestOptions(builder.build()) }
        catch (e: Exception) { Log.w("FoldCameraPro", "Camera2 request rejected", e) }
        if (manual) {
            try { activity.camConfig.camera?.cameraControl?.setExposureCompensationIndex(0) } catch (_: Exception) { }
        } else {
            try { activity.camConfig.camera?.cameraControl?.setExposureCompensationIndex(ev) } catch (_: Exception) { }
        }
        refreshLabels()
    }

    private fun refreshLabels() {
        controls["ISO"]?.text = "ISO\n${iso ?: "Auto"}"
        controls["SPEED"]?.text = "SPEED\n${shutter?.let {
            if (it >= 1_000_000_000L) "${it / 1_000_000_000L}s"
            else "1/${1_000_000_000L / it.coerceAtLeast(1)}"
        } ?: "Auto"}"
        controls["EV"]?.text = "EV\n${if (iso != null) "—" else "%.1f".format(Locale.US, ev * exposureStepEv)}"
        controls["FOCUS"]?.text = "FOCUS\n${focus?.let { "%.1f".format(it) } ?: "AF"}"
        controls["WB"]?.text = "WB\n${kelvin?.let { "~${it}K" } ?: if (awbMode == CameraMetadata.CONTROL_AWB_MODE_AUTO) "Auto" else wbModeName(awbMode)}"
        controls["METER"]?.text = "METER\n${arrayOf("Matrix", "Center", "Spot")[meter.coerceIn(0, 2)]}"
        controls["ISO"]?.isEnabled = supportsManual && isoRange != null && shutterRange != null
        controls["SPEED"]?.isEnabled = supportsManual && isoRange != null && shutterRange != null
        controls["EV"]?.isEnabled = iso == null && exposureRange != null
        controls["FOCUS"]?.isEnabled = true
        controls["METER"]?.isEnabled = supportsAeRegions && iso == null
        controls.forEach { (key, button) -> styleControl(button, key == currentPanel) }
        styleControl(tools, currentPanel == "TOOLS")
        status.text = when {
            iso != null -> "EV unavailable with manual exposure"
            kelvin != null -> "White balance approximated by camera presets"
            else -> ""
        }
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private val wbModes = listOf(
        Triple("Auto", CameraMetadata.CONTROL_AWB_MODE_AUTO, 0),
        Triple("Daylight", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, 5500),
        Triple("Cloudy", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, 6500),
        Triple("Tungsten", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, 2800),
        Triple("Fluorescent", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, 4000)
    )

    private fun wbModeName(mode: Int): String =
        wbModes.firstOrNull { it.second == mode }?.first ?: "Auto"

    private fun closePanel() {
        currentPanel = null
        controlPanel.visibility = View.GONE
        controls.forEach { (_, button) -> styleControl(button, false) }
        styleControl(tools, false)
    }

    private fun openPanel(key: String) {
        if (currentPanel == key) {
            closePanel()
            return
        }
        currentPanel = key
        lastSliderApplied = 0L
        controlPanel.visibility = View.VISIBLE
        panelTitle.text = when (key) {
            "SPEED" -> "SHUTTER SPEED"
            "WB" -> "WHITE BALANCE"
            "METER" -> "METERING"
            "TOOLS" -> "PRO TOOLS"
            else -> key
        }
        panelSlider.visibility = if (key in listOf("ISO", "SPEED", "EV", "FOCUS", "WB")) View.VISIBLE else View.GONE
        panelAuto.visibility = if (key == "TOOLS") View.GONE else View.VISIBLE
        optionScroller.visibility = if (key in listOf("WB", "METER", "TOOLS")) View.VISIBLE else View.GONE
        panelOptions.removeAllViews()
        when (key) {
            "WB" -> {
                wbModes.filter { it.second in allowedAwb }.forEach { (label, mode, _) ->
                    addPanelChoice(label) {
                        awbMode = mode
                        kelvin = null
                        saveApply()
                        updatePanel()
                    }
                }
                panelSlider.visibility = if (wbModes.count { it.second in allowedAwb && it.third != 0 } > 1) View.VISIBLE else View.GONE
            }
            "METER" -> {
                arrayOf("Matrix", "Center", "Spot").forEachIndexed { index, label ->
                    addPanelChoice(label) {
                        meter = index
                        saveApply()
                        if (index == 1) meterAt(.5f, .5f)
                        updatePanel()
                    }
                }
            }
            "TOOLS" -> {
                listOf(
                    "Peaking" to 0, "Histogram" to 1, "Zebra" to 2,
                    "Magnifier" to 3, "AE/AF Lock" to 4,
                    "Bracketing" to 5, "Reset" to 6
                ).forEach { (label, action) ->
                    addPanelChoice(label) {
                        applyTool(action)
                        updatePanel()
                    }
                }
            }
        }
        updatePanel()
        controls.forEach { (name, button) -> styleControl(button, name == key) }
        styleControl(tools, key == "TOOLS")
    }

    private fun addPanelChoice(label: String, action: () -> Unit) {
        val button = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minWidth = dp(88)
            minimumWidth = dp(88)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setSingleLine(true)
            setOnClickListener { action() }
        }
        styleControl(button, false)
        panelOptions.addView(button, LinearLayout.LayoutParams(-2, dp(48)).apply {
            setMargins(dp(4), dp(2), dp(4), dp(2))
        })
    }

    private fun sliderLog(progress: Int, low: Double, high: Double): Double {
        if (low <= 0.0 || high <= low) return low
        return exp(ln(low) + (ln(high) - ln(low)) * progress.coerceIn(0, 1000) / 1000.0)
    }

    private fun sliderProgress(value: Double, low: Double, high: Double): Int {
        if (low <= 0.0 || high <= low) return 0
        return ((ln(value.coerceIn(low, high)) - ln(low)) / (ln(high) - ln(low)) * 1000)
            .roundToInt().coerceIn(0, 1000)
    }

    private fun formatSpeed(value: Long): String = when {
        value >= 1_000_000_000L -> "%.1fs".format(Locale.US, value / 1_000_000_000.0)
        else -> "1/${(1_000_000_000.0 / value.coerceAtLeast(1)).roundToInt()}s"
    }

    private fun formatSliderValue(key: String?, progress: Int): String = when (key) {
        "ISO" -> isoRange?.let { sliderLog(progress, it.lower.toDouble(), it.upper.toDouble()).roundToInt().toString() } ?: "Unavailable"
        "SPEED" -> shutterRange?.let {
            formatSpeed(sliderLog(progress, it.lower.toDouble(), it.upper.toDouble()).toLong())
        } ?: "Unavailable"
        "EV" -> exposureRange?.let {
            "%.1f EV".format(Locale.US, (it.lower + progress.coerceIn(0, it.upper - it.lower)) * exposureStepEv)
        } ?: "Unavailable"
        "FOCUS" -> if (progress == 0) "∞" else "%.2f D".format(Locale.US, focusMax * progress / 1000f)
        "WB" -> "~${2000 + 7 * progress.coerceIn(0, 1000)}K (approx.)"
        else -> ""
    }

    private fun updatePanel() {
        val key = currentPanel ?: return
        val available = when (key) {
            "ISO" -> isoRange != null && supportsManual && shutterRange != null
            "SPEED" -> shutterRange != null && supportsManual && isoRange != null
            "EV" -> iso == null && exposureRange != null
            "FOCUS" -> focusMax > 0f
            "WB" -> true
            else -> true
        }
        panelSlider.isEnabled = available
        val progress = when (key) {
            "ISO" -> isoRange?.let { sliderProgress((iso ?: it.lower).toDouble(), it.lower.toDouble(), it.upper.toDouble()) } ?: 0
            "SPEED" -> shutterRange?.let {
                sliderProgress((shutter ?: it.lower).toDouble(), it.lower.toDouble(), it.upper.toDouble())
            } ?: 0
            "EV" -> exposureRange?.let { (ev - it.lower).coerceIn(0, 1000) } ?: 0
            "FOCUS" -> if (focusMax > 0f) (((focus ?: 0f) / focusMax) * 1000).roundToInt() else 0
            "WB" -> ((kelvin ?: wbModes.firstOrNull { it.second == awbMode }?.third ?: 5500) - 2000).coerceIn(0, 7000) / 7
            else -> 0
        }
        if (key == "EV") panelSlider.max = exposureRange?.let { it.upper - it.lower }?.coerceAtLeast(0) ?: 0
        else panelSlider.max = 1000
        panelSlider.progress = progress
        panelValue.text = when (key) {
            "ISO" -> iso?.toString() ?: "AUTO"
            "SPEED" -> shutter?.let { formatSpeed(it) } ?: "AUTO"
            "EV" -> if (iso != null) "Manual exposure" else "%.1f EV".format(ev * exposureStepEv)
            "FOCUS" -> focus?.let { if (it == 0f) "∞" else "%.2f D".format(Locale.US, it) } ?: "AF"
            "WB" -> kelvin?.let { "~${it}K (preset)" } ?: wbModeName(awbMode)
            "METER" -> arrayOf("Matrix", "Center", "Spot")[meter.coerceIn(0, 2)]
            "TOOLS" -> ""
            else -> ""
        }
    }

    private fun commitSliderValue(key: String?, progress: Int, refreshPanel: Boolean = true) {
        when (key) {
            "ISO" -> {
                val range = isoRange ?: return
                val sr = shutterRange ?: return
                iso = sliderLog(progress, range.lower.toDouble(), range.upper.toDouble()).roundToInt().coerceIn(range.lower, range.upper)
                shutter = shutter ?: sr.clamp(16_666_667L)
            }
            "SPEED" -> {
                val range = shutterRange ?: return
                val ir = isoRange ?: return
                shutter = sliderLog(progress, range.lower.toDouble(), range.upper.toDouble()).toLong().coerceIn(range.lower, range.upper)
                iso = iso ?: ir.clamp(100)
            }
            "EV" -> {
                val range = exposureRange ?: return
                if (iso != null) return
                ev = (range.lower + progress).coerceIn(range.lower, range.upper)
            }
            "FOCUS" -> {
                if (focusMax <= 0f) return
                focus = focusMax * progress / 1000f
                locked = false
            }
            "WB" -> {
                val desired = 2000 + progress.coerceIn(0, 1000) * 7
                val supported = wbModes.filter { it.third > 0 && it.second in allowedAwb }
                if (supported.isEmpty()) return
                awbMode = supported.minBy { abs(it.third - desired) }.second
                kelvin = desired
            }
            else -> return
        }
        saveApply()
        if (refreshPanel) updatePanel()
    }

    private fun resetSelectedControl() {
        when (currentPanel) {
            "ISO", "SPEED" -> { iso = null; shutter = null }
            "EV" -> ev = 0
            "FOCUS" -> { focus = null; locked = false }
            "WB" -> { kelvin = null; awbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO }
            "METER" -> meter = 0
        }
        saveApply()
        updatePanel()
    }

    private fun saveApply() {
        prefs.edit().putInt("iso", iso ?: -1).putLong("shutter", shutter ?: -1L)
            .putFloat("focus", focus ?: -1f).putInt("ev", ev)
            .putInt("kelvin", kelvin ?: -1).putInt("awb", awbMode)
            .putInt("meter", meter).apply()
        apply()
    }

    /** Return false to let MainActivity show its regular focus ring and trigger CameraX AF. */
    @Suppress("UNUSED_PARAMETER")
    fun onPreviewTap(x: Float, y: Float): Boolean {
        if (activity.camConfig.currentMode != CameraMode.PRO) return false
        if (controlPanel.visibility == View.VISIBLE) closePanel()
        if (locked) return true
        if (focus != null) {
            focus = null
            saveApply()
        }
        return false
    }

    fun spotMeteringEnabled(): Boolean =
        activity.camConfig.currentMode == CameraMode.PRO && meter == 2 && supportsAeRegions

    private fun meterAt(nx: Float, ny: Float) {
        if (!supportsAeRegions || iso != null) return
        val point = activity.previewView.meteringPointFactory.createPoint(
            nx * activity.previewView.width, ny * activity.previewView.height, if (meter == 2) .08f else .35f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
        try { activity.camConfig.camera?.cameraControl?.startFocusAndMetering(action) }
        catch (e: Exception) { Log.w("FoldCameraPro", "Metering not accepted", e) }
    }

    private fun applyTool(index: Int) {
        when (index) {
            0 -> { peaking = !peaking; toggleAnalysis() }
            1 -> { histogram = !histogram; toggleAnalysis() }
            2 -> { zebra = !zebra; toggleAnalysis() }
            3 -> {
                magnified = !magnified
                activity.previewView.scaleX = if (magnified) 2.5f else 1f
                activity.previewView.scaleY = if (magnified) 2.5f else 1f
                overlay.visibility = if (!magnified && (peaking || histogram || zebra)) View.VISIBLE else View.GONE
            }
            4 -> {
                if (iso != null || focus != null || !supportsAeLock) {
                    activity.showMessage("AE/AF lock requires supported auto exposure and focus")
                } else {
                    locked = !locked
                    if (locked && supportsAfRegions) {
                        val point = activity.previewView.meteringPointFactory.createPoint(
                            activity.previewView.width / 2f, activity.previewView.height / 2f)
                        activity.camConfig.camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(point).disableAutoCancel().build())
                    } else if (!locked) {
                        activity.camConfig.camera?.cameraControl?.cancelFocusAndMetering()
                    }
                    apply()
                }
            }
            5 -> {
                if (iso != null || !canBracket()) {
                    activity.showMessage("Bracketing requires auto exposure and a ±2 EV range")
                } else {
                    bracketingEnabled = !bracketingEnabled
                    prefs.edit().putBoolean("bracket", bracketingEnabled).apply()
                }
            }
            6 -> {
                prefs.edit().clear().apply()
                reload()
                toggleAnalysis()
                apply()
            }
        }
    }

    private fun canBracket(): Boolean {
        val range = exposureRange ?: return false
        if (exposureStepEv <= 0f) return false
        val delta = (2f / exposureStepEv).toInt()
        return delta > 0 && ev - delta >= range.lower && ev + delta <= range.upper
    }

    /** Called by the ordinary white shutter; only captures multiple files when explicitly enabled. */
    fun tryStartBracket(): Boolean {
        if (activity.camConfig.currentMode != CameraMode.PRO || !bracketingEnabled) return false
        if (bracketIndex >= 0) return true
        if (iso != null || !canBracket()) {
            activity.showMessage("Disable manual exposure or reduce EV before bracketing")
            return true
        }
        if (activity.imageCapturer.isTakingPicture) return true
        originalEv = ev
        val delta = (2f / exposureStepEv).toInt()
        bracketIndices = intArrayOf(ev - delta, ev, ev + delta)
        bracketIndex = 0
        captureBracketFrame()
        return true
    }

    private fun captureBracketFrame() {
        if (bracketIndex !in bracketIndices.indices || activity.camConfig.currentMode != CameraMode.PRO) {
            finishBracket()
            return
        }
        val camera = activity.camConfig.camera ?: run { finishBracket(); return }
        val requested = bracketIndices[bracketIndex]
        try {
            camera.cameraControl.setExposureCompensationIndex(requested).addListener({
                if (bracketIndex >= 0 && activity.camConfig.currentMode == CameraMode.PRO &&
                    !activity.imageCapturer.isTakingPicture) {
                    activity.imageCapturer.takePicture()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(activity))
        } catch (error: Exception) {
            Log.e("FoldCameraPro", "Bracket exposure request failed", error)
            finishBracket()
        }
    }

    fun onPictureSaved() {
        if (bracketIndex < 0) return
        bracketIndex++
        if (bracketIndex >= bracketIndices.size) finishBracket() else captureBracketFrame()
    }
    fun onPictureFailed() {
        if (bracketIndex >= 0) {
            activity.showMessage("Bracketing interrupted; saved frames were kept")
            finishBracket()
        }
    }
    fun nextImagePrefix(): String? {
        if (bracketIndex < 0 || bracketIndex >= bracketIndices.size) return null
        return when (bracketIndex) {
            0 -> "PRO_BKT_M2_"
            1 -> "PRO_BKT_0_"
            else -> "PRO_BKT_P2_"
        }
    }
    private fun finishBracket() {
        bracketIndex = -1
        bracketIndices = intArrayOf()
        if (activity.camConfig.currentMode == CameraMode.PRO) {
            activity.camConfig.camera?.cameraControl?.setExposureCompensationIndex(originalEv)
        }
    }

    private fun toggleAnalysis() {
        prefs.edit().putBoolean("peaking", peaking).putBoolean("histogram", histogram)
            .putBoolean("zebra", zebra).apply()
        overlay.setTypes(peaking, histogram, zebra)
        overlay.visibility = if (peaking || histogram || zebra) View.VISIBLE else View.GONE
        // Analysis is a distinct use case; rebind only when toggling tools, never per frame.
        activity.camConfig.startCamera(true)
    }

    /** Avoid freezing the camera if this device cannot bind Preview + JPEG + Analysis. */
    fun disableAnalysisAfterBindFailure(): Boolean {
        if (!peaking && !histogram && !zebra) return false
        peaking = false; histogram = false; zebra = false
        prefs.edit().putBoolean("peaking", false).putBoolean("histogram", false)
            .putBoolean("zebra", false).apply()
        overlay.visibility = View.GONE
        overlay.setTypes(false, false, false)
        return true
    }

    fun createAnalysis(): ImageAnalysis? {
        if (activity.camConfig.currentMode != CameraMode.PRO || !(peaking || histogram || zebra)) return null
        return ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build().also { useCase ->
                useCase.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }
            }
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzed < 250L) return
            lastAnalyzed = now
            val w = proxy.width
            val h = proxy.height
            if (w <= 1 || h <= 1) return
            val plane = proxy.planes[0]
            val buf = plane.buffer
            val stride = plane.rowStride
            val pixelStride = plane.pixelStride
            val step = max(1, max(w, h) / 160)
            val bins = Array(3) { IntArray(32) }
            val hot = ArrayList<Pair<Float, Float>>(900)
            val peaks = ArrayList<Pair<Float, Float>>(900)
            val grayscale = IntArray(((w + step - 1) / step) * ((h + step - 1) / step))
            val widthSamples = (w + step - 1) / step
            for (yy in 0 until h step step) {
                for (xx in 0 until w step step) {
                    val offset = yy * stride + xx * pixelStride
                    if (offset + 3 >= buf.limit()) continue
                    // CameraX 1.6.x camera-core ImageAnalysis source documents R,G,B,A.
                    val r = buf.get(offset).toInt() and 255
                    val g = buf.get(offset + 1).toInt() and 255
                    val b = buf.get(offset + 2).toInt() and 255
                    bins[0][r / 8]++; bins[1][g / 8]++; bins[2][b / 8]++
                    val y = (r * 77 + g * 150 + b * 29) shr 8
                    val ix = xx / step
                    val iy = yy / step
                    val pos = iy * widthSamples + ix
                    grayscale[pos] = y
                    if (zebra && y > 242 && hot.size < 900) hot.add(xx.toFloat() / w to yy.toFloat() / h)
                    if (peaking && ix > 0 && iy > 0 && peaks.size < 900) {
                        val grad = abs(y - grayscale[pos - 1]) + abs(y - grayscale[pos - widthSamples])
                        if (grad > 95) peaks.add(xx.toFloat() / w to yy.toFloat() / h)
                    }
                }
            }
            val rotation = proxy.imageInfo.rotationDegrees
            overlay.post {
                if (activity.camConfig.currentMode == CameraMode.PRO) overlay.showData(bins, hot, peaks, rotation)
            }
        } finally { proxy.close() }
    }
}

/** Tiny preview-derived analysis display. All annotations are ephemeral and never in saved JPEGs. */
private class ProPreviewOverlay(ctx: Context) : View(ctx) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hist = Array(3) { IntArray(32) }
    private var hot: List<Pair<Float, Float>> = emptyList()
    private var peaks: List<Pair<Float, Float>> = emptyList()
    private var rotation = 0
    private var showPeaks = false
    private var showHist = false
    private var showZebra = false

    fun setTypes(peaking: Boolean, histogram: Boolean, zebra: Boolean) {
        showPeaks = peaking; showHist = histogram; showZebra = zebra; invalidate()
    }
    fun showData(h: Array<IntArray>, z: List<Pair<Float, Float>>, p: List<Pair<Float, Float>>, rot: Int) {
        hist = h; hot = z; peaks = p; rotation = rot; invalidate()
    }
    private fun xy(pair: Pair<Float, Float>): Pair<Float, Float> = when (rotation) {
        90 -> (1f - pair.second) to pair.first
        180 -> (1f - pair.first) to (1f - pair.second)
        270 -> pair.second to (1f - pair.first)
        else -> pair
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dot = max(2f, width / 170f)
        if (showZebra) {
            pen.color = 0xC0FFE000.toInt(); pen.strokeWidth = 2f
            hot.forEach { (u, v) ->
                val (x, y) = xy(u to v)
                val px = x * width; val py = y * height
                canvas.drawLine(px - dot, py + dot, px + dot, py - dot, pen)
            }
        }
        if (showPeaks) {
            pen.color = Color.RED; pen.strokeWidth = dot * .7f
            peaks.forEach { (u, v) ->
                val (x, y) = xy(u to v)
                canvas.drawPoint(x * width, y * height, pen)
            }
        }
        if (showHist) {
            val left = 16f; val top = 15f; val histogramWidth = width.coerceAtMost(420) * .45f
            val histogramHeight = 74f
            pen.color = 0xB0000000.toInt()
            canvas.drawRoundRect(RectF(left, top, left + histogramWidth + 12f,
                top + histogramHeight + 10f), 8f, 8f, pen)
            val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE)
            val maximum = (hist.maxOfOrNull { h -> h.maxOrNull() ?: 0 } ?: 1).coerceAtLeast(1)
            hist.forEachIndexed { idx, bins ->
                pen.color = colors[idx]; pen.alpha = 210; pen.strokeWidth = 1.8f
                for (n in 1 until bins.size) {
                    canvas.drawLine(left + n * histogramWidth / 32f,
                        top + histogramHeight - histogramHeight * bins[n - 1] / maximum.toFloat(),
                        left + (n + 1) * histogramWidth / 32f,
                        top + histogramHeight - histogramHeight * bins[n] / maximum.toFloat(), pen)
                }
            }
        }
    }
}
