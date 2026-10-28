package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderDialog {

    data object StorageLocationNotFound : ViewfinderDialog

    data class Permission(
        val permission: AppPermission,
        val offersSettings: Boolean,
    ) : ViewfinderDialog
}
