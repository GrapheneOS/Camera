package app.grapheneos.camera.qr.parser

import app.grapheneos.camera.qr.data.SMS
import app.grapheneos.camera.util.removePrefixCaseInsensitive
import java.util.regex.Pattern
import kotlin.math.min

const val KEY_SMSTO = "smsto:"
const val KEY_SMS = "sms:"

fun parseSMS(input: String): SMS? {

    if (!input.startsWith(KEY_SMSTO, ignoreCase = true) &&
        !input.startsWith(KEY_SMS, ignoreCase = true)
    ) {
        return null
    }

    val rawText = input.removePrefixCaseInsensitive(KEY_SMSTO).removePrefixCaseInsensitive(KEY_SMS)

    val numberEndMatch = Regex(Pattern.quote(":")).find(rawText)
    val numberEndIndex = numberEndMatch?.range?.endInclusive ?: rawText.length

    val number = rawText.substring(0, numberEndIndex)
    val message = rawText.substring(min(numberEndIndex.plus(1), rawText.length), rawText.length)

    return SMS(number, message)
}
