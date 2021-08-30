package org.grapheneos.camera;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class FlashAdapter extends RecyclerView.Adapter<FlashAdapter.IconHolder> {

    private static final int[] resIds = {R.drawable.flash_auto, R.drawable.flash_on, R.drawable.flash_off};

    @NonNull
    @Override
    public IconHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ImageView iconView = (ImageView) layoutInflater.inflate(R.layout.icon_64,
                parent, false);
        return new IconHolder(iconView);
    }

    @Override
    public void onBindViewHolder(@NonNull IconHolder holder, int position) {
        holder.icon.setImageResource(resIds[position]);
    }

    @Override
    public int getItemCount() {
        return resIds.length;
    }

    public static class IconHolder extends RecyclerView.ViewHolder {
        public final ImageView icon;

        public IconHolder(final ImageView itemView) {
            super(itemView);
            icon = itemView;
        }
    }
}
