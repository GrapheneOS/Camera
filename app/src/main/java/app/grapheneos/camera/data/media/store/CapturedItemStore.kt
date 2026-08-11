package app.grapheneos.camera.data.media.store

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.CapturedItems
import app.grapheneos.camera.data.core.store.STORAGE_LOCATION_KEY
import app.grapheneos.camera.di.core.DurablePreferences
import app.grapheneos.camera.di.core.SessionPreferences
import app.grapheneos.camera.util.edit
import javax.inject.Inject

interface CapturedItemStore {

    fun readLastCapturedItem(): CapturedItem?

    fun writeLastCapturedItem(item: CapturedItem)

    /** The location captures are saved to now, or null while they go to the MediaStore. */
    fun currentSafTree(): Uri?

    /** Every location the gallery lists: the current one first, then the tracked previous ones. */
    fun safTrees(): List<Uri>

    fun previousSafTrees(): List<Uri>

    fun trackSafTree(treeUri: Uri)

    fun readLegacyMediaUris(): String?

    /** Records [trees] and drops the legacy list in one batch, so the migration cannot half-run. */
    fun replaceLegacyMediaUris(trees: List<Uri>)

    fun migrateLastCapturedItem()
}

internal class CapturedItemStoreImpl @Inject constructor(
    @SessionPreferences private val commons: SharedPreferences,
    @DurablePreferences private val media: SharedPreferences,
) : CapturedItemStore {

    override fun readLastCapturedItem(): CapturedItem? {
        return lastCapturedItemIn(media)
    }

    override fun writeLastCapturedItem(item: CapturedItem) {
        media.edit {
            putInt(LAST_CAPTURED_ITEM_TYPE, item.type)
            putString(LAST_CAPTURED_ITEM_DATE_STRING, item.dateString)
            putString(LAST_CAPTURED_ITEM_URI, item.uri.toString())
        }
    }

    override fun currentSafTree(): Uri? {
        return commons.getString(STORAGE_LOCATION_KEY, null)
            ?.takeIf { it.isNotEmpty() }
            ?.toUri()
    }

    override fun safTrees(): List<Uri> {
        val trees = ArrayList<Uri>()

        currentSafTree()?.let {
            trees.add(it)
        }

        trees.addAll(previousSafTrees())

        return trees.distinct()
    }

    override fun previousSafTrees(): List<Uri> {
        val stored = commons.getString(PREVIOUS_SAF_TREES_KEY, null) ?: return emptyList()

        return stored.split(CapturedItems.SAF_TREE_SEPARATOR).map { it.toUri() }
    }

    override fun trackSafTree(treeUri: Uri) {
        val trees = previousSafTrees().toMutableList()

        trees.remove(treeUri)
        trees.add(0, treeUri)

        while (trees.size > CapturedItems.MAX_NUMBER_OF_TRACKED_PREVIOUS_SAF_TREES) {
            // trees.removeLast() requires API level 35 now due to Java adding it
            trees.removeAt(trees.lastIndex)
        }

        commons.edit {
            putPreviousSafTrees(trees, this)
        }
    }

    override fun readLegacyMediaUris(): String? {
        return commons.getString(LEGACY_MEDIA_URIS, null)
    }

    override fun replaceLegacyMediaUris(trees: List<Uri>) {
        commons.edit {
            putPreviousSafTrees(trees, this)
            remove(LEGACY_MEDIA_URIS)
        }
    }

    override fun migrateLastCapturedItem() {
        if (!commons.contains(LAST_CAPTURED_ITEM_DATE_STRING)) {
            return
        }

        if (!media.contains(LAST_CAPTURED_ITEM_DATE_STRING)) {
            lastCapturedItemIn(commons)?.let(::writeLastCapturedItem)
        }

        commons.edit {
            remove(LAST_CAPTURED_ITEM_TYPE)
            remove(LAST_CAPTURED_ITEM_DATE_STRING)
            remove(LAST_CAPTURED_ITEM_URI)
        }
    }

    private fun lastCapturedItemIn(preferences: SharedPreferences): CapturedItem? {
        val dateString = preferences.getString(LAST_CAPTURED_ITEM_DATE_STRING, null)
        val uri = preferences.getString(LAST_CAPTURED_ITEM_URI, null)

        return when {
            dateString == null || uri == null -> null
            else -> {
                CapturedItem(
                    type = preferences.getInt(LAST_CAPTURED_ITEM_TYPE, -1),
                    dateString = dateString,
                    uri = uri.toUri(),
                )
            }
        }
    }

    private fun putPreviousSafTrees(trees: List<Uri>, editor: SharedPreferences.Editor) {
        if (trees.isEmpty()) {
            return
        }

        editor.putString(
            PREVIOUS_SAF_TREES_KEY,
            trees.joinToString(separator = CapturedItems.SAF_TREE_SEPARATOR),
        )
    }

    companion object {
        private const val LAST_CAPTURED_ITEM_TYPE = "last_captured_item_type"
        private const val LAST_CAPTURED_ITEM_DATE_STRING = "last_captured_item_date_string"
        private const val LAST_CAPTURED_ITEM_URI = "last_captured_item_uri"

        private const val PREVIOUS_SAF_TREES_KEY = "previous_saf_trees"

        private const val LEGACY_MEDIA_URIS = "media_uri_s"
    }
}
