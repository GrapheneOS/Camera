package app.grapheneos.camera.ui.dialog

import android.app.Activity
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import app.grapheneos.camera.R
import app.grapheneos.camera.qr.data.GEO
import app.grapheneos.camera.qr.data.Mail
import app.grapheneos.camera.qr.data.MeCard
import app.grapheneos.camera.qr.data.Phone
import app.grapheneos.camera.qr.data.SMS
import app.grapheneos.camera.qr.data.VCard
import app.grapheneos.camera.qr.data.Wifi
import app.grapheneos.camera.qr.parser.parseGeo
import app.grapheneos.camera.qr.parser.parseMail
import app.grapheneos.camera.qr.parser.parseMeCard
import app.grapheneos.camera.qr.parser.parsePhoneOrFacetime
import app.grapheneos.camera.qr.parser.parseSMS
import app.grapheneos.camera.qr.parser.parseVCard
import app.grapheneos.camera.qr.parser.parseWifi
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private data class DialogContent(
    @StringRes val title: Int,
    val message: String,
    @StringRes val action: Int
)

fun showActionableDialog(activity: Activity, rawContent: String, onDismiss: () -> Unit): Boolean {

    val card = parsePhoneOrFacetime(rawContent)
        ?: parseSMS(rawContent)
        ?: parseGeo(rawContent)
        ?: parseMeCard(rawContent)
        ?: parseMail(rawContent)
        ?: parseVCard(rawContent)
        ?: parseWifi(rawContent)
        ?: return false

    val (title, message, action) = when (card) {
        is GEO -> DialogContent(
            R.string.address,
            activity.getString(R.string.address_message, card.long, card.lat),
            R.string.open_in_maps
        )

        is MeCard -> DialogContent(
            R.string.contact_card_me_card,
            activity.getString(R.string.mecard_message),
            R.string.add_to_contacts
        )

        is Phone -> DialogContent(
            R.string.phone,
            activity.getString(R.string.call_message, "${card.number}"),
            R.string.call
        )

        is SMS -> DialogContent(
            R.string.message,
            activity.getString(R.string.sms_message, card.number),
            R.string.message
        )

        is Mail -> DialogContent(
            R.string.mail,
            activity.getString(R.string.mail_message, card.mailTo.to),
            R.string.mail,
        )

        is VCard -> DialogContent(
            R.string.contact_card_vcard,
            activity.getString(R.string.vcard_message),
            R.string.add_to_contacts
        )

        is Wifi -> DialogContent(
            R.string.connect_to_wifi,
            activity.getString(R.string.wifi_message, card.ssid),
            R.string.connect
        )
    }

    activity.runOnUiThread {
        val dialogContext = ContextThemeWrapper(
            activity,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        )
        MaterialAlertDialogBuilder(dialogContext)
            .setTitle(title)
            .setMessage(message)
            .setOnDismissListener { onDismiss() }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(action) { _, _ ->
                card.startIntent(activity)
            }.show()
    }

    return true
}
