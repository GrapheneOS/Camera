package app.grapheneos.camera

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.config.CamConfig
import app.grapheneos.camera.ui.fragment.GallerySlide
import java.io.File

class GallerySliderAdapter(private val inflater: LayoutInflater,
                           private val mediaFiles: Array<File>)
    : RecyclerView.Adapter<GallerySlide>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            GallerySlide {
        return GallerySlide(inflater.inflate(
            R.layout.gallery_slide,
            parent, false
        ))
    }

    override fun onBindViewHolder(holder: GallerySlide, position: Int) {

        val mediaPreview: ImageView =
            holder.itemView.findViewById(R.id.slide_preview)

        if(mediaPreview.drawable != null) return

        val mediaFile = mediaFiles[position]

        if(mediaFile.extension == "mp4") {
            try {
                mediaPreview.setImageBitmap(
                    CamConfig.getVideoThumbnail(
                        mediaFile.absolutePath
                    )
                )
            } catch (exception: Exception) {}

        } else {
            mediaPreview.setImageURI(Uri.parse(mediaFile.absolutePath))
        }
    }

    override fun getItemCount(): Int {
        return mediaFiles.size
    }
}