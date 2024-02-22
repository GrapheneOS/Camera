package app.grapheneos.camera.util

fun String.removePrefixCaseInsensitive(prefix: String): String {
    if (startsWith(prefix, ignoreCase = true)) {
        return substring(prefix.length)
    }
    return this
}
