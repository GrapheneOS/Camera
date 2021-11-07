package app.grapheneos.camera.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.R
import app.grapheneos.camera.capturer.VideoCapturer
import com.google.android.material.snackbar.Snackbar
import java.io.OutputStream
import java.text.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class InAppGallery : AppCompatActivity() {

    lateinit var gallerySlider: ViewPager2
    private val mediaUris: ArrayList<Uri> = arrayListOf()
    private var snackBar : Snackbar? = null

    private val isSecureMode : Boolean
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

    companion object {
        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: Long): String {

            val date = Date(time)
            val format: Format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            return format.format(date)
        }

        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: String): String {
            val dateFormat = SimpleDateFormat("yyyyMMdd'T'hhmmss.SSS'Z'")
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = dateFormat.parse(time)
            return convertTime(parsedDate?.time ?: 0)
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
        if (isSecureMode){
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

                val res = contentResolver.delete(
                    mediaUri,
                    null,
                    null
                ) == 1

                if (res) {

                    showMessage(
                        "File deleted successfully"
                    )

                    (gallerySlider.adapter as GallerySliderAdapter).removeUri(mediaUri)

                } else {
                    showMessage(
                        "Unable to delete this file"
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

        val dateAdded : String?
        val dateModified : String?

        if (VideoCapturer.isVideo(mediaUri)) {

            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(
                this,
                mediaUri
            )

            val date =
                convertTime(
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

            dateAdded = eInterface.getAttribute(
                "DateTimeOriginal"
            )

            dateModified = eInterface.getAttribute(
                "DateTime"
            )

            iStream.close()
        }


        val alertDialog: AlertDialog.Builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

        alertDialog.setTitle("File Details")

        val detailsBuilder = StringBuilder()

        detailsBuilder.append("\nFile Name: \n")
        detailsBuilder.append(fileName)
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Path: \n")
        detailsBuilder.append("$relativePath$fileName")
        detailsBuilder.append("\n\n")

        detailsBuilder.append("File Size: \n")
        if(size==0){
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

        Log.i("TAG", "Date added: $dateAdded")

        detailsBuilder.append("File Created On: \n")
        if(dateAdded==null){
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateAdded)
        }

        detailsBuilder.append("\n\n")

        detailsBuilder.append("Last Modified On: \n")
        if(dateModified==null){
            detailsBuilder.append("Not found")
        } else {
            detailsBuilder.append(dateModified)
        }

        alertDialog.setMessage(detailsBuilder)

        alertDialog.setPositiveButton("Ok", null)


        alertDialog.show()
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
        setContentView(R.layout.gallery)

        supportActionBar?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setDisplayShowTitleEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        gallerySlider = findViewById(R.id.gallery_slider)
        gallerySlider.setPageTransformer(GSlideTransformer())

        val showVideosOnly = intent.extras?.getBoolean("show_videos_only")!!

        if (isSecureMode) {
            val spName = intent.extras?.getString("fileSP")
            val sp = getSharedPreferences(spName, Context.MODE_PRIVATE)
            val filePaths = sp.getStringSet("filePaths", emptySet())!!
            val mediaFileArray: Array<Uri> =
                filePaths.stream().map { Uri.parse(it) }.toArray { length ->
                    arrayOfNulls<Uri>(length)
                }

            mediaUris.addAll(mediaFileArray)
        } else {

            val cursor = contentResolver.query(
                CamConfig.fileCollectionUri,
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                ),
                null, null,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )

            if (cursor != null) {

                val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID)

                if (index >= 0) {

                    while (cursor.moveToNext()) {

                        val dName = cursor.getString(1)

                        val tUri = if (dName.endsWith(".mp4")) {
                            CamConfig.videoCollectionUri
                        } else {
                            if(showVideosOnly) continue
                            CamConfig.imageCollectionUri
                        }

                        val mediaUri = ContentUris
                            .withAppendedId(
                                tUri,
                                cursor.getInt(index).toLong()
                            )
                        mediaUris.add(mediaUri)
                    }

                }

                cursor.close()
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

    fun showMessage(msg: String) {
        snackBar?.setText(msg)
        snackBar?.show()
    }
}