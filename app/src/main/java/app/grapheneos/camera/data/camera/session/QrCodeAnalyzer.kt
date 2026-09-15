package app.grapheneos.camera.data.camera.session

import androidx.camera.core.ImageAnalysis

interface QrCodeAnalyzer : ImageAnalysis.Analyzer {
    fun refreshHints()
}
