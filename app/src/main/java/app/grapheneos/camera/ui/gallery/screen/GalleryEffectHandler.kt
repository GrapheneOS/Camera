package app.grapheneos.camera.ui.gallery.screen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.ui.gallery.screen.model.GalleryMessage
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.VideoPlayerLauncher
import app.grapheneos.camera.ui.mediaviewer.navigation.editCapturedItem
import app.grapheneos.camera.ui.mediaviewer.navigation.shareCapturedItem
import app.grapheneos.camera.ui.mediaviewer.navigation.useCapturedItemAs

@Composable
internal fun rememberGalleryEffectHandler(
    videoPlayerLauncher: VideoPlayerLauncher,
    systemGalleryLauncher: SystemGalleryLauncher,
    clipboardManager: ClipboardManager,
    onShowMessage: (GalleryMessage) -> Unit,
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
    private val showMessage: (GalleryMessage) -> Unit,
) : GalleryEffectHandler {

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.OpenVideo -> videoPlayerLauncher.open(activity, effect.uri)

            is Effect.Edit -> edit(effect)
            is Effect.Share -> share(effect)
            is Effect.UseAs -> useAs(effect)
            is Effect.OpenMap -> openMap(effect.location)
            is Effect.OpenSystemGallery -> systemGalleryLauncher.open(activity)
            is Effect.ShowInSystemGallery -> systemGalleryLauncher.openItem(
                activity = activity,
                item = effect.item,
            )

            is Effect.CopyText -> copy(effect.text)
            is Effect.ItemDeleted -> showText(R.string.deleted_successfully)
            is Effect.ItemTrashed -> showUndoableText(R.string.deleted_successfully)
            is Effect.DeletionFailed -> showText(R.string.deleting_unexpected_error)
            is Effect.RestoreFailed -> showText(R.string.restoring_unexpected_error)
        }
    }

    private fun edit(effect: Effect.Edit) {
        val error = editCapturedItem(
            activity = activity,
            item = effect.item,
            useDefaultEditor = effect.usesDefaultEditor,
        )

        error?.let(::showText)
    }

    private fun share(effect: Effect.Share) {
        val error = shareCapturedItem(
            activity = activity,
            item = effect.item,
        )

        error?.let(::showText)
    }

    private fun useAs(effect: Effect.UseAs) {
        val error = useCapturedItemAs(
            activity = activity,
            item = effect.item,
        )

        error?.let(::showText)
    }

    private fun openMap(location: GeoCoordinates) {
        val coordinates = "${location.latitude},${location.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$coordinates".toUri())

        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showText(R.string.gallery_details_no_map_app)
        }
    }

    private fun copy(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showText(R.string.copied_text_to_clipboard)
        }
    }

    private fun showText(@StringRes text: Int) {
        showMessage(GalleryMessage(text))
    }

    private fun showUndoableText(@StringRes text: Int) {
        showMessage(
            GalleryMessage(
                text = text,
                offersUndo = true,
            ),
        )
    }
}
