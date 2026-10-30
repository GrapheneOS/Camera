package app.grapheneos.camera.data.camera.model

import com.google.zxing.BarcodeFormat

data class QrBindSettings(
    val lensFacing: LensFacing?,
    val barcodeFormats: Set<BarcodeFormat>,
)
