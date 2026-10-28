package app.grapheneos.camera.ui.viewfinder.screen.model

sealed interface PictureFailureCause {

    data class Capture(
        val errorCode: Int,
    ) : PictureFailureCause

    data class Save(
        val stage: String,
    ) : PictureFailureCause
}
