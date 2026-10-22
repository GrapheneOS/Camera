package app.grapheneos.camera

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.camera.ui.core.theme.CameraTheme
import app.grapheneos.camera.ui.gallery.screen.GalleryScreen
import app.grapheneos.camera.ui.gallery.screen.GalleryScreenModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPageUiModel
import app.grapheneos.camera.ui.gallery.screen.model.GalleryPreview
import app.grapheneos.camera.ui.gallery.screen.model.GalleryUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule(order = 0)
    val screenAwake = ScreenAwakeRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val uiState = MutableStateFlow(GalleryUiState())
    private val screenModel = mockk<GalleryScreenModel>(relaxed = true) {
        every { uiState } returns this@GalleryRegressionTest.uiState
        every { effects } returns emptyFlow()
    }

    /**
     *  The media actions must not be offered before there is media to act on.
     */
    @Test
    fun mediaActions_hiddenUntilThereIsMedia() {
        showGallery()
        val delete = composeRule.activity.getString(R.string.delete)

        composeRule.onNodeWithText(delete).assertDoesNotExist()

        uiState.value = IMAGE_SHOWN

        composeRule.onNodeWithText(delete).assertExists()
    }

    /**
     * A finger resting on a gallery page has its gesture cancelled when the page goes away. The
     * page used to answer that through whichever page the pager held first, and after the last item
     * was deleted the pager held none.
     */
    @Test
    fun removingTheLastPage_underARestingFinger_doesNotCrash() {
        uiState.value = IMAGE_SHOWN
        showGallery()

        composeRule.onRoot().performTouchInput { down(center) }
        uiState.value = GalleryUiState()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.waitForIdle()
    }

    private fun showGallery() {
        composeRule.setContent {
            CameraTheme {
                GalleryScreen(
                    videoPlayerLauncher = mockk(relaxed = true),
                    systemGalleryLauncher = mockk(relaxed = true),
                    clipboardManager = mockk(relaxed = true),
                    onNavigateBack = {},
                    screenModel = screenModel,
                )
            }
        }
    }

    private companion object {
        const val IMAGE_KEY = "content://media/external/images/media/0"
        val IMAGE_SHOWN = GalleryUiState(
            pages = persistentListOf(
                GalleryPageUiModel(
                    key = IMAGE_KEY,
                    isVideo = false,
                ),
            ),
            previews = persistentMapOf(
                IMAGE_KEY to GalleryPreview.Loaded(ImageBitmap(width = 64, height = 48)),
            ),
        )
    }
}
