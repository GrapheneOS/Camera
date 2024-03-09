package app.grapheneos.camera.qr.parser

import android.net.Uri
import androidx.core.net.MailTo
import app.grapheneos.camera.qr.data.Mail

fun parseMail(input: String): Mail? {
    val uri = Uri.parse(input) ?: return null
    if (!MailTo.isMailTo(uri)) return null
    val mailTo = MailTo.parse(uri)
    return Mail(mailTo, uri)
}
