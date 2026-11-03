package app.grapheneos.camera.data.permission.repository

import app.grapheneos.camera.data.permission.model.AppPermission
import javax.inject.Inject

interface PermissionRepository {
    fun isGranted(permission: AppPermission): Boolean
}

internal class PermissionRepositoryImpl @Inject constructor(
    private val selfPermissionChecker: SelfPermissionChecker,
) : PermissionRepository {

    override fun isGranted(permission: AppPermission): Boolean {
        return permission.manifestNames.any(selfPermissionChecker::isGranted)
    }
}
