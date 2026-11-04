package app.grapheneos.camera.data.media.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import javax.inject.Inject

internal interface VideoPlaybackFactory {
    fun create(): VideoPlayback
}

@OptIn(UnstableApi::class)
internal class VideoPlaybackFactoryImpl @Inject constructor(
    private val exoPlayerComponents: ExoPlayerComponents,
) : VideoPlaybackFactory {

    override fun create(): VideoPlayback {
        // The app records nothing but MP4 and has no network access, so the container parser runs
        // in this process for that one format only, and reads local content alone
        val mediaSourceFactory = ProgressiveMediaSource.Factory(
            DataSource.Factory {
                exoPlayerComponents.contentDataSource()
            },
            ExtractorsFactory {
                arrayOf(
                    Mp4Extractor(
                        SubtitleParser.Factory.UNSUPPORTED,
                        Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
                    ),
                )
            },
        )
        val renderersFactory = exoPlayerComponents.renderersFactory()
            .setEnableDecoderFallback(true)
        val player = exoPlayerComponents.playerBuilder(renderersFactory, mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()

        return VideoPlaybackImpl(player)
    }
}
