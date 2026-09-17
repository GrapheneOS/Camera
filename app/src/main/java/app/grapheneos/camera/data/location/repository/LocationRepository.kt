package app.grapheneos.camera.data.location.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface LocationRepository {
    fun shouldAskForPermission(): Boolean
}

internal class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {

    override fun shouldAskForPermission(): Boolean {
        return !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
