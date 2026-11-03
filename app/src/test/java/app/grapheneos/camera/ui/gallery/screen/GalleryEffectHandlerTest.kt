package app.grapheneos.camera.ui.gallery.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.mediaactions.CapturedItemIntents
import app.grapheneos.camera.ui.mediaactions.SystemGalleryLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GalleryEffectHandlerTest {

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val systemGalleryLauncher = mockk<SystemGalleryLauncher>(relaxed = true)
    private val capturedItemIntents = mockk<CapturedItemIntents>(relaxed = true)
    private val messages = mutableListOf<Int>()
    private val effectHandler = GalleryEffectHandlerImpl(
        activity = activity,
        systemGalleryLauncher = systemGalleryLauncher,
        capturedItemIntents = capturedItemIntents,
        showMessage = { message -> messages += message.text },
    )

    @Test
    fun openSystemGallery_opensTheSystemGallery() {
        effectHandler.handle(Effect.OpenSystemGallery)

        verify { systemGalleryLauncher.open(activity) }
    }

    @Test
    fun showInSystemGallery_opensTheItemInTheSystemGallery() {
        effectHandler.handle(Effect.ShowInSystemGallery(ITEM))

        verify {
            systemGalleryLauncher.openItem(
                activity = activity,
                item = ITEM,
            )
        }
    }

    @Test
    fun edit_withTheDefaultEditor_requestsTheDefaultEditor() {
        effectHandler.handle(
            Effect.Edit(
                item = ITEM,
                usesDefaultEditor = true,
            ),
        )

        verify {
            capturedItemIntents.edit(
                activity = activity,
                item = ITEM,
                useDefaultEditor = true,
            )
        }
    }

    @Test
    fun edit_withAChosenEditor_requestsAnEditorChooser() {
        effectHandler.handle(
            Effect.Edit(
                item = ITEM,
                usesDefaultEditor = false,
            ),
        )

        verify {
            capturedItemIntents.edit(
                activity = activity,
                item = ITEM,
                useDefaultEditor = false,
            )
        }
    }

    @Test
    fun share_delegatesToMediaActions() {
        effectHandler.handle(Effect.Share(ITEM))

        verify {
            capturedItemIntents.share(
                activity = activity,
                item = ITEM,
            )
        }
    }

    @Test
    fun useAs_delegatesToMediaActions() {
        effectHandler.handle(Effect.UseAs(ITEM))

        verify {
            capturedItemIntents.useAs(
                activity = activity,
                item = ITEM,
            )
        }
    }

    @Test
    fun failedMediaActions_showTheirErrorWithoutClosingTheGallery() {
        every {
            capturedItemIntents.share(
                activity = activity,
                item = ITEM,
            )
        } returns R.string.unable_to_share_media
        every {
            capturedItemIntents.edit(
                activity = activity,
                item = ITEM,
                useDefaultEditor = true,
            )
        } returns R.string.unable_to_edit_media
        every {
            capturedItemIntents.useAs(
                activity = activity,
                item = ITEM,
            )
        } returns R.string.unable_to_use_media

        effectHandler.handle(Effect.Share(ITEM))
        effectHandler.handle(
            Effect.Edit(
                item = ITEM,
                usesDefaultEditor = true,
            ),
        )
        effectHandler.handle(Effect.UseAs(ITEM))

        assertEquals(
            listOf(
                R.string.unable_to_share_media,
                R.string.unable_to_edit_media,
                R.string.unable_to_use_media,
            ),
            messages,
        )
        assertFalse(activity.isFinishing)
    }

    @Test
    fun openMap_showsTheLocationInAMapApp() {
        effectHandler.handle(
            Effect.OpenMap(
                GeoCoordinates(
                    latitude = 47.5,
                    longitude = -122.25,
                ),
            ),
        )

        val intent = nextStartedActivity()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:47.5,-122.25?q=47.5,-122.25", intent.dataString)
    }

    @Test
    fun outcomes_thatKeepTheGalleryOpen_areReportedAsMessages() {
        effectHandler.handle(Effect.ItemDeleted)
        effectHandler.handle(Effect.DeletionFailed)
        effectHandler.handle(Effect.TextCopied)

        assertEquals(
            listOf(
                R.string.deleted_successfully,
                R.string.deleting_unexpected_error,
                R.string.copied_text_to_clipboard,
            ),
            messages,
        )
        assertFalse(activity.isFinishing)
    }

    private fun nextStartedActivity(): Intent {
        return requireNotNull(shadowOf(activity).nextStartedActivity) {
            "No activity was started"
        }
    }

    private companion object {
        val ITEM = CapturedItem(
            type = CapturedItemType.IMAGE,
            dateString = "20231016_120000_000",
            uri = Uri.parse("content://media/external/images/media/0"),
        )
    }
}
