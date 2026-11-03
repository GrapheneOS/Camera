package app.grapheneos.camera.di.gallery

import android.content.res.Resources
import app.grapheneos.camera.data.media.model.ImageBounds
import app.grapheneos.camera.ui.gallery.screen.ScreenImageBounds
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.math.max
import kotlin.math.min

@Module
@InstallIn(SingletonComponent::class)
internal class GalleryProvidesModule {

    // The screen can be rotated, so the photo's longer side is fitted to the screen's longer side.
    @Provides
    @Reusable
    fun provideScreenImageBounds(
        resources: Resources,
    ): ScreenImageBounds {
        return ScreenImageBounds {
            val metrics = resources.displayMetrics

            ImageBounds(
                longSide = max(metrics.widthPixels, metrics.heightPixels),
                shortSide = min(metrics.widthPixels, metrics.heightPixels),
            )
        }
    }
}
