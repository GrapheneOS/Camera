package app.grapheneos.camera.data.settings.model

/**
 * Persisted by ordinal, so the order of these constants is the storage format: inserting one
 * anywhere but the end re-labels every stored grid.
 */
enum class GridType {
    NONE,
    THREE_BY_THREE,
    FOUR_BY_FOUR,
    GOLDEN_RATIO,
}
