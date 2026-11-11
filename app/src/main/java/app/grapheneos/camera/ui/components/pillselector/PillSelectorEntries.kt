package app.grapheneos.camera.ui.components.pillselector

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

@Stable
internal class PillSelectorEntry<Item, Key>(
    val key: Key,
    item: Item,
    isEntering: Boolean,
) {

    var item by mutableStateOf(item)
        private set

    val visibility = MutableTransitionState(initialState = !isEntering).apply {
        targetState = true
    }

    val isShown: Boolean
        get() {
            return visibility.targetState
        }

    val isGone: Boolean
        get() {
            return !visibility.targetState && !visibility.currentState && visibility.isIdle
        }

    fun show(item: Item) {
        this.item = item
        visibility.targetState = true
    }

    fun hide() {
        visibility.targetState = false
    }
}

@Stable
internal class PillSelectorEntries<Item, Key> {

    var renderedEntries by mutableStateOf<List<PillSelectorEntry<Item, Key>>>(emptyList())
        private set

    var keys by mutableStateOf<List<Key>>(emptyList())
        private set

    private var shownItems by mutableStateOf<List<Item>>(emptyList())
    private var shownKeys by mutableStateOf<List<Key>>(emptyList())

    fun update(
        items: List<Item>,
        keyOf: (Item) -> Key,
    ) {
        val itemKeys = items.map(keyOf)
        if (items == shownItems && itemKeys == shownKeys) return

        val activeKeys = itemKeys.toSet()
        require(activeKeys.size == items.size) {
            "Each item must have a distinct key, were $itemKeys"
        }

        val previousByKey = renderedEntries.associateBy { entry -> entry.key }
        val entries = items.mapIndexed { index, item ->
            val key = itemKeys[index]
            previousByKey[key]?.apply { show(item) } ?: PillSelectorEntry(
                key = key,
                item = item,
                isEntering = renderedEntries.isNotEmpty(),
            )
        }.toMutableList()

        // Keep each exiting entry immediately before its next surviving neighbor.
        var insertionIndex = entries.size
        for (entry in renderedEntries.asReversed()) {
            if (entry.key in activeKeys) {
                insertionIndex = entries.indexOf(entry)
            } else {
                entry.hide()
                entries.add(insertionIndex, entry)
            }
        }

        shownKeys = itemKeys
        shownItems = items
        replace(entries)
    }

    suspend fun removeGoneEntries() {
        snapshotFlow { renderedEntries.any { entry -> entry.isGone } }.collect { hasGone ->
            if (hasGone) {
                replace(entries = renderedEntries.filterNot { entry -> entry.isGone })
            }
        }
    }

    private fun replace(entries: List<PillSelectorEntry<Item, Key>>) {
        renderedEntries = entries
        keys = entries.map { entry -> entry.key }
    }
}

@Composable
internal fun <Item, Key> rememberPillSelectorEntries(
    items: List<Item>,
    key: (item: Item) -> Key,
): PillSelectorEntries<Item, Key> {
    val entries = remember { PillSelectorEntries<Item, Key>() }

    entries.update(
        items = items,
        keyOf = key,
    )

    LaunchedEffect(entries) {
        entries.removeGoneEntries()
    }

    return entries
}
