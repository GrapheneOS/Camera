package app.grapheneos.camera.ui

/** Selection and scrolling do not mutate the order; only an explicit placement does. */
internal object ModeEditorOrder {
    fun <T> paste(items: List<T>, source: T, before: T, after: Boolean = false): List<T> {
        if (source == before || !items.contains(source) || !items.contains(before)) return items
        val arranged = items.toMutableList()
        arranged.remove(source)
        val destinationIndex = arranged.indexOf(before)
        arranged.add(destinationIndex + if (after) 1 else 0, source)
        return arranged
    }
}
