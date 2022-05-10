package app.grapheneos.camera.capturer

class ImageSaverException(val place: Place, cause: Exception? = null) : Exception(cause) {
    enum class Place {
        IMAGE_EXTRACTION,
        IMAGE_CROPPING,
        EXIF_PARSING,
        FILE_CREATION,
        FILE_WRITE,
        FILE_WRITE_COMPLETION,
    }
}

fun Throwable.asStringList() : List<String> {
    val list = ArrayList<String>()

    val chain = ArrayList<Throwable>()
    var tail: Throwable? = this
    do {
        chain.add(tail!!)
        tail = tail.cause
    } while (tail != null)

    chain.reversed().forEachIndexed { index, entry ->
        if (chain.size != 1) {
            list.add("--- Exception ${index + 1} / ${chain.size}")
        }

        list.add(entry.javaClass.name)
        entry.message?.let {
            list.add(it)
        }

        entry.stackTrace.forEach {
            list.add("${it.methodName}  ::  ${it.className}  ::  ${it.lineNumber}")
        }
    }
    return list
}
