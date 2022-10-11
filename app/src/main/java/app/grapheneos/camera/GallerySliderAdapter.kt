package app.grapheneos.camera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.capturer.getVideoThumbnail
import app.grapheneos.camera.databinding.GallerySlideBinding
import app.grapheneos.camera.ui.ZoomableImageView
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.activities.VideoPlayer
import app.grapheneos.camera.ui.fragment.GallerySlide
import app.grapheneos.camera.util.executeIfAlive
import kotlin.math.max

class GallerySliderAdapter(
    private val gActivity: InAppGallery,
    val items: ArrayList<CapturedItem>
) : RecyclerView.Adapter<GallerySlide>() {

    var atLeastOneBindViewHolderCall = false

    private val layoutInflater: LayoutInflater = LayoutInflater.from(gActivity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GallerySlide {
        return GallerySlide(GallerySlideBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemId(position: Int): Long {
        return items[position].hashCode().toLong()
    }

    override fun onBindViewHolder(holder: GallerySlide, position: Int) {
        val mediaPreview: ZoomableImageView = holder.binding.slidePreview
//        Log.d("GallerySliderAdapter", "postiion $position, preview ${System.identityHashCode(mediaPreview)}")
        val playButton: ImageView = holder.binding.playButton
        val item = items[position]

        mediaPreview.setGalleryActivity(gActivity)
        mediaPreview.disableZooming()
        mediaPreview.setOnClickListener(null)
        mediaPreview.visibility = View.INVISIBLE
        mediaPreview.setImageBitmap(null)

        val placeholderText = holder.binding.placeholderText.root
        if (atLeastOneBindViewHolderCall) {
            placeholderText.visibility = View.VISIBLE
            placeholderText.setText("…")
        }
        atLeastOneBindViewHolderCall = true

        playButton.visibility = View.GONE

        holder.currentPostion = position

        gActivity.asyncImageLoader.executeIfAlive {
            val bitmap: Bitmap? = try {
                if (item.type == ITEM_TYPE_VIDEO) {
                    getVideoThumbnail(gActivity, item.uri)
                } else {
                    val source = ImageDecoder.createSource(gActivity.contentResolver, item.uri)
                    ImageDecoder.decodeBitmap(source, ImageDownscaler)
                }
            } catch (e: Exception) { null }

            gActivity.mainExecutor.execute {
                if (holder.currentPostion == position) {
                    if (bitmap != null) {
                        placeholderText.visibility = View.GONE
                        mediaPreview.visibility = View.VISIBLE
                        mediaPreview.setImageBitmap(bitmap)

                        if (item.type == ITEM_TYPE_VIDEO) {
                            playButton.visibility = View.VISIBLE
                        } else if (item.type == ITEM_TYPE_IMAGE) {
                            mediaPreview.enableZooming()
                        }

                        mediaPreview.setOnClickListener {
                            val curItem = getCurrentItem()
                            if (curItem.type == ITEM_TYPE_VIDEO) {
                                val intent = Intent(gActivity, VideoPlayer::class.java)
                                intent.putExtra(VideoPlayer.VIDEO_URI, curItem.uri)
                                intent.putExtra(VideoPlayer.IN_SECURE_MODE, gActivity.isSecureMode)

                                gActivity.startActivity(intent)
                            } else {
                                gActivity.toggleActionBarState()
                            }
                        }
                    } else  {
                        mediaPreview.visibility = View.INVISIBLE

                        val resId = if (item.type == ITEM_TYPE_IMAGE) {
                            R.string.inaccessible_image
                        } else { R.string.inaccessible_video }

                        placeholderText.visibility = View.VISIBLE
                        placeholderText.setText(gActivity.getString(resId, item.dateString))
                    }
                } else {
                    bitmap?.recycle()
                }
            }
        }
    }

    fun removeItem(item: CapturedItem) {
        removeChildAt(items.indexOf(item))
    }

    private fun removeChildAt(index: Int) {
        items.removeAt(index)

        // Close gallery if no files are present
        if (items.isEmpty()) {
            gActivity.showMessage(
                gActivity.getString(R.string.existing_no_image)
            )
            gActivity.finish()
        }

        notifyItemRemoved(index)
    }

    fun getCurrentItem(): CapturedItem {
        return items[gActivity.gallerySlider.currentItem]
    }

    override fun getItemCount(): Int {
        return items.size
    }
}

object ImageDownscaler : ImageDecoder.OnHeaderDecodedListener {
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
}
