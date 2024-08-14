package app.grapheneos.camera.ktx

import android.net.Uri
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.util.getImageOrientationFromUri
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

fun SubsamplingScaleImageView.fixOrientationForImage(imageUri: Uri) {
    val exifOrientation = getImageOrientationFromUri(context, imageUri)
    orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
    when (exifOrientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            scaleX = -1f
        }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            scaleY = -1f
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            scaleX = -1f
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            scaleX = -1f
        }
    }
}