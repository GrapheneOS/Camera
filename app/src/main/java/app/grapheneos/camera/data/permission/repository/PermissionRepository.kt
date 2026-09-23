package app.grapheneos.camera.data.permission.repository

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.grapheneos.camera.data.permission.model.AppPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PermissionRepository {
    fun isGranted(permission: AppPermission): Boolean
}

internal class PermissionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionRepository {

    override fun isGranted(permission: AppPermission): Boolean {
        return permission.manifestNames.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
