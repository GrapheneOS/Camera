package app.grapheneos.camera

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.config.CamConfig
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.activities.VideoPlayer
import app.grapheneos.camera.ui.fragment.GallerySlide
import java.io.File

class GallerySliderAdapter(private val gActivity: InAppGallery,
                           private val mediaFiles: ArrayList<File>)
    : RecyclerView.Adapter<GallerySlide>() {

    private val layoutInflater: LayoutInflater = LayoutInflater.from(
        gActivity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            GallerySlide {
        return GallerySlide(layoutInflater.inflate(
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

                val playButton: ImageView =
                    holder.itemView.findViewById(R.id.play_button)
                playButton.visibility = View.VISIBLE

                val rootView: View =
                    holder.itemView.findViewById(R.id.root)
                rootView.setOnClickListener {
                    val intent = Intent(
                        gActivity,
                        VideoPlayer::class.java
                    )
                    intent.putExtra("videoUri",
                        Uri.parse(mediaFile.absolutePath))
                    gActivity.startActivity(intent)
                }

            } catch (exception: Exception) {}

        } else {
            mediaPreview.setImageURI(Uri.parse(mediaFile.absolutePath))
        }
    }

    fun removeChildAt(index: Int) {
        mediaFiles.removeAt(index)

        // Close gallery if no files are present
        if (mediaFiles.isEmpty()) {
            Toast.makeText(
                gActivity,
                "No image found. Exiting in-app gallery.",
                Toast.LENGTH_LONG
            ).show()
            gActivity.finish()
        }

        notifyItemRemoved(index)
    }

    fun getCurrentFile() : File {
        return mediaFiles[gActivity.gallerySlider.currentItem]
    }

    override fun getItemCount(): Int {
        return mediaFiles.size
    }
}