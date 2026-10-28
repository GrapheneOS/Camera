package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.permission.model.AppPermission

sealed interface ViewfinderDialog {

    data class Permission(
        val permission: AppPermission,
        val offersSettings: Boolean,
    ) : ViewfinderDialog
}
