package app.grapheneos.camera.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.grapheneos.camera.R
import app.grapheneos.camera.adapter.FlashAdapter.IconHolder

class FlashAdapter : RecyclerView.Adapter<IconHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val iconView = layoutInflater.inflate(
            R.layout.icon_64,
            parent, false
        ) as ImageView
        return IconHolder(iconView)
    }

    override fun onBindViewHolder(holder: IconHolder, position: Int) {
        holder.icon.setImageResource(resIds[position])
    }

    override fun getItemCount(): Int {
        return resIds.size
    }

    class IconHolder(val icon: ImageView) : RecyclerView.ViewHolder(
        icon
    )

    companion object {
        private val resIds =
            intArrayOf(R.drawable.flash_auto, R.drawable.flash_on, R.drawable.flash_off)
    }
}
