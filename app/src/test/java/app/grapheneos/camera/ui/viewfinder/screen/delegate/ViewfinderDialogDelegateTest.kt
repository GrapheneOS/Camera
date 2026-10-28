package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import app.grapheneos.camera.domain.clipboard.usecase.CopyText
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureCause
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderDialogDelegateTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)
    private val copyText = mockk<CopyText>()
    private val delegate = ViewfinderDialogDelegateImpl(copyText = copyText).apply {
        bind(stateHolder)
    }

    @Test
    fun cameraPermissionDismissed_closesTheScreen() {
        runTest {
            val effects = collectEffects(stateHolder)
            show(ViewfinderDialog.CameraPermission(offersSettings = true))

            delegate.dismiss()

            assertNull(dialog())
            assertEquals(listOf(Effect.CloseScreen), effects)
        }
    }

    @Test
    fun otherDialogDismissed_keepsTheScreen() {
        runTest {
            val effects = collectEffects(stateHolder)
            show(ViewfinderDialog.MicrophonePermission(offersSettings = true))

            delegate.dismiss()

            assertNull(dialog())
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun pictureFailureDetails_replaceTheMessage() {
        show(PICTURE_FAILURE)

        delegate.showPictureFailureDetails()

        assertEquals(PICTURE_FAILURE.copy(showsDetails = true), dialog())
    }

    @Test
    fun pictureFailureDetails_areCopiedAndTheDialogClosed() {
        every { copyText(any()) } returns CopyTextResult.ConfirmedBySystem
        show(PICTURE_FAILURE)

        delegate.copyPictureFailureDetails()

        verify { copyText(PICTURE_FAILURE.details) }
        assertNull(dialog())
    }

    @Test
    fun copy_unconfirmed_confirmsWithAMessage() {
        runTest {
            every { copyText("text") } returns CopyTextResult.Unconfirmed
            val effects = collectEffects(stateHolder)

            delegate.copy("text")

            assertEquals(listOf(Effect.ShowMessage(R.string.copied_text_to_clipboard)), effects)
        }
    }

    @Test
    fun copy_confirmedBySystem_showsNoMessage() {
        runTest {
            every { copyText(any()) } returns CopyTextResult.ConfirmedBySystem
            val effects = collectEffects(stateHolder)

            delegate.copy("text")

            assertTrue(effects.isEmpty())
        }
    }

    private fun show(dialog: ViewfinderDialog) {
        stateHolder.update { it.copy(dialog = dialog) }
    }

    private fun dialog(): ViewfinderDialog? {
        return stateHolder.state.value.dialog
    }

    private companion object {
        val PICTURE_FAILURE = ViewfinderDialog.PictureFailure(
            cause = PictureFailureCause.Save(stage = "FILE_WRITE"),
            details = "details",
        )
    }
}
