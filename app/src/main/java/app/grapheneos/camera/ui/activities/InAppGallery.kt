package app.grapheneos.camera.ui.activities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.ui.activities.MainActivity.Companion.camConfig
import com.google.android.material.snackbar.Snackbar
import java.io.OutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.properties.Delegates
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.lang.Exception
import java.util.Locale


class InAppGallery : AppCompatActivity() {

    lateinit var gallerySlider: ViewPager2
    private val mediaUris: ArrayList<Uri> = arrayListOf()
    private var snackBar: Snackbar? = null
    private var ogColor by Delegates.notNull<Int>()

    private val isSecureMode: Boolean
        get() {
            return intent.extras?.containsKey("fileSP") == true
        }

    private val editIntentLauncher =
        registerForActivityResult(StartActivityForResult())
        { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val contentUri: Uri? = result.data?.data

                if (contentUri != null) {
                    val inStream = contentResolver.openInputStream(
                        contentUri
                    )

                    inStream?.readBytes()?.let {

                        val outStream: OutputStream? =
                            contentResolver.openOutputStream(getCurrentUri())
                        outStream?.write(it)
                        outStream?.close()

                        showMessage("Edit successful")

                        recreate()
                        return@registerForActivityResult
                    }
                }

                showMessage("An unexpected error occurred after editing.")
            }
        }

    private lateinit var rootView: View

    companion object {
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

        fun getRelativePath(uri: Uri, path: String?, fileName: String): String {

            if (path == null) {
                val dPath = URLDecoder.decode(
                    uri.lastPathSegment,
                    "UTF-8"
                )

                val sType = dPath.substring(0, 7).replaceFirstChar {
                    it.uppercase()
                }

                val rPath = dPath.substring(8)

                return "($sType Storage) $rPath"
            }

            return "(Primary Storage) $path$fileName"
        }

    }

    private fun getCurrentUri(): Uri {
        return (gallerySlider.adapter as GallerySliderAdapter).getCurrentUri()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.gallery, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.edit_icon -> {
                editCurrentMedia()
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

    private fun editCurrentMedia() {
        if (isSecureMode) {
            showMessage(
                "Editing images in secure mode is not allowed."
            )
            return
        }

        val mediaUri = getCurrentUri()

        val editIntent = Intent(Intent.ACTION_EDIT)

        editIntent.putExtra(Intent.EXTRA_STREAM, mediaUri)
        editIntent.data = mediaUri

        editIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        editIntentLauncher.launch(
            Intent.createChooser(editIntent, "Edit Image")
        )
    }

    private fun deleteCurrentMedia() {

        val mediaUri = getCurrentUri()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Are you sure?")
            .setMessage("Do you really want to delete this file?")
            .setPositiveButton("Delete") { _, _ ->

                val res: Boolean

                if (mediaUri.authority == "media") {

                    res = contentResolver.delete(
                        mediaUri,
                        null,
                        null
                    ) == 1

                } else {
                    val doc = DocumentFile.fromSingleUri(
                        this,
                        mediaUri
                    )!!

                    res = doc.delete()
                }

                if (res) {
                    camConfig.removeFromGallery(mediaUri)
                    showMessage("File deleted successfully")
                    (gallerySlider.adapter as GallerySliderAdapter).removeUri(mediaUri)
                } else {
                    showMessage(
                        "An unexpected error occurred while deleting this" +
                                " file"
                    )
                }
            }
            .setNegativeButton("Cancel", null).show()

    }

    private fun showCurrentMediaDetails() {
        val mediaUri = getCurrentUri()

        val mediaCursor = contentResolver.query(
            mediaUri,
            arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            ),
            null,
            null,
        )

        if (mediaCursor?.moveToFirst() != true) {
            showMessage(
                "An unexpected error occurred"
            )

            mediaCursor?.close()
            return
        }

        val relativePath = mediaCursor.getString(0)
        val fileName = mediaCursor.getString(1)
        val size = mediaCursor.getInt(2)

        mediaCursor.close()

        var dateAdded: String? = null
        var dateModified: String? = null

        if (VideoCapturer.isVideo(mediaUri)) {

            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(
                this,
                mediaUri
            )

            val date =
                convertTimeForVideo(
                    mediaMetadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DATE
                    )!!
                )

            dateAdded = date
            dateModified = date

        } else {
            val iStream = contentResolver.openInputStream(
                mediaUri
            )
            val eInterface = ExifInterface(iStream!!)

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

            iStream.close()
        }


        val alertDialog: AlertDialog.Builder =
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

        alertDialog.setTitle("File Details")

        val detailsBuilder = StringBuilder()

        detailsBuilder.append("\nFile Name: \n")
        detailsBuilder.append(fileName)
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Path: \n")
        detailsBuilder.append(getRelativePath(mediaUri, relativePath, fileName))
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Size: \n")
        if (size == 0) {
            detailsBuilder.append("Loading...")
        } else {
            detailsBuilder.append(
                String.format(
                    "%.2f",
                    (size / (1024f * 1024f))
                )
            )
            detailsBuilder.append(" mb")
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Created On: \n")
        if (dateAdded == null) {
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateAdded)
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append("Last Modified On: \n")
        if (dateModified == null) {
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateModified)
        }

        alertDialog.setMessage(detailsBuilder)

        alertDialog.setPositiveButton("Ok", null)


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
            showMessage(
                "Sharing images in secure mode is not allowed."
            )
            return
        }

        val mediaUri = getCurrentUri()

        val share = Intent(Intent.ACTION_SEND)
        share.putExtra(Intent.EXTRA_STREAM, mediaUri)
        share.data = mediaUri
        share.type = if (VideoCapturer.isVideo(mediaUri)) {
            "video/*"
        } else {
            "image/*"
        }
        share.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        startActivity(
            Intent.createChooser(share, "Share Image")
        )

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val showVideosOnly = intent.extras?.getBoolean("show_videos_only")!!
        val isSecureMode = intent.extras?.getBoolean("is_secure_mode") == true

        if (isSecureMode) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        ogColor = ContextCompat.getColor(this, R.color.system_neutral1_900)

        setContentView(R.layout.gallery)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.appbar)))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        rootView = findViewById(R.id.root_view)
        rootView.setOnClickListener {
            toggleActionBarState()
        }

        gallerySlider = findViewById(R.id.gallery_slider)
        gallerySlider.setPageTransformer(GSlideTransformer())

        if (isSecureMode) {
            val spName = intent.extras?.getString("fileSP")
            val sp = getSharedPreferences(spName, Context.MODE_PRIVATE)
            val filePaths = sp.getString("filePaths", "")!!.split(",")
            val mediaFileArray: Array<Uri> =
                filePaths.stream().map { Uri.parse(it) }.toArray { length ->
                    arrayOfNulls<Uri>(length)
                }

            mediaUris.addAll(mediaFileArray)
        } else {

            if (showVideosOnly) {
                for (mediaUri in camConfig.mediaUris) {
                    if (VideoCapturer.isVideo(mediaUri)) {
                        mediaUris.add(mediaUri)
                    }
                }
            } else {
                mediaUris.addAll(camConfig.mediaUris)
            }


        }

        // Close gallery if no files are present
        if (mediaUris.isEmpty()) {
            showMessage(
                "Please capture a photo/video before trying to view" +
                        " them."
            )
            finish()
        }

        gallerySlider.adapter = GallerySliderAdapter(this, mediaUris)

        snackBar = Snackbar.make(
            gallerySlider,
            "",
            Snackbar.LENGTH_LONG
        )

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

    private fun uriExists(uri: Uri): Boolean {
        try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return false
            inputStream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onResume() {
        super.onResume()

        val gsaUris = (gallerySlider.adapter as GallerySliderAdapter).mediaUris

        if (isSecureMode) {

            val newUris: ArrayList<Uri> = arrayListOf()

            for (mediaUri in gsaUris) {
                if (uriExists(mediaUri)) {
                    newUris.add(mediaUri)
                }
            }

            // If mediaUris have changed
            if (mediaUris.size != newUris.size) {
                gallerySlider.adapter = GallerySliderAdapter(this, newUris)
            }

        } else {

            val newUris = camConfig.mediaUris
            var urisHaveChanged = false

            for (mediaUri in gsaUris) {
                if (!newUris.contains(mediaUri)) {
                    urisHaveChanged = true
                    break
                }
            }

            if (urisHaveChanged) {
                gallerySlider.adapter = GallerySliderAdapter(this, newUris)
            }
        }

        showActionBar()
    }

    fun showMessage(msg: String) {
        snackBar?.setText(msg)
        snackBar?.show()
    }
}
