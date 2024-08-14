package app.grapheneos.camera

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.capturer.getVideoThumbnail
import app.grapheneos.camera.databinding.GallerySlideBinding
import app.grapheneos.camera.ktx.fixOrientationForImage
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.activities.VideoPlayer
import app.grapheneos.camera.ui.fragment.GallerySlide
import app.grapheneos.camera.util.executeIfAlive
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.max

class GallerySliderAdapter(
    private val gActivity: InAppGallery,
    val items: ArrayList<CapturedItem>
) : RecyclerView.Adapter<GallerySlide>() {

    companion object {
        private const val TAG = "GallerySliderAdapter"
    }

    var atLeastOneBindViewHolderCall = false

    private val layoutInflater: LayoutInflater = LayoutInflater.from(gActivity)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GallerySlide {
        return GallerySlide(GallerySlideBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemId(position: Int): Long {
        return items[position].hashCode().toLong()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: GallerySlide, position: Int) {
        holder.currentPostion = position

        val mediaPreview: SubsamplingScaleImageView = holder.binding.slidePreview
        val playButton: ImageView = holder.binding.playButton
        val item = items[position]

        val placeholderText = holder.binding.placeholderText.root
        if (atLeastOneBindViewHolderCall) {
            placeholderText.visibility = View.VISIBLE
            placeholderText.text = "…"
        }
        atLeastOneBindViewHolderCall = true

        playButton.visibility = View.GONE

        mediaPreview.isPanEnabled = false
        mediaPreview.isZoomEnabled = false

        mediaPreview.setExecutor(gActivity.asyncImageLoader)

        mediaPreview.maxScale = 3f

        mediaPreview.setDoubleTapZoomScale(1.5F)
        mediaPreview.setDoubleTapZoomDuration(300)

        mediaPreview.setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
            override fun onReady() {}

            override fun onImageLoaded() {
                mediaPreview.visibility = View.VISIBLE
                placeholderText.visibility = View.GONE

                if (item.type == ITEM_TYPE_IMAGE) {
                    mediaPreview.isPanEnabled = true
                    mediaPreview.isZoomEnabled = true

                    mediaPreview.setOnClickListener {
                        gActivity.toggleActionBarState()
                    }
                } else {
                    playButton.visibility = View.VISIBLE

                    mediaPreview.setOnClickListener {
                        val curItem = getCurrentItem()
                        if (curItem.type == ITEM_TYPE_VIDEO) {
                            val intent = Intent(gActivity, VideoPlayer::class.java)
                            intent.putExtra(VideoPlayer.VIDEO_URI, curItem.uri)
                            intent.putExtra(VideoPlayer.IN_SECURE_MODE, gActivity.isSecureMode)

                            gActivity.startActivity(intent)
                        }
                    }
                }
            }

            override fun onImageLoadError(e: java.lang.Exception?) {
                Log.i(TAG, "onImageLoadError: Failed to load image")
                if (e == null) {
                    Log.d(TAG, "onImageLoadError received null as an exception")
                } else {
                    e.printStackTrace()
                }

                onErrorLoadingMedia()
            }

            override fun onTileLoadError(e: java.lang.Exception?) {
                Log.i(TAG, "onTileLoadError: An unexpected error occurred while loading a tile")
                if (e == null) {
                    Log.d(TAG, "onTileLoadError: Received null as an exception")
                } else {
                    e.printStackTrace()
                }

                onErrorLoadingMedia()
            }

            override fun onPreviewLoadError(e: java.lang.Exception?) {}

            override fun onPreviewReleased() {}

            fun onErrorLoadingMedia() {
                mediaPreview.visibility = View.INVISIBLE

                val placeholderTextFormat = if (item.type == ITEM_TYPE_VIDEO) {
                    R.string.inaccessible_video
                } else {
                    R.string.inaccessible_image
                }

                placeholderText.visibility = View.VISIBLE
                placeholderText.text = gActivity.getString(placeholderTextFormat, item.dateString)
            }
        })

        // Ensure that the touch events are being sent to the most recently viewed media
        mediaPreview.setOnTouchListener { v, event ->
            gActivity.gallerySlider.getChildAt(0).findViewById<SubsamplingScaleImageView>(R.id.slide_preview).onTouchEvent(event)
        }

        mediaPreview.setOnStateChangedListener(object: SubsamplingScaleImageView.OnStateChangedListener {
            override fun onScaleChanged(newScale: Float, origin: Int) {
                gActivity.let {
                    if (newScale == mediaPreview.minScale) {
                        gActivity.gallerySlider.isUserInputEnabled = true
                        it.showActionBar()
                        it.vibrateDevice()
                    } else {
                        gActivity.gallerySlider.isUserInputEnabled = false
                        it.hideActionBar()
                    }
                }
            }

            override fun onCenterChanged(newCenter: PointF?, origin: Int) {}

        })

        if (item.type == ITEM_TYPE_IMAGE) {
            mediaPreview.fixOrientationForImage(item.uri)
            mediaPreview.setImage(ImageSource.uri(item.uri))
        } else {
            gActivity.asyncImageLoader.executeIfAlive {
                val thumbnailBitmap = getVideoThumbnail(gActivity, item.uri)

                if (thumbnailBitmap != null) {
                    gActivity.mainExecutor.execute {
                        if (holder.currentPostion == position) {
                            thumbnailBitmap.let {
                                mediaPreview.setImage(ImageSource.bitmap(thumbnailBitmap))
                            }
                        } else {
                            thumbnailBitmap.recycle()
                        }
                    }
                } else {
                    mediaPreview.visibility = View.INVISIBLE
                    placeholderText.visibility = View.VISIBLE
                    placeholderText.text = gActivity.getString(R.string.inaccessible_video, item.dateString)
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
