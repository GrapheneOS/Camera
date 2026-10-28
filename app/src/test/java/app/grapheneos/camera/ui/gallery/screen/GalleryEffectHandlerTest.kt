package app.grapheneos.camera.ui.gallery.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.media.model.GeoCoordinates
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.mediaviewer.navigation.SystemGalleryLauncher
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GalleryEffectHandlerTest {

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val systemGalleryLauncher = mockk<SystemGalleryLauncher>(relaxed = true)
    private val messages = mutableListOf<Int>()
    private val effectHandler = GalleryEffectHandlerImpl(
        activity = activity,
        systemGalleryLauncher = systemGalleryLauncher,
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
    fun edit_withTheDefaultEditor_startsTheEditor() {
        effectHandler.handle(
            Effect.Edit(
                item = ITEM,
                usesDefaultEditor = true,
            ),
        )

        assertEquals(Intent.ACTION_EDIT, nextStartedActivity().action)
    }

    @Test
    fun edit_withAChosenEditor_offersAChooser() {
        effectHandler.handle(
            Effect.Edit(
                item = ITEM,
                usesDefaultEditor = false,
            ),
        )

        assertEquals(Intent.ACTION_CHOOSER, nextStartedActivity().action)
    }

    @Test
    fun share_offersAChooser() {
        effectHandler.handle(Effect.Share(ITEM))

        assertEquals(Intent.ACTION_CHOOSER, nextStartedActivity().action)
    }

    @Test
    fun useAs_offersTheAppsThatCanUseThePhoto() {
        effectHandler.handle(Effect.UseAs(ITEM))

        val chooser = nextStartedActivity()
        val target = IntentCompat.getParcelableExtra(
            chooser,
            Intent.EXTRA_INTENT,
            Intent::class.java,
        )

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_ATTACH_DATA, target?.action)
        assertEquals(ITEM.uri, target?.data)
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
