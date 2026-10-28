package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import app.grapheneos.camera.domain.clipboard.usecase.CopyText
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject

interface ViewfinderDialogDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun close()
    fun dismiss()
    fun showPictureFailureDetails()
    fun copyPictureFailureDetails()
    fun copy(text: String)
}

internal class ViewfinderDialogDelegateImpl @Inject constructor(
    private val copyText: CopyText,
) : ViewfinderDialogDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun close() {
        stateHolder.update { it.copy(dialog = null) }
    }

    override fun dismiss() {
        val dialog = stateHolder.state.value.dialog

        close()

        // The camera can't do anything without its permission, so declining to grant it leaves.
        if (dialog is ViewfinderDialog.CameraPermission) {
            stateHolder.postEffect(Effect.CloseScreen)
        }
    }

    override fun showPictureFailureDetails() {
        val failure = pictureFailure() ?: return

        stateHolder.update { it.copy(dialog = failure.copy(showsDetails = true)) }
    }

    override fun copyPictureFailureDetails() {
        val failure = pictureFailure() ?: return

        close()
        copy(failure.details)
    }

    override fun copy(text: String) {
        if (copyText(text) == CopyTextResult.Unconfirmed) {
            stateHolder.postEffect(Effect.ShowMessage(R.string.copied_text_to_clipboard))
        }
    }

    private fun pictureFailure(): ViewfinderDialog.PictureFailure? {
        return stateHolder.state.value.dialog as? ViewfinderDialog.PictureFailure
    }
}
