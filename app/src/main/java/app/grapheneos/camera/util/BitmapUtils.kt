package app.grapheneos.camera.util

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi

fun setBlurBitmapCompat(view: ImageView, bitmap: Bitmap, radius: Float = 4f) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blurRenderEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        view.setImageBitmap(bitmap)
        view.setRenderEffect(blurRenderEffect)
        return
    }
    view.setImageBitmap(app.grapheneos.camera.BlurBitmap[bitmap])
}
