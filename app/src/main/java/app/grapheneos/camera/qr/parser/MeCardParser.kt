package app.grapheneos.camera.qr.parser

import app.grapheneos.camera.qr.data.MeCard
import app.grapheneos.camera.util.removePrefixCaseInsensitive

const val KEY_MECARD = "MECARD:"
const val MECARD_KEY_ADDRESS = "ADR:"
const val MECARD_KEY_BIRTHDAY = "BDAY:"
const val MECARD_KEY_EMAIL = "EMAIL:"
const val MECARD_KEY_NAME = "N:"
const val MECARD_KYE_NICKNAME = "NICKNAME:"
const val MECARD_KEY_NOTE = "NOTE:"
const val MECARD_KYE_SOUND = "SOUND:"
const val MECARD_KEY_TELEPHONE = "TEL:"
const val MECARD_KEY_TELEPHONE_AV = "TEL-AV:"
const val MECARD_KEY_URL = "URL:"

fun parseMeCard(input: String): MeCard? {

    if (!input.startsWith(KEY_MECARD, ignoreCase = true)) {
        return null
    }

    val rawText = input.removePrefixCaseInsensitive(KEY_MECARD)

    val escapeChar = Regex.escape("\\")
    val splitAt = Regex.escape(";")
    val pattern = Regex("(?<!${escapeChar})${splitAt}")
    val fields = rawText.splitToSequence(pattern)

    var name = ""
    var nickname = ""
    var email = ""
    var note = ""
    var sound = ""
    var telephoneNumber = ""
    var telephoneNumberAv = ""
    var birthDate = ""
    var address = ""
    var url = ""

    for (field in fields) {
        when {
            field.startsWith(MECARD_KEY_NAME, ignoreCase = true) -> {
                name = field.removePrefixCaseInsensitive(MECARD_KEY_NAME)
            }

            field.startsWith(MECARD_KYE_NICKNAME, ignoreCase = true) -> {
                nickname = field.removePrefixCaseInsensitive(MECARD_KYE_NICKNAME)
            }

            field.startsWith(MECARD_KYE_SOUND, ignoreCase = true) -> {
                sound = field.removePrefixCaseInsensitive(MECARD_KYE_SOUND)
            }

            field.startsWith(MECARD_KEY_ADDRESS, ignoreCase = true) -> {
                address = field.removePrefixCaseInsensitive(MECARD_KEY_ADDRESS)
            }

            field.startsWith(MECARD_KEY_TELEPHONE, ignoreCase = true) -> {
                telephoneNumber = field.removePrefixCaseInsensitive(MECARD_KEY_TELEPHONE)
            }

            field.startsWith(MECARD_KEY_TELEPHONE_AV, ignoreCase = true) -> {
                telephoneNumberAv = field.removePrefixCaseInsensitive(MECARD_KEY_TELEPHONE_AV)
            }

            field.startsWith(MECARD_KEY_EMAIL, ignoreCase = true) -> {
                email = field.removePrefixCaseInsensitive(MECARD_KEY_EMAIL)
            }

            field.startsWith(MECARD_KEY_URL, ignoreCase = true) -> {
                url = field.removePrefixCaseInsensitive(MECARD_KEY_URL)
            }

            field.startsWith(MECARD_KEY_NOTE, ignoreCase = true) -> {
                note = field.removePrefixCaseInsensitive(MECARD_KEY_NOTE)
            }

            field.startsWith(MECARD_KEY_BIRTHDAY, ignoreCase = true) -> {
                birthDate = field.removePrefixCaseInsensitive(MECARD_KEY_BIRTHDAY)
            }

        }
    }

    return MeCard(
        name = name, email = email, note = note, sound = sound,
        telephoneNumber = telephoneNumber, telephoneNumberAv = telephoneNumberAv,
        birthDate = birthDate, address = address, nickName = nickname, url = url
    )

}
