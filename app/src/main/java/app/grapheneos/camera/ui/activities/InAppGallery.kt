package app.grapheneos.camera.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.GallerySliderAdapter
import app.grapheneos.camera.R
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class InAppGallery: AppCompatActivity() {

    private lateinit var gallerySlider: ViewPager2
    private lateinit var mediaFiles: Array<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery)

        gallerySlider = findViewById(R.id.gallery_slider)

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
            LayoutInflater.from(this),
            mediaFiles
        )
    }
}