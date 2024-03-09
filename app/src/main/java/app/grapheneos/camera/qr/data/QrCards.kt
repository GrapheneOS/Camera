package app.grapheneos.camera.qr.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.MailTo
import app.grapheneos.camera.R
import app.grapheneos.camera.qr.handler.addToContact
import app.grapheneos.camera.qr.handler.convertWifiQrDataToIntent
import app.grapheneos.camera.qr.parser.vcardToIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder

sealed class QrIntent {
    abstract fun startIntent(context: Context): Boolean
}

enum class WifiSecurityType {
    Open,
    WPA,
    WPA2,
    WPA3
}

data class Wifi(
    val ssid: String,
    val securityType: WifiSecurityType,
    val sharedKey: String,
    val isHidden: Boolean
) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(convertWifiQrDataToIntent(this))
        } else {
            showWifiDialog(context)
        }
        return true
    }

    private fun showWifiDialog(context: Context) {
        val dialogContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        )
        MaterialAlertDialogBuilder(dialogContext)
            .setTitle(R.string.wifi_dialog_title)
            .setMessage(context.getString(R.string.wifi_dialog_message, ssid))
            .setPositiveButton(R.string.wifi_dialog_button_positive) { _, _ ->
                copySharedKeyToClipboard(context)
            }
            .setNegativeButton(R.string.wifi_dialog_button_negative, null)
            .show()
    }

    private fun copySharedKeyToClipboard(context: Context) {
        val sharedKeyClipData = ClipData.newPlainText(
            context.getString(R.string.wifi_password_clipboard_label),
            sharedKey
        )
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(sharedKeyClipData)
    }

}

data class SMS(val number: String, val message: String) : QrIntent() {

    companion object {
        private const val EXTRA_SMS_BODY = "sms_body"
        private const val SMS_URI = "sms"
    }

    override fun startIntent(context: Context): Boolean {
        val messageIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("$SMS_URI:$number")
            putExtra(EXTRA_SMS_BODY, message)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(messageIntent, null))
        return true
    }
}

data class Phone(val number: Int) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${number}")
                }, null
            )
        )
        return true
    }
}

data class Mail(val mailTo: MailTo, val uri: Uri) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SENDTO, uri), null
            )
        )
        return true
    }
}

data class GEO(val lat: String, val long: String, val altitude: String, val uri: Uri) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                }, null
            )
        )
        return true
    }
}

data class VCard(val input: String) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        context.startActivity(vcardToIntent(input, context))
        return true
    }
}

data class MeCard(

    val name: String,
    val email: String,
    val note: String,
    val sound: String,
    val telephoneNumber: String,

    //supported in v2+//

    val telephoneNumberAv: String,

    //supported in v3+//

    val birthDate: String, //YYYY-MM-DD
    val address: String,
    val nickName: String,
    val url: String
) : QrIntent() {
    override fun startIntent(context: Context): Boolean {
        context.startActivity(addToContact())
        return true
    }
}
