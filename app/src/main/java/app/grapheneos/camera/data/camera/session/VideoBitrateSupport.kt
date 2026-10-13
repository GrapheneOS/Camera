package app.grapheneos.camera.data.camera.session

import android.media.CamcorderProfile
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import app.grapheneos.camera.data.camera.model.VideoBitrates
import app.grapheneos.camera.data.core.model.VideoQuality
import javax.inject.Inject

interface VideoBitrateSupport {

    fun bitrates(cameraInfo: CameraInfo): Map<VideoQuality, VideoBitrates>
}

// CameraX keeps its encoder profiles internal, but builds them from the platform's.
internal class VideoBitrateSupportImpl @Inject constructor() : VideoBitrateSupport {

    override fun bitrates(cameraInfo: CameraInfo): Map<VideoQuality, VideoBitrates> {
        val cameraId = cameraId(cameraInfo) ?: return emptyMap()

        return buildMap {
            VideoQuality.entries.forEach { videoQuality ->
                val profileQuality = profileQuality(videoQuality)
                val bitrates = bitrates(
                    cameraId = cameraId,
                    quality = profileQuality,
                )

                if (bitrates != null) {
                    put(videoQuality, bitrates)
                }
            }
        }
    }

    private fun bitrates(
        cameraId: String,
        quality: Int,
    ): VideoBitrates? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> encoderBitrates(cameraId, quality)
            else -> camcorderBitrates(cameraId, quality)
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun cameraId(cameraInfo: CameraInfo): String? {
        return try {
            Camera2CameraInfo.from(cameraInfo).cameraId
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Camera info carries no camera2 id", exception)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun encoderBitrates(
        cameraId: String,
        quality: Int,
    ): VideoBitrates? {
        val profiles = CamcorderProfile.getAll(cameraId, quality)
        val video = profiles?.videoProfiles?.firstOrNull() ?: return null
        val audio = profiles.audioProfiles.firstOrNull()

        return VideoBitrates(
            videoBitsPerSecond = video.bitrate,
            videoFrameRate = video.frameRate,
            audioBitsPerSecond = audio?.bitrate ?: 0,
        )
    }

    @Suppress("DEPRECATION")
    private fun camcorderBitrates(
        cameraId: String,
        quality: Int,
    ): VideoBitrates? {
        val id = cameraId.toIntOrNull()?.takeIf { CamcorderProfile.hasProfile(it, quality) }
            ?: return null
        val profile = CamcorderProfile.get(id, quality)

        return VideoBitrates(
            videoBitsPerSecond = profile.videoBitRate,
            videoFrameRate = profile.videoFrameRate,
            audioBitsPerSecond = profile.audioBitRate,
        )
    }

    private fun profileQuality(videoQuality: VideoQuality): Int {
        return when (videoQuality) {
            VideoQuality.HIGHEST -> CamcorderProfile.QUALITY_HIGH
            VideoQuality.UHD -> CamcorderProfile.QUALITY_2160P
            VideoQuality.FHD -> CamcorderProfile.QUALITY_1080P
            VideoQuality.HD -> CamcorderProfile.QUALITY_720P
            VideoQuality.SD -> CamcorderProfile.QUALITY_480P
        }
    }

    private companion object {
        const val TAG = "VideoBitrateSupport"
    }
}
