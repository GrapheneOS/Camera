package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject

interface ViewfinderPermissionDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun refresh()
    fun showDialog(permission: AppPermission)
    fun dismissDialog()
}

internal class ViewfinderPermissionDelegateImpl @Inject constructor(
    private val permissionRepository: PermissionRepository,
) : ViewfinderPermissionDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun refresh() {
        val missing = AppPermission.entries.filterNotTo(mutableSetOf()) {
            permissionRepository.isGranted(it)
        }

        stateHolder.update { it.copy(missingPermissions = missing) }
    }

    override fun showDialog(permission: AppPermission) {
        stateHolder.update { it.copy(permissionDialog = permission) }
    }

    override fun dismissDialog() {
        stateHolder.update { it.copy(permissionDialog = null) }
    }
}
