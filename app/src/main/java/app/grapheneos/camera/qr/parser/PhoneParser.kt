package app.grapheneos.camera.qr.parser

import android.util.Patterns
import app.grapheneos.camera.qr.data.Phone
import app.grapheneos.camera.util.removePrefixCaseInsensitive

const val KEY_PHONE = "tel:"
const val KEY_FACETIME = "facetime:"
const val KEY_FACETIME_AUDIO = "facetime-audio:"

fun parsePhoneOrFacetime(input: String): Phone? {
    return when {
        input.startsWith(KEY_PHONE, ignoreCase = true) -> {

            val rawText = input.removePrefixCaseInsensitive(KEY_PHONE).replace("-", "")
            if (!Patterns.PHONE.matcher(rawText).find()) {
                return null
            }
            val phoneNumber = rawText.toIntOrNull() ?: 0
            return Phone(phoneNumber)
        }

        input.startsWith(KEY_FACETIME, ignoreCase = true) ||
                input.startsWith(KEY_FACETIME_AUDIO, ignoreCase = true) -> {

            val rawText = input.removePrefixCaseInsensitive(KEY_FACETIME)
                .removePrefixCaseInsensitive(KEY_FACETIME_AUDIO)
                .replace("-", "")

            if (!Patterns.PHONE.matcher(rawText).find()) {
                return null
            }
            val number = rawText.toIntOrNull() ?: 0
            return Phone(number)
        }

        else -> null
    }

}
