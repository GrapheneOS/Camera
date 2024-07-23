package app.grapheneos.camera.qr.parser

import app.grapheneos.camera.qr.data.Wifi
import app.grapheneos.camera.qr.data.WifiSecurityType
import app.grapheneos.camera.util.removePrefixCaseInsensitive

const val WIFI_BEGINNING = "WIFI:"
const val KYE_SSID = "S:"
const val KYE_SECURITY_TYPE = "T:"
const val KYE_SHARED_KEY = "P:"
const val KYE_IS_HIDDEN = "H:"

fun parseWifi(input: String): Wifi? {
    if (!input.startsWith(WIFI_BEGINNING, ignoreCase = true)) {
        return null
    }

    val rawText = input.removePrefixCaseInsensitive(WIFI_BEGINNING)

    val escapeChar = Regex.escape("\\")
    val splitAt = Regex.escape(";")
    val pattern = Regex("(?<!${escapeChar})${splitAt}")
    val fields = rawText.splitToSequence(pattern)

    var ssid = ""
    var type: WifiSecurityType? = null
    var sharedKey = ""
    var isHidden = false


    for (field in fields) {
        when {
            field.startsWith(KYE_SSID, ignoreCase = true) -> {
                ssid = field.removePrefixCaseInsensitive(KYE_SSID)
            }

            field.startsWith(KYE_SECURITY_TYPE, ignoreCase = true) -> {
                type = wifiQrTypeToSecurityType(
                    field.removePrefixCaseInsensitive(KYE_SECURITY_TYPE)
                )
            }

            field.startsWith(KYE_SHARED_KEY, ignoreCase = true) -> {
                sharedKey = field.removePrefixCaseInsensitive(KYE_SHARED_KEY)
            }

            field.startsWith(KYE_IS_HIDDEN, ignoreCase = true) -> {
                isHidden = field.removePrefixCaseInsensitive(KYE_IS_HIDDEN) == "true"
            }
        }

    }

    if (ssid.isBlank() ||
        type == null ||
        (type != WifiSecurityType.Open && sharedKey.isEmpty())
    ) {
        return null
    }

    return Wifi(
        ssid,
        type,
        sharedKey,
        isHidden
    )
}


private fun wifiQrTypeToSecurityType(type: String): WifiSecurityType? {
    return when (type.uppercase()) {
        "NOPASS" -> WifiSecurityType.Open
        "WPA" -> WifiSecurityType.WPA
        "WPA2" -> WifiSecurityType.WPA2
        "WPA3" -> WifiSecurityType.WPA3
        else -> null
    }
}
