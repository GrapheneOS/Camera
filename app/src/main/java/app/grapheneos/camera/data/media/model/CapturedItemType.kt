package app.grapheneos.camera.data.media.model

enum class CapturedItemType(
    val namePrefix: String,
    val mimeType: String,
) {
    IMAGE(
        namePrefix = "IMG_",
        mimeType = "image/*",
    ),
    VIDEO(
        namePrefix = "VID_",
        mimeType = "video/*",
    ),
}
