package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject

interface ViewfinderPermissionDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun refresh()
    fun request(permission: AppPermission, explainsFirst: Boolean)
    fun showDialog(permission: AppPermission)
    fun openSettings()
    fun onDialogDismissed(permission: AppPermission)
    fun dismissDialog()
}

internal class ViewfinderPermissionDelegateImpl @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val entryPoint: CameraEntryPoint,
) : ViewfinderPermissionDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun refresh() {
        val dialog = stateHolder.state.value.dialog
        val missing = AppPermission.entries.filterNotTo(mutableSetOf()) {
            permissionRepository.isGranted(it)
        }

        stateHolder.update { it.copy(missingPermissions = missing) }

        if (dialog !is ViewfinderDialog.Permission || dialog.permission in missing) return

        dismissDialog()
    }

    override fun request(
        permission: AppPermission,
        explainsFirst: Boolean,
    ) {
        stateHolder.postEffect(
            Effect.Permission.Request(
                permission = permission,
                explainsFirst = explainsFirst,
            ),
        )
    }

    override fun showDialog(permission: AppPermission) {
        val dialog = ViewfinderDialog.Permission(
            permission = permission,
            offersSettings = !entryPoint.isSecureSession,
        )

        stateHolder.update { it.copy(dialog = dialog) }
    }

    override fun openSettings() {
        dismissDialog()
        stateHolder.postEffect(Effect.Permission.OpenSettings)
    }

    override fun onDialogDismissed(permission: AppPermission) {
        dismissDialog()

        // The dialog could have either been dismissed by clicking on the
        // background or by clicking the cancel button. So in those cases,
        // the app should exit as the app depends on the camera permission.
        if (permission == AppPermission.CAMERA) {
            stateHolder.postEffect(Effect.CloseScreen)
        }
    }

    override fun dismissDialog() {
        stateHolder.update { it.copy(dialog = null) }
    }
}
