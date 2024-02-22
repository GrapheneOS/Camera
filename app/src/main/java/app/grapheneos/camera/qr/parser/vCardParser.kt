package app.grapheneos.camera.qr.parser

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.grapheneos.camera.qr.data.VCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val VCARD_BEGINNING = "BEGIN:VCARD"
const val VCARD_ENDING = "END:VCARD"

fun parseVCard(input: String): VCard? {
    if (!input.startsWith(VCARD_BEGINNING, ignoreCase = true)
        || !input.endsWith(VCARD_ENDING, ignoreCase = true)
    ) {
        return null
    }

    return VCard(input)
}

fun vcardToIntent(input: String, context: Context): Intent {
    val time = SimpleDateFormat("yyyy-MM-dd_HH-mmss-SSS", Locale.US).format(Date())
    val name = "vcard-${time}.vcf"
    val uri = saveViaMediaStore(context.contentResolver, name, input)

    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension("vcf")
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun saveViaMediaStore(
    contentResolver: ContentResolver,
    name: String,
    content: String
): Uri? {
    val values = ContentValues()
    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    values.put(MediaStore.MediaColumns.IS_PENDING, true)
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)

    val target = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val uri = contentResolver.insert(target, values) ?: return null

    contentResolver.openOutputStream(uri, "rw")?.use {
        it.write(content.toByteArray())
    }

    contentResolver.update(uri, ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, false)
    }, null, null)

    return uri
}
