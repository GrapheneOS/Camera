package app.grapheneos.camera.ui.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.GalleryBinding
import app.grapheneos.camera.util.storageLocationToUiString
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
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

    private val editIntentLauncher =
        registerForActivityResult(StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                saveEditedImage(result.data?.data)
            }
        }

    private fun unexpectedError() = showMessage(getString(R.string.editing_unexpected_error))

    private fun saveEditedImage(editingUri : Uri?) {
        val contentUri = editingUri ?: return unexpectedError()
        try {
            contentResolver.openInputStream(contentUri).use { inStream ->
                inStream?.readBytes()?.let { editedStream ->
                    contentResolver.openOutputStream(getCurrentItem().uri)?.use { outputStream ->
                        outputStream.write(editedStream)
                    }
                    showMessage(getString(R.string.edit_successfully))
                    recreate()
                }
            }
        } catch (ignored: SecurityException) {
            showMessage(getString(R.string.editing_permission_error))
        } catch (e: FileNotFoundException) {
            unexpectedError()
        }
    }

    private lateinit var rootView: View

    companion object {
        const val INTENT_KEY_SECURE_MODE = "is_secure_mode"
        const val INTENT_KEY_VIDEO_ONLY_MODE = "video_only_mode"
        const val INTENT_KEY_LIST_OF_SECURE_MODE_CAPTURED_ITEMS = "secure_mode_items"
        const val INTENT_KEY_LAST_CAPTURED_ITEM = "last_captured_item"

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

    private fun getCurrentItem(): CapturedItem {
        return gallerySliderAdapter!!.getCurrentItem()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery, menu)
        return super.onCreateOptionsMenu(menu)
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

        val curItem = getCurrentItem()

        val editIntent = Intent(Intent.ACTION_EDIT)

        editIntent.putExtra(Intent.EXTRA_STREAM, curItem.uri)
        editIntent.data = curItem.uri

        editIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (withDefault) {
            try {
                editIntentLauncher.launch(editIntent)
            } catch (ignored: ActivityNotFoundException) {
                showMessage(getString(R.string.no_editor_app_error))
            }
        } else {
            editIntentLauncher.launch(
                Intent.createChooser(
                     editIntent,
                     getString(R.string.edit_image)
                ).putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
            )
        }
    }

    private fun deleteCurrentMedia() {
        val curItem = getCurrentItem()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
                    gallerySliderAdapter!!.removeItem(curItem)
                } else {
                    showMessage(getString(R.string.deleting_unexpected_error))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

    }

    private fun showCurrentMediaDetails() {
        val curItem = getCurrentItem()

        var relativePath: String? = null
        var fileName: String? = null
        var size: Long = 0

        var dateAdded: String? = null
        var dateModified: String? = null

        try {
            // note that the first column (RELATIVE_PATH) is undefined for SAF Uris
            val projection = arrayOf(MediaColumns.RELATIVE_PATH, OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

            contentResolver.query(curItem.uri, projection, null,null)?.use {
                if (it.moveToFirst()) {
                    relativePath = it.getString(0)
                    fileName = it.getString(1)
                    size = it.getLong(2)
                }
            }

            if (fileName == null) {
                showMessage(getString(R.string.unable_to_obtain_file_details))
                return
            }

            if (curItem.type == ITEM_TYPE_VIDEO) {
                MediaMetadataRetriever().use {
                    it.setDataSource(this, curItem.uri)
                    dateAdded = convertTimeForVideo(it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)!!)
                    dateModified = dateAdded
                }
            } else {
                contentResolver.openInputStream(curItem.uri)?.use { stream ->
                    val eInterface = ExifInterface(stream)

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
            }
        } catch (e: Exception) {
            Log.d("showCurrentMediaDetails", "unable to obtain file details", e)
            showMessage(getString(R.string.unable_to_obtain_file_details))
            return
        }


        val alertDialog: AlertDialog.Builder =
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

        alertDialog.setTitle(getString(R.string.file_details))

        val detailsBuilder = StringBuilder()

        detailsBuilder.append("\n", getString(R.string.file_name_generic), "\n")
        detailsBuilder.append(fileName)
        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.file_path), "\n")
        detailsBuilder.append(getRelativePath(this, curItem.uri, relativePath, fileName!!))
        detailsBuilder.append("\n\n")

        detailsBuilder.append(getString(R.string.file_size), "\n")
        if (size == 0L) {
            detailsBuilder.append(getString(R.string.loading_generic))
        } else {
            detailsBuilder.append(
                String.format(
                    "%.2f",
                    (size / (1000f * 1000f))
                )
            )
            detailsBuilder.append(" MB")
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

    private fun shareCurrentMedia() {
        if (isSecureMode) {
            showMessage(getString(R.string.sharing_not_allowed))
            return
        }

        val curItem = getCurrentItem()

        val share = Intent(Intent.ACTION_SEND)
        share.putExtra(Intent.EXTRA_STREAM, curItem.uri)
        share.setDataAndType(curItem.uri, curItem.mimeType())
        share.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        startActivity(Intent.createChooser(share, getString(R.string.share_image)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isSecureMode = intent.getBooleanExtra(INTENT_KEY_SECURE_MODE, false)

        super.onCreate(savedInstanceState)

        if (isSecureMode) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        ogColor = ContextCompat.getColor(this, R.color.system_neutral1_900)
        binding = GalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        rootView = binding.rootView
        rootView.setOnClickListener {
            if (gallerySliderAdapter != null) {
                toggleActionBarState()
            }
        }

        gallerySlider = binding.gallerySlider
        snackBar = Snackbar.make(gallerySlider, "", Snackbar.LENGTH_LONG)
        gallerySlider.setPageTransformer(GSlideTransformer())

        val showVideosOnly = intent.getBooleanExtra(INTENT_KEY_VIDEO_ONLY_MODE, false)
        val listOfSecureModeCapturedItems = intent.getParcelableArrayListExtra<CapturedItem>(INTENT_KEY_LIST_OF_SECURE_MODE_CAPTURED_ITEMS)

        asyncLoaderOfCapturedItems.execute {
            val unprocessedItems: List<CapturedItem> = try {
                CapturedItems.get(this)
            } catch (e: InterruptedException) {
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

        val lastCapturedItem = intent.getParcelableExtra<CapturedItem>(INTENT_KEY_LAST_CAPTURED_ITEM)

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

            hideActionBar()
        }
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

        binding.placeholderText.root.visibility = View.GONE

        val existingAdapter = gallerySliderAdapter

        if (existingAdapter == null) {
            GallerySliderAdapter(this, items).let {
                gallerySliderAdapter = it
                gallerySlider.adapter = it
            }
        } else {
            val adapterItems = existingAdapter.items
            adapterItems.ensureCapacity(items.size)

            val preloadedItem = adapterItems[0]

            items.forEachIndexed { index, item ->
                // this check is needed to avoid showing preloaded item twice (it's not guaranteed
                // that it'll be first in the list)
                if (index > 50 || item != preloadedItem) {
                    adapterItems.add(item)
                }
            }
            existingAdapter.notifyItemRangeInserted(1, items.size - 1)
        }
        showActionBar()
    }

    fun toggleActionBarState() {
        supportActionBar?.let {
            if (it.isShowing) {
                hideActionBar()
            } else {
                showActionBar()
            }
        }
    }

    fun showActionBar() {
        supportActionBar?.let {
            it.show()
            animateBackgroundToOriginal()
        }
    }

    fun hideActionBar() {
        supportActionBar?.let {
            it.hide()
            animateBackgroundToBlack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        asyncLoaderOfCapturedItems.shutdownNow()
        asyncImageLoader.shutdownNow()
    }

    fun showMessage(msg: String) {
        snackBar.setText(msg)
        snackBar.show()
    }
}
