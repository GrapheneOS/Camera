package app.grapheneos.camera.data.permission.model

import android.Manifest

enum class AppPermission(
    val manifestNames: List<String>,
) {
    CAMERA(manifestNames = listOf(Manifest.permission.CAMERA)),
    MICROPHONE(manifestNames = listOf(Manifest.permission.RECORD_AUDIO)),
}
