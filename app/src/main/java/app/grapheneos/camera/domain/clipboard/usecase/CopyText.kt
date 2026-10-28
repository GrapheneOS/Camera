package app.grapheneos.camera.domain.clipboard.usecase

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import javax.inject.Inject

interface CopyText {
    operator fun invoke(text: String): CopyTextResult
}

internal class CopyTextImpl @Inject constructor(
    private val clipboardManager: ClipboardManager,
) : CopyText {

    override fun invoke(text: String): CopyTextResult {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))

        val systemConfirms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        return when {
            systemConfirms -> CopyTextResult.ConfirmedBySystem
            else -> CopyTextResult.Unconfirmed
        }
    }
}
