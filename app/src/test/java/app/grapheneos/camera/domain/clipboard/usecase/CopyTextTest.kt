package app.grapheneos.camera.domain.clipboard.usecase

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CopyTextTest {

    private val clipboardManager = ApplicationProvider
        .getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java)
    private val copyText = CopyTextImpl(clipboardManager = clipboardManager)

    @Test
    fun copy_putsTheTextOnTheClipboard() {
        copyText("47.50000, -122.25000")

        assertEquals(
            "47.50000, -122.25000",
            clipboardManager.primaryClip?.getItemAt(0)?.text,
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun copy_beforeAndroid13_isUnconfirmed() {
        assertEquals(CopyTextResult.Unconfirmed, copyText("text"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun copy_fromAndroid13_isConfirmedBySystem() {
        assertEquals(CopyTextResult.ConfirmedBySystem, copyText("text"))
    }
}
