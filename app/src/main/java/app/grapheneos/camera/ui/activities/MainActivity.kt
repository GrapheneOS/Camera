package app.grapheneos.camera.ui.activities

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import app.grapheneos.camera.R
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.databinding.ActivityMainBinding
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.qr.BarcodeFormats
import app.grapheneos.camera.ktx.applyPreviewRatio
import app.grapheneos.camera.ui.BottomTabLayout
import app.grapheneos.camera.ui.CaptureButton
import app.grapheneos.camera.ui.CountDownTimerUI
import app.grapheneos.camera.ui.CustomGrid
import app.grapheneos.camera.ui.QROverlay
import app.grapheneos.camera.ui.QRToggle
import app.grapheneos.camera.ui.SettingsDialog
import app.grapheneos.camera.ui.seekbar.ExposureBar
import app.grapheneos.camera.ui.seekbar.ZoomBar
import app.grapheneos.camera.ui.showMoreQrFormatOptions
import app.grapheneos.camera.ui.viewfinder.ViewfinderGestureHandler
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolder
import app.grapheneos.camera.ui.viewfinder.screen.PreviewFrameHolderImpl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffectHandler
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffectHandlerImpl
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewModel
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderViewRenderer
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.util.setBlurBitmapCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var cameraEntryPoint: CameraEntryPoint

    @Inject
    lateinit var barcodeFormats: BarcodeFormats

    @Inject
    lateinit var clipboardManager: ClipboardManager

    @Inject
    lateinit var vibrator: Vibrator

    internal lateinit var binding: ActivityMainBinding

    val viewfinder: ViewfinderViewModel by viewModels(
        extrasProducer = { viewfinderCreationExtras() },
    )

    internal val gestureHandler by lazy {
        ViewfinderGestureHandler(
            context = this,
            onAction = viewfinder::onAction,
        )
    }

    val gestureDetector: GestureDetector
        get() = gestureHandler.gestureDetector

    @set:VisibleForTesting
    lateinit var tunePlayer: TunePlayer

    lateinit var settingsDialog: SettingsDialog

    val threeButtons: View
        get() = binding.threeButtons

    val previewView: PreviewView
        get() = binding.preview

    val bottomOverlay: View
        get() = binding.bottomOverlay

    val rootView: View
        get() = binding.root

    val qrScanToggles: View
        get() = binding.qrScanToggles

    val qrToggle: QRToggle
        get() = binding.qrScanToggle

    val dmToggle: QRToggle
        get() = binding.dataMatrixToggle

    val cBToggle: QRToggle
        get() = binding.pdf417Toggle

    val azToggle: QRToggle
        get() = binding.aztecToggle

    val flipCameraCircle: View
        get() = binding.flipCameraCircle

    val cancelButtonView: ImageView
        get() = binding.cancelButton

    val tabLayout: BottomTabLayout
        get() = binding.cameraModeTabs

    val captureButton: CaptureButton
        get() = binding.captureButton

    val timerView: TextView
        get() = binding.timer

    val thirdOption: View
        get() = binding.thirdOption

    val imagePreview: ShapeableImageView
        get() = binding.imagePreview

    val previewLoader: ProgressBar
        get() = binding.previewLoading

    val zoomBar: ZoomBar
        get() = binding.zoomBar

    val zoomBarPanel: LinearLayout
        get() = binding.zoomBarPanel

    val exposureBar: ExposureBar
        get() = binding.exposureBar

    val exposureBarPanel: LinearLayout
        get() = binding.exposureBarPanel

    val qrOverlay: QROverlay
        get() = binding.qrOverlay

    val settingsIcon: ImageView
        get() = binding.settingsOption

    val mainOverlay: ImageView
        get() = binding.mainOverlay

    val previewGrid: CustomGrid
        get() = binding.previewGrid

    val cdTimer: CountDownTimerUI
        get() = binding.cTimer

    val cbText: TextView
        get() = binding.captureButtonText

    val cbCross: ImageView
        get() = binding.captureButtonCross

    val gCircleFrame: FrameLayout
        get() = binding.gCircleFrame

    val muteToggle: ShapeableImageView
        get() = binding.muteToggle

    val micOffIcon: ImageView
        get() = binding.micOff

    internal val previewFrames: PreviewFrameHolder by lazy {
        PreviewFrameHolderImpl(
            previewView = previewView,
            onLateFrame = ::showLateTransitionFrame,
        )
    }

    // Whether the transition still is standing in for the preview.
    private var transitionShown = false

    private var bottomNavigationBarPadding: Int = 0

    private lateinit var snackBar: Snackbar

    private val focusRingHandler: Handler = Handler(Looper.getMainLooper())

    private val focusRingCallback: Runnable = Runnable {
        binding.focusRing.visibility = View.INVISIBLE
    }

    // The blurred still that stands in for the preview whenever the camera is not streaming.
    private fun showPreviewTransition() {
        transitionShown = true
        previewGrid.visibility = View.INVISIBLE

        val lastFrame = previewFrames.lastFrame
        if (lastFrame == null || this is CaptureActivity) return

        // The still is of the camera being left behind, and the box under it takes the new camera's
        // aspect ratio partway through the switch. Cropping keeps it covering that box; the layout's
        // fitStart, which is there for the captured photo CaptureActivity shows in this same view,
        // would leave a bar down the side of the preview until the new camera streams.
        mainOverlay.scaleType = ImageView.ScaleType.CENTER_CROP
        setBlurBitmapCompat(mainOverlay, lastFrame)
        settingsIcon.visibility = View.INVISIBLE
        settingsIcon.isEnabled = false
        mainOverlay.visibility = View.VISIBLE
    }

    private fun showLateTransitionFrame() {
        if (transitionShown) {
            showPreviewTransition()
        }
    }

    private fun hidePreviewTransition() {
        transitionShown = false
        mainOverlay.visibility = View.INVISIBLE

        if (viewfinder.uiState.value.isQrMode) {
            return
        }

        previewGrid.visibility = View.VISIBLE
        if (!settingsDialog.isShowing) {
            settingsIcon.visibility = View.VISIBLE
        }

        settingsIcon.isEnabled = true
    }

    // Not from the strip's own touch listener: a tab view takes the DOWN, so the strip is only
    // handed a gesture once the scroll view has taken it back off the tab -- by which point a
    // switch started by a plain tap has already happened. The freshness window keeps a touch that
    // switches nothing from costing more than one copy every couple of seconds.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            previewFrames.prefetch()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewfinder.onAction(LifecycleAction.ScreenInteracted)
    }

    fun animateFocusRing(x: Float, y: Float) {
        // Move the focus ring so that its center is at the tap location (x, y)
        val width = binding.focusRing.width.toFloat()
        binding.focusRing.updateLayoutParams<ConstraintLayout.LayoutParams> {
            updateMargins(
                left = (x - width / 2).roundToInt(),
                top = (y - width / 2).roundToInt()
            )
        }

        // Show focus ring
        binding.focusRing.visibility = View.VISIBLE
        binding.focusRing.alpha = 1f

        if (areSystemAnimationsEnabled()) {
            // Animate the focus ring to disappear
            binding.focusRing.animate()
                .setStartDelay(500)
                .setDuration(300)
                .alpha(0f)
                .setListener(object : Animator.AnimatorListener {

                    var isCancelled = false

                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animator: Animator) {
                        if (!isCancelled) {
                            binding.focusRing.visibility = View.INVISIBLE
                        }

                        isCancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        isCancelled = true
                    }

                    override fun onAnimationRepeat(animation: Animator) {}
                }).start()
        } else {
            focusRingHandler.removeCallbacks(focusRingCallback)
            focusRingHandler.postDelayed(focusRingCallback, 800)
        }
    }

    private fun areSystemAnimationsEnabled(): Boolean {
        val duration: Float = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        val transition: Float = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )

        return duration != 0f && transition != 0f
    }

    fun animateLensSwitch() {
        val rotation = when {
            binding.flipCameraIcon.rotation < 180 -> 180f
            else -> 360f
        }

        val rotate = RotateAnimation(
            0f,
            rotation,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
        )
        rotate.duration = LENS_SWITCH_ANIMATION_DURATION
        rotate.interpolator = LinearInterpolator()

        flipCameraCircle.startAnimation(rotate)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_CAMERA,
            -> {
                tabLayout.settleNow()
                viewfinder.onAction(CaptureAction.CaptureKeyPressed)
            }
            KeyEvent.KEYCODE_FOCUS -> {
                viewfinder.onAction(CameraAction.FocusKeyPressed)
            }
            KeyEvent.KEYCODE_ZOOM_IN -> {
                viewfinder.onAction(CameraAction.ZoomInKeyPressed)
            }
            KeyEvent.KEYCODE_ZOOM_OUT -> {
                viewfinder.onAction(CameraAction.ZoomOutKeyPressed)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Pretend as if the event was handled by the app (avoid volume bar from appearing)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        viewfinder.onAction(LifecycleAction.ScreenResumed)
    }

    protected open val outputUri: Uri?
        get() {
            return null
        }

    private fun selectBarcodeFormatToggles() {
        val toggles = mapOf(
            BarcodeFormat.QR_CODE to qrToggle,
            BarcodeFormat.AZTEC to azToggle,
            BarcodeFormat.PDF_417 to cBToggle,
            BarcodeFormat.DATA_MATRIX to dmToggle,
        )

        barcodeFormats.enabled.forEach { format ->
            toggles[format]?.isSelected = true
        }
    }

    override fun onPause() {
        super.onPause()

        // Leaving a mode switch waiting on an animation that will never finish would strand the
        // strip on a mode the camera never entered.
        tabLayout.settleNow()

        // The countdown would otherwise keep ticking while the app is in the background and fire a
        // capture into a camera that has already been unbound.
        cdTimer.cancelTimer()
        viewfinder.onAction(LifecycleAction.ScreenPaused)
        previewFrames.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        snackBar = Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG)

        val renderer = ViewfinderViewRenderer(
            activity = this,
        )
        val effectHandler: ViewfinderEffectHandler = ViewfinderEffectHandlerImpl(
            activity = this,
            clipboardManager = clipboardManager,
            vibrator = vibrator,
            onAction = viewfinder::onAction,
        )
        viewfinder.onAction(
            LifecycleAction.ScreenCreated(
                host = ViewfinderHost(
                    previewTarget = PreviewTarget(
                        lifecycleOwner = this,
                        surfaceProvider = previewView.surfaceProvider,
                        meteringPointFactory = previewView.meteringPointFactory,
                    ),
                    previewFrames = previewFrames,
                    thumbnailSize = ThumbnailSize(
                        width = imagePreview.layoutParams.width,
                        height = imagePreview.layoutParams.height,
                    ),
                ),
            ),
        )

        viewfinder.onAction(CameraAction.DisplayRotationChanged(rotation = displayRotation()))

        tunePlayer = TunePlayer(
            context = this,
            soundsEnabled = { viewfinder.uiState.value.capture.cameraSounds },
        )

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.uiState.collect { state ->
                    renderer.render(state)
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.zoomUiState.collect { zoom ->
                    renderer.renderZoom(zoom)
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.levelUiState.collect { level ->
                    renderer.renderLevel(level)
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewfinder.effects.collect { effect ->
                    effectHandler.handle(effect)
                }
            }
        }

        previewView.scaleType = PreviewView.ScaleType.FIT_START

        tabLayout.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                val tab = tabLayout.getTabAtX(tabLayout.scrollX)
                finalizeMode(tab)
                return@setOnTouchListener true
            }

            return@setOnTouchListener false
        }

        previewView.previewStreamState.observe(this) { state: StreamState ->
            if (state == StreamState.STREAMING) {
                hidePreviewTransition()
                viewfinder.onAction(LifecycleAction.PreviewStreamingStarted)
            } else {
                showPreviewTransition()
            }
        }

        var tapDownTimestamp: Long = 0
        flipCameraCircle.setOnTouchListener { _, event ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (tapDownTimestamp == 0L) {
                        tapDownTimestamp = System.currentTimeMillis()
                        flipCameraCircle.animate().scaleXBy(0.05f).setDuration(300).start()
                        flipCameraCircle.animate().scaleYBy(0.05f).setDuration(300).start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dif = System.currentTimeMillis() - tapDownTimestamp
                    if (dif < 300) {
                        flipCameraCircle.performClick()
                    }

                    tapDownTimestamp = 0
                    flipCameraCircle.animate().cancel()
                    flipCameraCircle.animate().scaleX(1f).setDuration(300).start()
                    flipCameraCircle.animate().scaleY(1f).setDuration(300).start()
                }
                else -> {
                }
            }
            true
        }
        flipCameraCircle.setOnClickListener {
            viewfinder.onAction(CameraAction.FlipCameraClicked)
        }

        binding.thirdCircle.setOnClickListener {
            viewfinder.onAction(CaptureAction.ThirdCircleClicked)
        }

        binding.thirdCircle.setOnLongClickListener {
            viewfinder.onAction(CaptureAction.ThirdCircleLongClicked)
            return@setOnLongClickListener true
        }

        captureButton.setOnClickListener {
            // A mode the strip is still settling into has not reached the camera yet, and this
            // would otherwise capture in the mode being left behind.
            tabLayout.settleNow()

            viewfinder.onAction(CaptureAction.CaptureButtonClicked)
        }

        zoomBar.setMainActivity(this)
        exposureBar.setMainActivity(this)

        settingsIcon.setOnClickListener {
            viewfinder.onAction(SettingsAction.SettingsIconClicked)
        }

        settingsIcon.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val displayCutout = window.decorView.rootWindowInsets.displayCutout
                val layoutParams = (settingsIcon.layoutParams as RelativeLayout.LayoutParams)

                val rect = if (displayCutout?.boundingRects?.isNotEmpty() == true) {
                    displayCutout.boundingRects.first()
                } else {
                    null
                }

                val windowsSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    windowManager.currentWindowMetrics.bounds
                } else {
                    val size = Point()
                    // defaultDisplay isn't deprecated below API 30 as highlighted by the IDE
                    // and this code would only execute if it is (Hint: enclosing if-block)
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealSize(size)
                    Rect(0, 0, size.x, size.y)
                }

                if (rect == null || rect.left <= 0 || rect.right == windowsSize.right) {
                    layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
                } else {
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                }
            }
        })

        var isInsetSet = false

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
            )

            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).let {
                it.setMargins(
                    insets.left,
                    0,
                    insets.right,
                    0,
                )

                it
            }

            zoomBarPanel.setPadding(0, 0, 0, insets.bottom)
            exposureBarPanel.setPadding(0, 0, 0, insets.bottom)
            bottomNavigationBarPadding = insets.bottom

            if (insets.top != 0 && !isInsetSet) {
                binding.mainFrame.layoutParams =
                    (binding.mainFrame.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            (8 * resources.displayMetrics.density.toInt()) + insets.top,
                            it.rightMargin,
                            it.bottomMargin,
                        )

                        it
                    }

                qrScanToggles.layoutParams =
                    (qrScanToggles.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            (16 * resources.displayMetrics.density.toInt()) +
                                insets.top,
                            it.rightMargin,
                            it.bottomMargin,
                        )

                        it
                    }

                isInsetSet = true
            }

            WindowInsetsCompat.CONSUMED
        }

        setContentView(binding.getRoot())

        WindowInsetsControllerCompat(window, rootView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        enableEdgeToEdge()

        cdTimer.setMainActivity(this)

        val themedContext = DynamicColors.wrapContextIfAvailable(this, R.style.Theme_SettingsDialog)
        settingsDialog = SettingsDialog(this, themedContext)

        previewView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    previewView.viewTreeObserver.removeOnPreDrawListener(this)
                    repositionTabLayout()
                    return true
                }
            }
        )

        binding.moreOptions.setOnClickListener {
            showMoreQrFormatOptions(
                activity = this,
                barcodeFormats = barcodeFormats,
            )
        }

        qrToggle.mActivity = this
        qrToggle.key = BarcodeFormat.QR_CODE.name

        dmToggle.mActivity = this
        dmToggle.key = BarcodeFormat.DATA_MATRIX.name

        cBToggle.mActivity = this
        cBToggle.key = BarcodeFormat.PDF_417.name

        azToggle.mActivity = this
        azToggle.key = BarcodeFormat.AZTEC.name

        selectBarcodeFormatToggles()

        settingsDialog.loadInitialState()

        muteToggle.setOnClickListener {
            viewfinder.onAction(RecordingAction.MuteToggleClicked)
        }
    }

    private fun repositionTabLayout() {
        threeButtons.visibility = View.VISIBLE

        tabLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                tabLayout.viewTreeObserver
                    .removeOnPreDrawListener(
                        this
                    )

                val previewHeight169 = binding.previewContainer.width * 16 / 9

                val extraHeight169 = binding.previewContainer.height -
                    previewHeight169 -
                    tabLayout.height -
                    10 * resources.displayMetrics.density.toInt()

                // When there's no extra space in 16:9 for even the bottom nav bar to be present without
                // obscuring the preview or if there's sufficient space for the entire bottom UI to exist
                val shouldSnapAboveBottomNav = extraHeight169 < bottomNavigationBarPadding ||
                    extraHeight169 >=
                    (threeButtons.height + tabLayout.height + tabLayout.marginTop)

                tabLayout.layoutParams =
                    (tabLayout.layoutParams as ViewGroup.MarginLayoutParams).let {
                        it.setMargins(
                            it.leftMargin,
                            it.topMargin,
                            it.rightMargin,
                            if (shouldSnapAboveBottomNav) {
                                bottomNavigationBarPadding
                            } else {
                                extraHeight169
                            }
                        )

                        it
                    }

                return true
            }
        })
    }

    fun finalizeMode(tab: TabLayout.Tab? = null) {
        // The strip is untouchable during a recording but not while its start sound still plays, and
        // rebinding the camera there starts the queued recording on a dead recorder. The touch may
        // already have dragged the strip, so put it back on the mode the camera is really in.
        if (viewfinder.uiState.value.isRecordingActive) {
            tabLayout.getTabForMode(viewfinder.uiState.value.mode)?.let {
                tabLayout.goToTab(it)
            }
            return
        }

        val selectedTab = tab ?: tabLayout.selectedTab
        if (selectedTab != null) {
            val mode = selectedTab.tag as CameraMode

            // Blurred while the strip is still gliding, not once the camera has gone: the rebind
            // holds the main thread for half a second, so a transition left to the stream state
            // would only reach the screen after the wait it is there to explain. Guarded on the
            // mode really changing, since nothing would rebind to take it back down again.
            if (mode != viewfinder.uiState.value.mode) {
                showPreviewTransition()
            }

            // switchMode() puts the strip on the mode the camera actually ended up in, which is a
            // different one when an extension fails to bind.
            tabLayout.goToTab(selectedTab) {
                if (mode != viewfinder.uiState.value.mode) {
                    viewfinder.onAction(CameraAction.ModeSelected(mode))
                } else if (
                    transitionShown &&
                    previewView.previewStreamState.value == StreamState.STREAMING
                ) {
                    // A transition raised for a switch this tap has just cancelled, over a camera
                    // that never stopped streaming: nothing is left to rebind and take it down.
                    hidePreviewTransition()
                }
            }
        }
    }

    private fun viewfinderCreationExtras(): CreationExtras {
        val defaults = defaultViewModelCreationExtras
        val arguments = Bundle().apply {
            defaults[DEFAULT_ARGS_KEY]?.let(::putAll)
            putAll(ViewfinderViewModel.arguments(cameraEntryPoint))
        }

        return MutableCreationExtras(defaults)
            .apply { set(DEFAULT_ARGS_KEY, arguments) }
            .withCreationCallback<ViewfinderViewModel.Factory> { factory ->
                factory.create(outputUri = outputUri)
            }
    }

    fun showMessage(@StringRes message: Int) {
        showMessage(getString(message))
    }

    fun showMessage(
        @StringRes msg: Int,
        action: String?,
        callback: View.OnClickListener?,
    ) {
        showMessage(getString(msg), action, callback)
    }

    /**
     * The icon in the left circle stands for whatever [flipCameraCircle] does right now — flip
     * the camera, pause/resume the recording, or toggle scanning of every barcode format. Swap
     * its description together with its drawable so that it is never announced as the wrong
     * button.
     */
    fun setFlipCameraIcon(@DrawableRes icon: Int, @StringRes description: Int) {
        binding.flipCameraIconContent.setImageResource(icon)
        binding.flipCameraIconContent.contentDescription = getString(description)
    }

    /**
     * `thirdCircle` is the view that carries the click listener, so it is the one that has to be
     * described: it opens the gallery, except while a video is being recorded, when it takes a
     * still instead.
     */
    fun setThirdCircleIcon(@DrawableRes icon: Int, @StringRes description: Int) {
        binding.thirdCircle.setImageResource(icon)
        binding.thirdCircle.contentDescription = getString(description)
    }

    /**
     * The mute toggle signals its state through an icon, a background colour and a tooltip, and a
     * tooltip is supplementary text rather than a view's label, so the state was never described
     * to accessibility services at all. Set all four together here so they cannot drift apart.
     */
    fun setMuteToggleState(muted: Boolean) {
        muteToggle.setImageResource(if (muted) R.drawable.mic_off else R.drawable.mic_on)
        muteToggle.setBackgroundColor(
            if (muted) getColor(android.R.color.darker_gray) else getColor(R.color.red)
        )
        muteToggle.tooltipText = getString(
            if (muted) R.string.tap_to_unmute_audio else R.string.tap_to_mute_audio
        )
        muteToggle.contentDescription = getString(
            if (muted) R.string.unmute_audio else R.string.mute_audio
        )
    }

    fun showMessage(message: String) {
        showMessage(message, action = null, callback = null)
    }

    fun showMessage(msg: String, action: String?, callback: View.OnClickListener?) {
        snackBar.apply {
            setText(msg)
            setAction(action, callback)
            show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // The activity declares configChanges for orientation, so nothing else refreshes
        // rotation-dependent state.
        // The preview follows the window; the capture use cases follow the sensor.
        viewfinder.onAction(CameraAction.DisplayRotationChanged(rotation = displayRotation()))

        val state = viewfinder.uiState.value
        state.sensorOrientationDegrees?.let {
            previewView.applyPreviewRatio(
                aspectRatio = state.aspectRatio,
                sensorOrientationDegrees = it,
            )
        }
    }

    private fun displayRotation(): Int {
        @Suppress("DEPRECATION")
        val defaultDisplayRotation = windowManager.defaultDisplay.rotation

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                display?.rotation ?: defaultDisplayRotation
            }

            else -> defaultDisplayRotation
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewFrames.release()
        viewfinder.onAction(LifecycleAction.ScreenDestroyed)
    }

    override fun onStart() {
        super.onStart()
        viewfinder.onAction(LifecycleAction.ScreenStarted)
    }

    override fun onStop() {
        viewfinder.onAction(LifecycleAction.ScreenStopped)
        // Stop explicitly rather than letting the unbind tear the recording down for us.
        if (viewfinder.uiState.value.isRecordingActive) {
            previewFrames.holdCurrentFrame()
            viewfinder.onAction(RecordingAction.RecordingStopRequested)
        }
        super.onStop()
    }

    private companion object {
        const val LENS_SWITCH_ANIMATION_DURATION = 400L
    }
}
