package app.grapheneos.camera

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.databinding.GallerySlideBinding
import app.grapheneos.camera.ui.ZoomableImageView
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.activities.VideoPlayer
import app.grapheneos.camera.ui.fragment.GallerySlide

class GallerySliderAdapter(
    private val gActivity: InAppGallery,
    val mediaUris: ArrayList<Uri>
) : RecyclerView.Adapter<GallerySlide>() {

    private val layoutInflater: LayoutInflater = LayoutInflater.from(
        gActivity
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            GallerySlide {
        return GallerySlide(
            GallerySlideBinding.inflate(layoutInflater, parent, false)
        )
    }

    override fun getItemId(position: Int): Long {
        return mediaUris[position].hashCode().toLong()
    }

    override fun onBindViewHolder(holder: GallerySlide, position: Int) {
        val mediaPreview: ZoomableImageView = holder.binding.slidePreview
        val playButton: ImageView = holder.binding.playButton
        val mediaUri = mediaUris[position]

        mediaPreview.apply {
            disableZooming()
            setGalleryActivity(gActivity)
            setOnClickListener {
                val mUri = getCurrentUri()
                if (VideoCapturer.isVideo(mUri)) {
                    val intent = Intent(
                        gActivity,
                        VideoPlayer::class.java
                    )
                    intent.putExtra("videoUri", mUri)

                    gActivity.startActivity(intent)
                } else {
                    gActivity.toggleActionBarState()
                }
            }
        }

        if (VideoCapturer.isVideo(mediaUri)) {
            try {
                mediaPreview.setImageBitmap(
                    CamConfig.getVideoThumbnail(
                        gActivity,
                        mediaUri
                    )
                )

                playButton.visibility = View.VISIBLE

            } catch (exception: Exception) {
                //TODO why all exception are getting ignored here
                // and why it surrounded with try catch in the first place
            }

        } else {
            playButton.visibility = View.INVISIBLE
            mediaPreview.enableZooming()
            mediaPreview.setImageURI(mediaUri)
        }
    }

    fun removeUri(uri: Uri) {
        removeChildAt(mediaUris.indexOf(uri))
    }

    private fun removeChildAt(index: Int) {
        mediaUris.removeAt(index)

        // Close gallery if no files are present
        if (mediaUris.isEmpty()) {
            gActivity.showMessage(
                "No image found. Exiting in-app gallery."
            )
            gActivity.finish()
        }

        notifyItemRemoved(index)
    }

    fun getCurrentUri(): Uri {
        return mediaUris[gActivity.gallerySlider.currentItem]
    }

    override fun getItemCount(): Int {
        return mediaUris.size
    }
}