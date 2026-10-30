package app.grapheneos.camera.data.permission.repository

fun interface SelfPermissionChecker {
    fun isGranted(manifestName: String): Boolean
}
