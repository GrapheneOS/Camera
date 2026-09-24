package app.grapheneos.camera.data.permission.model

import android.Manifest

enum class AppPermission(
    val manifestNames: List<String>,
) {
    CAMERA(
        manifestNames = listOf(
            Manifest.permission.CAMERA,
        ),
    ),
    MICROPHONE(
        manifestNames = listOf(
            Manifest.permission.RECORD_AUDIO,
        ),
    ),
    LOCATION(
        manifestNames = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    ),
}
