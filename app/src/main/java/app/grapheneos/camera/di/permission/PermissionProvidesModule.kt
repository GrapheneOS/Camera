package app.grapheneos.camera.di.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.grapheneos.camera.data.permission.repository.SelfPermissionChecker
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class PermissionProvidesModule {

    @Provides
    @Reusable
    fun provideSelfPermissionChecker(
        @ApplicationContext context: Context,
    ): SelfPermissionChecker {
        return SelfPermissionChecker { manifestName ->
            ContextCompat.checkSelfPermission(context, manifestName) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
