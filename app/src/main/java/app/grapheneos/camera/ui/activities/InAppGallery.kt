package app.grapheneos.camera.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.R
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Arrays

class InAppGallery : AppCompatActivity() {

    lateinit var gallerySlider: ViewPager2
    private lateinit var mediaFiles: Array<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery)

        gallerySlider = findViewById(R.id.gallery_slider)
        gallerySlider.setPageTransformer(GSlideTransformer())

        val parentFilePath = intent.extras?.getString("folder_path")!!
        val showVideosOnly = intent.extras?.getBoolean("show_videos_only")!!
        val parentDir = File(parentFilePath)

        if(intent.extras?.containsKey("fileSP") == true) {

            val spName = intent.extras?.getString("fileSP")

            val sp = getSharedPreferences(spName, Context.MODE_PRIVATE)

            val filePaths = sp.getStringSet("filePaths", emptySet())!!

            mediaFiles = filePaths.stream().map{ File(it) }.toArray { length ->
                arrayOfNulls<File>(length) }

        } else {
            mediaFiles = parentDir.listFiles { file: File ->
                if (!file.isFile) return@listFiles false
                val ext = file.extension

                if (showVideosOnly) {
                    ext == "mp4"
                } else {
                    ext == "jpg" || ext == "png" || ext == "mp4"
                }

            } ?: throw FileNotFoundException()
        }

        // Close gallery if no files are present
        if (mediaFiles.isEmpty()) {
            Toast.makeText(
                this,
                "Please capture a photo/video before trying to view" +
                        " them.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        // Make sure the latest one is first
        Arrays.sort(mediaFiles) { f1, f2 ->
            java.lang.Long.valueOf(
                f2.lastModified()
            ).compareTo(f1.lastModified())
        }

        val mediaFileList = ArrayList<File>()
        mediaFileList.addAll(mediaFiles)

        gallerySlider.adapter = GallerySliderAdapter(
            this,
            mediaFileList
        )

        val backButton: ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        val fIButton: ImageView = findViewById(R.id.file_info)
        fIButton.setOnClickListener {

            val file = getCurrentFile()

            val alertDialog: AlertDialog.Builder = AlertDialog.Builder(this)

            alertDialog.setTitle("File Details")

            val detailsBuilder = StringBuilder()

            detailsBuilder.append("File Name: \n")
            detailsBuilder.append(file.name)
            detailsBuilder.append("\n\n")

            detailsBuilder.append("File Path: \n")
            detailsBuilder.append(file.absolutePath)
            detailsBuilder.append("\n\n")

            detailsBuilder.append("File Size: \n")
            detailsBuilder.append(
                String.format(
                    "%.2f",
                    (file.length() / (1024f * 1024f))
                )
            )
            detailsBuilder.append(" mb\n\n")

            detailsBuilder.append("File Created On: \n")
            detailsBuilder.append(convertTime(file.lastModified()))

            alertDialog.setMessage(detailsBuilder)

            alertDialog.setPositiveButton("Ok", null)
            alertDialog.show()
        }

        val shareIcon: ImageView = findViewById(R.id.share_icon)
        shareIcon.setOnClickListener {

            val file = getCurrentFile()

            val share = Intent(Intent.ACTION_SEND)
            val values = ContentValues()
            val uri: Uri?

            if (file.extension == "mp4") {
                // Share video file
                share.type = "video/mp4"
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                uri = contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            } else {
                // Share image file
                share.type = "image/jpeg"
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            }

            val outStream: OutputStream? = contentResolver.openOutputStream(uri!!)
            outStream?.write(file.readBytes())
            outStream?.close()

            share.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(Intent.createChooser(share, "Share Image"))

        }

        val deleteIcon: ImageView = findViewById(R.id.delete_icon)
        deleteIcon.setOnClickListener {

            val file = getCurrentFile()

            AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("Do you really want to delete this file?")
                .setPositiveButton("Delete") { _, _ ->

                    Log.i("TAG", "File Name: ${file.name}")

                    if (file.delete()) {

                        (gallerySlider.adapter as GallerySliderAdapter)
                            .removeChildAt(gallerySlider.currentItem)

                        Toast.makeText(
                            this,
                            "File deleted successfully",
                            Toast.LENGTH_LONG
                        ).show()

                    } else {
                        Toast.makeText(
                            this,
                            "Unable to delete this file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null).show()


        }

        val editIcon: ImageView = findViewById(R.id.edit_icon)
        editIcon.setOnClickListener {

            val file = getCurrentFile()

            val editIntent = Intent(Intent.ACTION_EDIT)
            val values = ContentValues()
            val uri: Uri?

            if (file.extension == "mp4") {
                // Share video file
                editIntent.type = "video/mp4"
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                uri = contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            } else {
                // Share image file
                editIntent.type = "image/jpeg"
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            }

            val outStream: OutputStream? = contentResolver.openOutputStream(uri!!)
            outStream?.write(file.readBytes())
            outStream?.close()

            editIntent.putExtra(Intent.EXTRA_STREAM, uri)
            editIntent.data = uri
            editIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            editIntentLauncher.launch(
                Intent.createChooser(editIntent, "Edit Image")
            )
        }
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
                        getCurrentFile().writeBytes(it)
                        Toast.makeText(
                            this,
                            "Edit successful",
                            Toast.LENGTH_LONG
                        ).show()
                        recreate()
                        return@registerForActivityResult
                    }
                }

                Toast.makeText(
                    this,
                    "An unexpected error occurred after editing.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun getCurrentFile(): File {
        return (gallerySlider.adapter as GallerySliderAdapter)
            .getCurrentFile()
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: Long): String {
            val date = Date(time)
            val format: Format =
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            return format.format(date)
        }

        fun getCreationTimestamp(file: File): Long {
            val attr: BasicFileAttributes = Files.readAttributes(
                file.toPath(),
                BasicFileAttributes::class.java
            )
            return attr.creationTime().toMillis()
        }
    }
}