package app.grapheneos.camera.qr.parser

import android.net.Uri
import app.grapheneos.camera.qr.data.GEO
import app.grapheneos.camera.util.removePrefixCaseInsensitive
import java.util.regex.Pattern

const val KEY_GEO = "geo:"

fun parseGeo(input: String): GEO? {

    if (!input.startsWith(KEY_GEO, ignoreCase = true)) return null
    val rawText = input.removePrefixCaseInsensitive(KEY_GEO)
    val geoDividerFinder = Regex(Pattern.quote(","))
    val parts = rawText.split(geoDividerFinder)
    val defaultValue = ""

    return GEO(
        lat = parts.getOrElse(0) { defaultValue },
        long = parts.getOrElse(1) { defaultValue },
        altitude = parts.getOrElse(2) { defaultValue },
        uri = Uri.parse(input)
    )
}
