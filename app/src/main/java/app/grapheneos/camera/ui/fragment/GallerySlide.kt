package app.grapheneos.camera.ui.fragment

import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.databinding.GallerySlideBinding

class GallerySlide(val binding: GallerySlideBinding) : RecyclerView.ViewHolder(binding.root) {
    @Volatile var currentPostion = 0
}
