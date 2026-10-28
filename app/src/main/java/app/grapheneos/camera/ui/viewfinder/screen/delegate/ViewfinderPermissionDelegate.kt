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
        val dialogPermission = when (stateHolder.state.value.dialog) {
            is ViewfinderDialog.CameraPermission -> AppPermission.CAMERA
            is ViewfinderDialog.MicrophonePermission -> AppPermission.MICROPHONE
            else -> null
        }
        val missing = AppPermission.entries.filterNotTo(mutableSetOf()) {
            permissionRepository.isGranted(it)
        }

        stateHolder.update { it.copy(missingPermissions = missing) }

        if (dialogPermission == null || dialogPermission in missing) return

        stateHolder.update { it.copy(dialog = null) }
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
        val offersSettings = !entryPoint.isSecureSession
        val dialog = when (permission) {
            AppPermission.CAMERA -> ViewfinderDialog.CameraPermission(offersSettings)
            AppPermission.MICROPHONE -> ViewfinderDialog.MicrophonePermission(offersSettings)
            AppPermission.LOCATION -> return
        }

        stateHolder.update { it.copy(dialog = dialog) }
    }

    override fun openSettings() {
        stateHolder.postEffect(Effect.Permission.OpenSettings)
    }
}
