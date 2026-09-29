package app.grapheneos.camera.ui

import android.content.Intent
import android.text.util.Linkify
import android.view.View
import androidx.core.net.toUri
import app.grapheneos.camera.R
import app.grapheneos.camera.databinding.ScanResultDialogBinding
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.util.resolveActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.nio.charset.StandardCharsets

fun showQrResultDialog(
    activity: MainActivity,
    rawText: String,
    onCopyText: (CharSequence) -> Unit,
    onDismissed: () -> Unit,
) {
    val hexText = bytesToHex(rawText.toByteArray(StandardCharsets.UTF_8))

    val builder = MaterialAlertDialogBuilder(activity)
    val dialogBinding = ScanResultDialogBinding.inflate(activity.layoutInflater)
    builder.setView(dialogBinding.root)

    val tabLayout: TabLayout = dialogBinding.encodingTabs
    val textView = dialogBinding.scanResultText

    val intentView = Intent(Intent.ACTION_VIEW, rawText.toUri())

    if (activity.packageManager.resolveActivity(intentView, 0L) != null) {
        dialogBinding.openWith.setOnClickListener {
            val chooser = Intent.createChooser(intentView, activity.getString(R.string.open_with))
            activity.startActivity(chooser)
        }
    } else {
        dialogBinding.openWith.visibility = View.GONE
    }

    tabLayout.addOnTabSelectedListener(
        object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.text.toString()) {
                    "Binary" -> {
                        textView.autoLinkMask = 0
                        textView.text = hexText
                    }

                    "UTF-8" -> {
                        textView.autoLinkMask = Linkify.WEB_URLS or
                            Linkify.PHONE_NUMBERS or
                            Linkify.EMAIL_ADDRESSES
                        textView.text = rawText
                    }
                }
            }

            @Suppress("EmptyFunctionBlock")
            override fun onTabReselected(tab: TabLayout.Tab?) {}

            @Suppress("EmptyFunctionBlock")
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
        },
    )

    tabLayout.addTab(
        tabLayout.newTab().apply {
            text = "UTF-8"
        },
    )

    tabLayout.addTab(
        tabLayout.newTab().apply {
            text = "Binary"
        },
    )

    dialogBinding.copyQrText.setOnClickListener {
        onCopyText(textView.text)
    }

    dialogBinding.shareQrText.setOnClickListener {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, textView.text.toString())
        activity.startActivity(
            Intent.createChooser(
                shareIntent,
                activity.getString(R.string.share_text_via),
            ),
        )
    }

    builder.setOnDismissListener {
        onDismissed()
    }

    builder.showIgnoringShortEdgeMode()
}

@Suppress("MagicNumber")
private fun bytesToHex(bytes: ByteArray): String {
    if (bytes.isEmpty()) return "" // outLen will be wrong for empty inputs

    // Represent bytes as a grid of hex digits:
    // Add a space between every byte
    // Double space every 4 bytes (unless end or newline)
    // Add a newline every 8 bytes (unless end)

    var outLen = bytes.size * 3 - 1 // 2 hex digits + 1 space/newline per byte (except last)
    outLen += bytes.size / 8 // One double space per row except the last incomplete row
    if (bytes.size % 8 > 4) {
        outLen += 1 // One double space for the last incomplete row, if it has >4 columns
    }

    val hexChars = CharArray(outLen)
    var j = 0 // Output index

    for (i in bytes.indices) {
        val byte = bytes[i].toInt() and 0xFF
        hexChars[j++] = HEX_DIGITS[byte ushr 4]
        hexChars[j++] = HEX_DIGITS[byte and 0x0F]

        if (i == bytes.lastIndex) break // No trailing whitespace
        if (i % 8 == 7) {
            hexChars[j++] = '\n'
        } else {
            hexChars[j++] = ' '
            if (i % 4 == 3) hexChars[j++] = ' '
        }
    }

    return String(hexChars)
}

private const val HEX_DIGITS = "0123456789ABCDEF"
