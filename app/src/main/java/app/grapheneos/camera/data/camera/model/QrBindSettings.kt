package app.grapheneos.camera.data.camera.model

import app.grapheneos.camera.data.core.model.LensFacing
import com.google.zxing.BarcodeFormat

data class QrBindSettings(
    val lensFacing: LensFacing?,
    val barcodeFormats: Set<BarcodeFormat>,
)
