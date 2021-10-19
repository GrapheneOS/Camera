package app.grapheneos.camera.ui.activities

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.GSlideTransformer
import app.grapheneos.camera.GallerySliderAdapter
import java.io.File
import java.io.FileNotFoundException
import java.util.*

import androidx.appcompat.app.AlertDialog
import app.grapheneos.camera.R
import java.io.OutputStream
import java.text.Format
import java.text.SimpleDateFormat

class InAppGallery: AppCompatActivity() {

    private lateinit var gallerySlider: ViewPager2
    private lateinit var mediaFiles: Array<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery)

        gallerySlider = findViewById(R.id.gallery_slider)
        gallerySlider.setPageTransformer(GSlideTransformer())

        val parentFilePath = intent.extras?.getString("folder_path")!!
        val parentDir = File(parentFilePath)

        mediaFiles = parentDir.listFiles { file: File ->
            if (!file.isFile) return@listFiles false
            val ext = file.extension
            ext == "jpg" || ext == "png" || ext == "mp4"
        } ?: throw FileNotFoundException()

        // Make sure the latest one is first
        Arrays.sort(mediaFiles) { f1, f2 ->
            java.lang.Long.valueOf(
                f2.lastModified()
            ).compareTo(f1.lastModified())
        }

        gallerySlider.adapter = GallerySliderAdapter(
            this,
            mediaFiles
        )

        val backButton : ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        val fIButton : ImageView = findViewById(R.id.file_info)
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

        val shareIcon : ImageView = findViewById(R.id.share_icon)
        shareIcon.setOnClickListener {

            val file = getCurrentFile()

            val share = Intent(Intent.ACTION_SEND)
            val values = ContentValues()
            val uri: Uri?

            if (file.extension=="mp4") {
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
    }

    private fun getCurrentFile() : File {
        val i = gallerySlider.currentItem
        return mediaFiles[i]
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        fun convertTime(time: Long): String {
            val date = Date(time)
            val format: Format =
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            return format.format(date)
        }
    }
}