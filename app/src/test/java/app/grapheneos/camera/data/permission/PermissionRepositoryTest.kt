package app.grapheneos.camera.data.permission

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.permission.repository.PermissionRepositoryImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PermissionRepositoryTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val repository = PermissionRepositoryImpl(context = application)

    @Test
    fun location_isGrantedByEitherOfItsPermissions() {
        assertFalse(repository.isGranted(AppPermission.LOCATION))

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(repository.isGranted(AppPermission.LOCATION))
    }
}
