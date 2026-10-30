package app.grapheneos.camera.data.media.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.MediaSource

@OptIn(UnstableApi::class)
interface ExoPlayerComponents {

    fun contentDataSource(): DataSource

    fun renderersFactory(): DefaultRenderersFactory

    fun playerBuilder(
        renderersFactory: RenderersFactory,
        mediaSourceFactory: MediaSource.Factory,
    ): ExoPlayer.Builder
}
