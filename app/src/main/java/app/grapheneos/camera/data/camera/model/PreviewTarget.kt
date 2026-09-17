package app.grapheneos.camera.data.camera.model

import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner

class PreviewTarget(
    val lifecycleOwner: LifecycleOwner,
    val surfaceProvider: Preview.SurfaceProvider,
    val meteringPointFactory: MeteringPointFactory,
)
