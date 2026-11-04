package app.grapheneos.camera.di.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.MediaSource
import app.grapheneos.camera.data.media.player.ExoPlayerComponents
import app.grapheneos.camera.data.media.store.RetrieverDataSource
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class MediaStoreProvidesModule {

    @Provides
    @Reusable
    fun provideRetrieverDataSource(
        @ApplicationContext context: Context,
    ): RetrieverDataSource {
        return RetrieverDataSource { retriever, uri ->
            retriever.setDataSource(context, uri)
        }
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Reusable
    fun provideExoPlayerComponents(
        @ApplicationContext context: Context,
    ): ExoPlayerComponents {
        return object : ExoPlayerComponents {

            override fun contentDataSource(): DataSource {
                return ContentDataSource(context)
            }

            override fun renderersFactory(): DefaultRenderersFactory {
                return DefaultRenderersFactory(context)
            }

            override fun playerBuilder(
                renderersFactory: RenderersFactory,
                mediaSourceFactory: MediaSource.Factory,
            ): ExoPlayer.Builder {
                return ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            }
        }
    }
}
