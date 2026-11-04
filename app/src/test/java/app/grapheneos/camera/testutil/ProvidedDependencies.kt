package app.grapheneos.camera.testutil

import android.content.Context
import app.grapheneos.camera.data.core.store.PreferenceFiles
import app.grapheneos.camera.data.media.model.ImageBounds
import app.grapheneos.camera.data.media.store.RetrieverDataSource
import app.grapheneos.camera.data.permission.repository.SelfPermissionChecker
import app.grapheneos.camera.di.media.MediaStoreProvidesModule
import app.grapheneos.camera.di.permission.PermissionProvidesModule
import app.grapheneos.camera.di.preferences.DurablePreferencesProvidesModule
import app.grapheneos.camera.ui.gallery.screen.ScreenImageBounds

internal val SCREEN_BOUNDS = ImageBounds(longSide = 2400, shortSide = 1080)

internal val FIXED_SCREEN = ScreenImageBounds { SCREEN_BOUNDS }

internal fun preferenceFilesOf(context: Context): PreferenceFiles {
    return DurablePreferencesProvidesModule().providePreferenceFiles(context)
}

internal fun retrieverDataSourceOf(context: Context): RetrieverDataSource {
    return MediaStoreProvidesModule().provideRetrieverDataSource(context)
}

internal fun selfPermissionCheckerOf(context: Context): SelfPermissionChecker {
    return PermissionProvidesModule().provideSelfPermissionChecker(context)
}
