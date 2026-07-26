package app.grapheneos.camera.ui.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
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
import androidx.viewpager2.widget.ViewPager2
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.AutoFinishOnSleep
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.GalleryBinding
import app.grapheneos.camera.editCapturedItem
import app.grapheneos.camera.shareCapturedItem
import app.grapheneos.camera.util.formatVideoDuration
import app.grapheneos.camera.util.getParcelableArrayListExtra
import app.grapheneos.camera.util.getParcelableExtra
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.properties.Delegates

class InAppGallery : AppCompatActivity() {

    lateinit var binding: GalleryBinding
    lateinit var gallerySlider: ViewPager2
    var gallerySliderAdapter: GallerySliderAdapter? = null

    val asyncLoaderOfCapturedItems = Executors.newSingleThreadExecutor()
    val asyncImageLoader = Executors.newSingleThreadExecutor()

    private lateinit var snackBar: Snackbar
    private var ogColor by Delegates.notNull<Int>()

    var isSecureMode = false
        private set

    private lateinit var rootView: View

    private val autoFinisher = AutoFinishOnSleep(this)

    private var lastViewedMediaItem: CapturedItem? = null

    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    companion object {
        const val INTENT_KEY_SECURE_MODE = "is_secure_mode"
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

        fun convertTimeForVideo(time: String): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = dateFormat.parse(time)
            return convertTime(parsedDate?.time ?: 0)
        }

        fun convertTimeForPhoto(time: String, offset: String? = null): String {

            val timestamp = if (offset != null) {
                "$time $offset"
            } else {
                time
            }

            val dateFormat = SimpleDateFormat(
                if (offset == null) {
                    "yyyy:MM:dd HH:mm:ss"
                } else {
                    "yyyy:MM:dd HH:mm:ss Z"
                }, Locale.US
            )

            if (offset == null) {
                dateFormat.timeZone = TimeZone.getDefault()
            }
            val parsedDate = dateFormat.parse(timestamp)
            return convertTime(parsedDate?.time ?: 0, offset != null)
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
        if (isSecureMode) {
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

    /**
     * A provider is free to ignore the projection it was handed and answer with its own columns
     * -- the DocumentsProvider contract explicitly allows it -- so reading by position can hand
     * back an unrelated column, e.g. a document id that then renders as a 1970 date. Look the
     * column up by name instead and treat "absent" the same as "not set".
     */
    private fun Cursor.optionalLong(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    private fun Cursor.optionalString(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    /**
     * Best-effort creation and modification timestamps, in milliseconds, for media whose Exif
     * has been stripped. MediaStore and the Storage Access Framework disagree both on the column
     * names and on the unit, and each provider rejects the columns it does not know, so the two
     * are queried separately and every failure just leaves the value unknown.
     */
    private fun queryStoredTimestamps(uri: Uri): Pair<Long?, Long?> {
        var created: Long? = null
        var modified: Long? = null

        try {
            val projection = arrayOf(MediaColumns.DATE_ADDED, MediaColumns.DATE_MODIFIED)
            contentResolver.query(uri, projection, null, null)?.use {
                if (it.moveToFirst()) {
                    // Seconds since the epoch, and 0 stands for "not set"
                    created = it.optionalLong(MediaColumns.DATE_ADDED)
                        ?.takeIf { seconds -> seconds > 0 }?.times(1000L)
                    modified = it.optionalLong(MediaColumns.DATE_MODIFIED)
                        ?.takeIf { seconds -> seconds > 0 }?.times(1000L)
                }
            }
        } catch (e: Exception) {
            Log.d("queryStoredTimestamps", "no MediaStore timestamps from ${uri.authority}", e)
        }

        if (modified == null) {
            try {
                val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                contentResolver.query(uri, projection, null, null)?.use {
                    if (it.moveToFirst()) {
                        // Already in milliseconds here
                        modified = it.optionalLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            ?.takeIf { millis -> millis > 0 }
                    }
                }
            } catch (e: Exception) {
                Log.d("queryStoredTimestamps", "no document timestamp from ${uri.authority}", e)
            }
        }

        return Pair(created, modified)
    }

    /**
     * The dimensions a photo presents at, which is the stored frame with its Exif orientation
     * applied. Only the header is read, so the full bitmap is never decoded.
     */
    private fun readImageResolution(uri: Uri, orientation: Int): Size? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) {
            return null
        }

        val quarterTurned = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE

        return if (quarterTurned) Size(height, width) else Size(width, height)
    }

    private fun showCurrentMediaDetails() {
        val curItem = getCurrentItem() ?: return

        var relativePath: String? = null
        var fileName: String? = null
        // Absent rather than zero: a provider that reports no size at all used to be rendered as
        // "Loading…", which is not what was happening and never resolved
        var size: Long? = null

        var dateAdded: String? = null
        var dateModified: String? = null

        var resolution: Size? = null
        var durationMillis: Long? = null

        try {
            // note that RELATIVE_PATH is undefined for SAF Uris, which the by-name lookups below
            // report as absent rather than as whatever column happened to land in that position
            val projection = arrayOf(
                MediaColumns.RELATIVE_PATH,
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            )

            contentResolver.query(curItem.uri, projection, null, null)?.use {
                if (it.moveToFirst()) {
                    relativePath = it.optionalString(MediaColumns.RELATIVE_PATH)
                    fileName = it.optionalString(OpenableColumns.DISPLAY_NAME)
                    size = it.optionalLong(OpenableColumns.SIZE)
                }
            }

            if (fileName == null) {
                showMessage(getString(R.string.unable_to_obtain_file_details))
                return
            }

            if (curItem.type == ITEM_TYPE_VIDEO) {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(this, curItem.uri)
                    // Not every container carries a creation date, and one that is missing or
                    // malformed must not take the whole dialog down with it
                    val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (date != null) {
                        try {
                            dateAdded = convertTimeForVideo(date)
                            dateModified = dateAdded
                        } catch (e: ParseException) {
                            Log.d("showCurrentMediaDetails", "unparseable video date: $date", e)
                        }
                    }

                    durationMillis = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.takeIf { millis -> millis > 0 }

                    val width = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()

                    val height = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()

                    if (width != null && height != null && width > 0 && height > 0) {
                        // A track holds its frames as encoded plus the rotation a player has to
                        // apply, so a recording taken in portrait is stored landscape
                        val rotation = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                            ?.toIntOrNull() ?: 0
                        resolution = if (rotation == 90 || rotation == 270) {
                            Size(height, width)
                        } else {
                            Size(width, height)
                        }
                    }
                }
            } else {
                var orientation = ExifInterface.ORIENTATION_NORMAL

                contentResolver.openInputStream(curItem.uri)?.use { stream ->
                    val eInterface = ExifInterface(stream)

                    orientation = eInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )

                    val offset = eInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME)

                    if (eInterface.hasAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) {
                        dateAdded = convertTimeForPhoto(
                            eInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)!!,
                            offset
                        )
                    }

                    if (eInterface.hasAttribute(ExifInterface.TAG_DATETIME)) {
                        dateModified = convertTimeForPhoto(
                            eInterface.getAttribute(ExifInterface.TAG_DATETIME)!!,
                            offset
                        )
                    }
                }

                resolution = readImageResolution(curItem.uri, orientation)
            }

            // Photos carry no Exif dates when "Remove Exif data after capture" is on, which is
            // the default, so fall back to the timestamps the storage layer keeps.
            if (dateAdded == null || dateModified == null) {
                val (created, modified) = queryStoredTimestamps(curItem.uri)
                if (dateAdded == null && created != null) {
                    dateAdded = convertTime(created)
                }
                if (dateModified == null && modified != null) {
                    dateModified = convertTime(modified)
                }
            }

            // Neither source has a creation time to give for a stripped photo kept through the
            // Storage Access Framework, but the app named the file after the moment it captured it.
            // A name records wall-clock time and not the zone it was read in, so this one is shown
            // without a zone: after the device travels, the digits are still the time the camera
            // showed at capture, but naming a zone for them would assert the wrong one.
            if (dateAdded == null) {
                curItem.captureTime()?.let { dateAdded = convertTime(it, showTimeZone = false) }
            }
        } catch (e: Exception) {
            Log.d("showCurrentMediaDetails", "unable to obtain file details", e)
            showMessage(getString(R.string.unable_to_obtain_file_details))
            return
        }


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

        if (curItem.type == ITEM_TYPE_VIDEO) {
            detailsBuilder.append("\n\n")

            detailsBuilder.append(getString(R.string.duration), "\n")
            detailsBuilder.append(
                durationMillis
                    ?.let { formatVideoDuration(it / 1000) }
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
        if (isSecureMode) {
            showMessage(getString(R.string.sharing_not_allowed))
            return
        }

        val curItem = getCurrentItem() ?: return

        if (!shareCapturedItem(this, curItem)) {
            showMessage(getString(R.string.unable_to_share_media))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isSecureMode = intent.getBooleanExtra(INTENT_KEY_SECURE_MODE, false)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            show(WindowInsetsCompat.Type.systemBars())
        }

        if (isSecureMode) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            autoFinisher.start()
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
                CapturedItems.get(this)
            } catch (_: InterruptedException) {
                // activity was destroyed and exectutor.shutdownNow() was called, which interrupts
                // executor threads
                return@execute
            }
            val setOfSecureModeCapturedItems = listOfSecureModeCapturedItems?.toHashSet()
            val items = ArrayList<CapturedItem>(unprocessedItems.size)

            unprocessedItems.forEach { item ->
                if (showVideosOnly) {
                    if (item.type != ITEM_TYPE_VIDEO) {
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
            view.background = ContextCompat.getDrawable(this@InAppGallery, R.drawable.shade)
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
        if (isSecureMode) {
            autoFinisher.stop()
        }
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
