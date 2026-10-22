package app.grapheneos.camera.ui.gallery.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.editCapturedItem
import app.grapheneos.camera.ui.mediaviewer.navigation.shareCapturedItem

@Composable
internal fun rememberGalleryEffectHandler(
    videoPlayerLauncher: VideoPlayerLauncher,
    systemGalleryLauncher: SystemGalleryLauncher,
    clipboardManager: ClipboardManager,
    onShowMessage: (Int) -> Unit,
): GalleryEffectHandler {
    val currentOnShowMessage by rememberUpdatedState(onShowMessage)
    val activity = requireNotNull(LocalActivity.current) {
        "The gallery needs a hosting activity"
    }

    return remember(activity, videoPlayerLauncher, systemGalleryLauncher, clipboardManager) {
        GalleryEffectHandlerImpl(
            activity = activity,
            videoPlayerLauncher = videoPlayerLauncher,
            systemGalleryLauncher = systemGalleryLauncher,
            clipboardManager = clipboardManager,
            showMessage = { message -> currentOnShowMessage(message) },
        )
    }
}

internal interface GalleryEffectHandler {
    fun handle(effect: Effect)
}

internal class GalleryEffectHandlerImpl(
    private val activity: Activity,
    private val videoPlayerLauncher: VideoPlayerLauncher,
    private val systemGalleryLauncher: SystemGalleryLauncher,
    private val clipboardManager: ClipboardManager,
    private val showMessage: (Int) -> Unit,
) : GalleryEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.NothingToShow -> close(R.string.empty_gallery)
            is Effect.OpenVideo -> videoPlayerLauncher.open(activity, effect.uri)
            is Effect.OpenSystemGallery -> systemGalleryLauncher.open(activity)
            is Effect.Edit -> edit(effect)
            is Effect.Share -> share(effect)
            is Effect.OpenMap -> openMap(effect.location)
            is Effect.CopyText -> copy(effect.text)
            is Effect.ItemDeleted -> showMessage(R.string.deleted_successfully)
            is Effect.LastItemDeleted -> close(R.string.existing_no_image)
            is Effect.DeletionFailed -> showMessage(R.string.deleting_unexpected_error)
        }
    }

    private fun edit(effect: Effect.Edit) {
        val error = editCapturedItem(
            activity = activity,
            item = effect.item,
            useDefaultEditor = effect.usesDefaultEditor,
        )

        error?.let(showMessage)
    }

    private fun share(effect: Effect.Share) {
        val error = shareCapturedItem(
            activity = activity,
            item = effect.item,
        )

        error?.let(showMessage)
    }

    private fun openMap(location: GeoCoordinates) {
        val coordinates = "${location.latitude},${location.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$coordinates".toUri())

        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.gallery_details_no_map_app)
        }
    }

    private fun copy(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showMessage(R.string.copied_text_to_clipboard)
        }
    }

    private fun close(@StringRes message: Int) {
        Toast
            .makeText(
                activity.applicationContext,
                message,
                Toast.LENGTH_SHORT,
            )
            .show()
        activity.finish()
    }
}
