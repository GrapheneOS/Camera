package app.grapheneos.camera.ui.gallery

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.MediaTimestamp
import app.grapheneos.camera.data.media.repository.CapturedItemDetailsRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.databinding.GalleryBinding
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.activities.SecureActivity
import app.grapheneos.camera.ui.activities.editCapturedItem
import app.grapheneos.camera.ui.activities.shareCapturedItem
import app.grapheneos.camera.ui.common.navigation.VideoPlayerLauncher
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.getParcelableArrayListExtra
import app.grapheneos.camera.util.getParcelableExtra
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
open class GalleryActivity : AppCompatActivity() {

    @Inject
    lateinit var capturedItemDetailsRepository: CapturedItemDetailsRepository

    @Inject
    @MainImmediateDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var capturedItemRepository: CapturedItemRepository

    @Inject
    lateinit var videoPlayerLauncher: VideoPlayerLauncher

    lateinit var binding: GalleryBinding
    lateinit var gallerySlider: ViewPager2
    var gallerySliderAdapter: GallerySliderAdapter? = null

    val asyncLoaderOfCapturedItems = Executors.newSingleThreadExecutor()
    val asyncImageLoader = Executors.newSingleThreadExecutor()

    private lateinit var snackBar: Snackbar
    private var ogColor by Delegates.notNull<Int>()

    private lateinit var rootView: View

    private var lastViewedMediaItem: CapturedItem? = null

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    companion object {
        const val INTENT_KEY_VIDEO_ONLY_MODE = "video_only_mode"
        const val INTENT_KEY_LIST_OF_SECURE_MODE_CAPTURED_ITEMS = "secure_mode_items"
        const val INTENT_KEY_LAST_CAPTURED_ITEM = "last_captured_item"

        const val LAST_VIEWED_ITEM_KEY = "LAST_VIEWED_ITEM_KEY"

        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: Long, showTimeZone: Boolean = true): String {
            val date = Date(time)
            val format = SimpleDateFormat(
                if (showTimeZone) {
                    "yyyy-MM-dd HH:mm:ss z"
                } else {
                    "yyyy-MM-dd HH:mm:ss"
                }
            )
            format.timeZone = TimeZone.getDefault()
            return format.format(date)
        }

        fun getRelativePath(ctx: Context, uri: Uri, path: String?, fileName: String): String {
            if (path == null) {
                return storageLocationToUiString(ctx, uri.toString())
            }

            return "${ctx.getString(R.string.main_storage)}/$path$fileName"
        }
    }

    // Null while the async media scan is still running (or once the last item is gone)
    private fun getCurrentItem(): CapturedItem? {
        return gallerySliderAdapter?.getCurrentItem()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Every item is a media action; none of them apply until the scan has delivered items
        val hasMedia = gallerySliderAdapter != null
        for (i in 0 until menu.size) {
            menu[i].isVisible = hasMedia
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.edit_icon -> {
                editCurrentMedia()
                true
            }

            R.id.edit_with -> {
                editCurrentMedia(withDefault = false)
                true
            }

            R.id.delete_icon -> {
                deleteCurrentMedia()
                true
            }

            R.id.info -> {
                showCurrentMediaDetails()
                true
            }

            R.id.share_icon -> {
                shareCurrentMedia()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun editCurrentMedia(withDefault: Boolean = true) {
        if (this is SecureActivity) {
            showMessage(getString(R.string.edit_not_allowed))
            return
        }

        val curItem = getCurrentItem() ?: return

        editCapturedItem(this, curItem, useDefaultEditor = withDefault)?.let {
            showMessage(getString(it))
        }
    }

    private fun deleteCurrentMedia() {
        val curItem = getCurrentItem() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_description, curItem.uiName()))
            .setPositiveButton(R.string.delete) { _, _ ->
                var res = false

                val uri = curItem.uri
                try {
                    if (uri.authority == MediaStore.AUTHORITY) {
                        res = contentResolver.delete(uri, null, null) > 0
                    } else {
                        res = DocumentsContract.deleteDocument(contentResolver, uri)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (res) {
                    showMessage(getString(R.string.deleted_successfully))
                    gallerySliderAdapter?.removeItem(curItem)
                } else {
                    showMessage(getString(R.string.deleting_unexpected_error))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

    }

    private fun showCurrentMediaDetails() {
        val curItem = getCurrentItem() ?: return

        lifecycleScope.launch(mainDispatcher) {
            val details = capturedItemDetailsRepository.details(curItem)
            if (details == null) {
                showMessage(getString(R.string.unable_to_obtain_file_details))
            } else {
                showDetailsDialog(curItem, details)
            }
        }
    }

    private fun showDetailsDialog(curItem: CapturedItem, details: CapturedItemDetails) {
        val fileName = details.fileName
        val relativePath = details.relativePath
        val size = details.sizeBytes
        val resolution = details.resolution
        val duration = details.duration
        val dateAdded = details.created?.let(::formatTimestamp)
        val dateModified = details.modified?.let(::formatTimestamp)

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.file_details))

        val detailsBuilder = StringBuilder()

        detailsBuilder.append("\n", getString(R.string.file_name_generic), "\n")
        detailsBuilder.append(fileName)
        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.file_path), "\n")
        detailsBuilder.append(getRelativePath(this, curItem.uri, relativePath, fileName))
        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.file_size), "\n")
        detailsBuilder.append(
            size
                ?.let {
                    String.format(Locale.getDefault(), "%.2f MB", it / (1000f * 1000f))
                }
                ?: getString(R.string.not_found_generic)
        )

        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.resolution), "\n")
        detailsBuilder.append(resolution?.toString() ?: getString(R.string.not_found_generic))

        if (curItem.type == CapturedItemType.VIDEO) {
            detailsBuilder.append("\n\n")

            detailsBuilder.append(getString(R.string.duration), "\n")
            detailsBuilder.append(
                duration
                    ?.let { formatVideoDuration(it.inWholeSeconds) }
                    ?: getString(R.string.not_found_generic)
            )
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.file_created_on), "\n")
        if (dateAdded == null) {
            detailsBuilder.append(getString(R.string.not_found_generic))
        } else {
            detailsBuilder.append(dateAdded)
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.last_modified_on), "\n")
        if (dateModified == null) {
            detailsBuilder.append(getString(R.string.not_found_generic))
        } else {
            detailsBuilder.append(dateModified)
        }

        alertDialog.setMessage(detailsBuilder)

        alertDialog.setPositiveButton(getString(R.string.ok), null)


        alertDialog.show()
    }

    private fun formatTimestamp(timestamp: MediaTimestamp): String {
        return when (timestamp) {
            is MediaTimestamp.Absolute -> convertTime(timestamp.instant.toEpochMilli())
            // A name records wall-clock time and not the zone it was read in, so naming a zone for
            // it would assert the wrong one after the device travels
            is MediaTimestamp.WallClock -> convertTime(
                time = timestamp.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                showTimeZone = false,
            )
        }
    }

    private fun animateBackgroundToBlack() {

        val cBgColor = (rootView.background as ColorDrawable).color

        if (cBgColor == Color.BLACK) {
            return
        }

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            ogColor,
            Color.BLACK
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()
    }

    private fun animateBackgroundToOriginal() {

        val cBgColor = (rootView.background as ColorDrawable).color

        if (cBgColor == ogColor) {
            return
        }

        val bgColorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.BLACK,
            ogColor,
        )
        bgColorAnim.duration = 300
        bgColorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            this.rootView.setBackgroundColor(color)
        }
        bgColorAnim.start()
    }

    private fun animateShadeToTransparent() {
        if (binding.shade.alpha == 0f) {
            return
        }

        binding.shade.animate().apply {
            duration = 300
            alpha(0f)
        }
    }

    private fun animateShadeToOriginal() {
        if (binding.shade.alpha == 1f) {
            return
        }

        binding.shade.animate().apply {
            duration = 300
            alpha(1f)
        }
    }

    private fun shareCurrentMedia() {
        if (this is SecureActivity) {
            showMessage(getString(R.string.sharing_not_allowed))
            return
        }

        val curItem = getCurrentItem() ?: return

        shareCapturedItem(this, curItem)?.let {
            showMessage(getString(it))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            show(WindowInsetsCompat.Type.systemBars())
        }

        ogColor = ContextCompat.getColor(this, R.color.system_neutral1_900)
        binding = GalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        rootView = binding.rootView
        rootView.setOnClickListener {
            if (gallerySliderAdapter != null) {
                toggleUIState()
            }
        }

        gallerySlider = binding.gallerySlider
        snackBar = Snackbar.make(binding.snackbarAnchor, "", Snackbar.LENGTH_LONG)
        gallerySlider.setPageTransformer(GSlideTransformer())
        ViewCompat.setOnApplyWindowInsetsListener(binding.snackbarAnchor) { view, insets ->
            val systemBars =
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            view.y = -systemBars.bottom.toFloat()
            snackBar.anchorView = view
            insets
        }

        if (savedInstanceState != null) {
            lastViewedMediaItem = BundleCompat.getParcelable(
                savedInstanceState,
                LAST_VIEWED_ITEM_KEY,
                CapturedItem::class.java
            )
        }

        val intent = this.intent

        val showVideosOnly = intent.getBooleanExtra(INTENT_KEY_VIDEO_ONLY_MODE, false)
        val listOfSecureModeCapturedItems = getParcelableArrayListExtra<CapturedItem>(
            intent, INTENT_KEY_LIST_OF_SECURE_MODE_CAPTURED_ITEMS
        )

        asyncLoaderOfCapturedItems.execute {
            val unprocessedItems: List<CapturedItem> = try {
                runBlocking { capturedItemRepository.capturedItems() }
            } catch (_: InterruptedException) {
                // activity was destroyed and exectutor.shutdownNow() was called, which interrupts
                // executor threads
                return@execute
            }
            val setOfSecureModeCapturedItems = listOfSecureModeCapturedItems?.toHashSet()
            val items = ArrayList<CapturedItem>(unprocessedItems.size)

            unprocessedItems.forEach { item ->
                if (showVideosOnly) {
                    if (item.type != CapturedItemType.VIDEO) {
                        return@forEach
                    }
                }

                setOfSecureModeCapturedItems?.let {
                    if (!it.contains(item)) {
                        return@forEach
                    }
                }

                items.add(item)
            }
            items.sortByDescending { it.dateString }

            mainExecutor.execute { asyncResultReady(items) }
        }

        if (lastViewedMediaItem == null) {
            val lastCapturedItem =
                getParcelableExtra<CapturedItem>(intent, INTENT_KEY_LAST_CAPTURED_ITEM)

            if (lastCapturedItem != null) {
                val list = ArrayList<CapturedItem>()
                list.add(lastCapturedItem)
                GallerySliderAdapter(this, list).let {
                    gallerySliderAdapter = it
                    gallerySlider.adapter = it
                }
            } else {
                Handler(mainLooper).postDelayed({
                    if (gallerySliderAdapter == null) {
                        binding.placeholderText.root.visibility = View.VISIBLE
                    }
                }, 500)
            }
        }

        supportActionBar?.setBackgroundDrawable(null)

        ViewCompat.setOnApplyWindowInsetsListener(binding.shade) { view, insets ->
            val systemBars =
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val actionBarHeight = resources.getDimensionPixelSize(R.dimen.action_bar_height)
            view.layoutParams =
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    systemBars.top + actionBarHeight
                )
            view.background = ContextCompat.getDrawable(this@GalleryActivity, R.drawable.shade)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        showUI()
    }

    fun asyncResultReady(items: ArrayList<CapturedItem>) {
        if (isDestroyed) {
            return
        }

        if (items.isEmpty()) {
            Toast.makeText(applicationContext, R.string.empty_gallery, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        var capturedItemPosition = 0

        if (lastViewedMediaItem != null) {
            for (i in 0..<items.size) {
                val capturedItem = items[i]
                if (capturedItem == lastViewedMediaItem) {
                    capturedItemPosition = i
                }
            }
        }

        binding.placeholderText.root.visibility = View.GONE

        val existingAdapter = gallerySliderAdapter

        if (existingAdapter == null) {
            GallerySliderAdapter(this, items).let {
                gallerySliderAdapter = it
                gallerySlider.adapter = it
            }
            invalidateOptionsMenu()
        } else {
            val adapterItems = existingAdapter.items
            adapterItems.ensureCapacity(items.size)

            // At times the first item could get deleted before the others get
            // loaded especially on relatively slower devices, allowing null without throwing
            // an exception enables handling of such scenarios
            val preloadedItem = adapterItems.getOrNull(0)

            items.forEachIndexed { index, item ->
                // this check is needed to avoid showing preloaded item twice (it's not guaranteed
                // that it'll be first in the list)
                if (index > 50 || item != preloadedItem) {
                    adapterItems.add(item)
                }
            }
            existingAdapter.notifyItemRangeInserted(1, items.size - 1)
        }
        gallerySlider.setCurrentItem(capturedItemPosition, false)
        showUI()
    }

    fun toggleUIState() {
        supportActionBar?.let {
            if (it.isShowing) {
                hideUI()
            } else {
                showUI()
            }
        }
    }

    fun showUI() {
        supportActionBar?.let {
            it.show()
            animateBackgroundToOriginal()
        }
        animateShadeToOriginal()
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    fun hideUI() {
        supportActionBar?.let {
            it.hide()
            animateBackgroundToBlack()
        }
        animateShadeToTransparent()
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        asyncLoaderOfCapturedItems.shutdownNow()
        asyncImageLoader.shutdownNow()
    }

    fun vibrateDevice() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    fun showMessage(msg: String) {
        snackBar.setText(msg)
        snackBar.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        gallerySliderAdapter?.getCurrentItem()?.let {
            outState.putParcelable(LAST_VIEWED_ITEM_KEY, it)
        }
    }
}
