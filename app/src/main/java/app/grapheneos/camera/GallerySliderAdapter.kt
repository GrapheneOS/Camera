package app.grapheneos.camera

import android.content.Intent
import android.graphics.ImageDecoder
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
import kotlin.math.max

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

        mediaPreview.setGalleryActivity(gActivity)
        mediaPreview.disableZooming()
        mediaPreview.setOnClickListener {
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
            }

        } else {
            playButton.visibility = View.INVISIBLE
            mediaPreview.enableZooming()

            val bitmap = try {
                val source = ImageDecoder.createSource(gActivity.contentResolver, mediaUri)
                ImageDecoder.decodeBitmap(source, object : ImageDecoder.OnHeaderDecodedListener {
                    override fun onHeaderDecoded(decoder: ImageDecoder,
                        info: ImageDecoder.ImageInfo, source: ImageDecoder.Source) {
                        val size = info.size
                        val w = size.width
                        val h = size.height
                        // limit the max size of the bitmap to avoid bumping into bitmap size limit
                        // (100 MB)
                        val largerSide = max(w, h)
                        val maxSide = 4500

                        if (largerSide > maxSide) {
                            val ratio = maxSide.toDouble() / largerSide
                            decoder.setTargetSize((ratio * w).toInt(), (ratio * h).toInt())
                        }
                    }
                })
            } catch (e: Exception) {
                gActivity.showMessage(gActivity.getString(R.string.inaccessible_image))
                null
            }

            mediaPreview.setImageBitmap(bitmap)
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
                gActivity.getString(R.string.existing_no_image)
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
