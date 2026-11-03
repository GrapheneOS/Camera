package app.grapheneos.camera.ui.core.effect

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.grapheneos.camera.ui.core.screen.ScreenEvents
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectEventsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val owner = EntryOwner()
    private val channel = ScreenEvents<Int>()
    private val events = channel.flow

    @Test
    fun events_areHandledWhileStartedAndNotReplayedAfterRestart() {
        val handled = mutableListOf<Int>()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                CollectEvents(
                    events = events,
                    onEvent = { handled += it },
                )
            }
        }

        composeRule.runOnIdle { channel.send(1) }
        composeRule.runOnIdle {
            assertEquals(emptyList<Int>(), handled)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.runOnIdle {
            assertEquals(listOf(1), handled)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }

        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            channel.send(2)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(1, 2), handled)
            owner.registry.currentState = Lifecycle.State.CREATED
        }

        composeRule.runOnIdle { channel.send(3) }
        composeRule.runOnIdle {
            assertEquals(listOf(1, 2), handled)
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.runOnIdle {
            assertEquals(listOf(1, 2, 3), handled)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.runOnIdle { assertEquals(listOf(1, 2, 3), handled) }
    }

    @Test
    fun recomposition_usesTheLatestCallbackWithoutReplayingTheEvent() {
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        val callback = mutableStateOf<(Int) -> Unit>({ first += it })
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val onEvent = callback.value
                CollectEvents(
                    events = events,
                    onEvent = { onEvent(it) },
                )
            }
        }

        composeRule.runOnIdle { channel.send(1) }
        composeRule.runOnIdle { callback.value = { second += it } }
        composeRule.runOnIdle { channel.send(2) }
        composeRule.runOnIdle {
            assertEquals(listOf(1), first)
            assertEquals(listOf(2), second)
        }
    }

    @Test
    fun replacingTheEntryOwner_stopsCollectionForTheOldOwner() {
        val nextOwner = EntryOwner()
        val currentOwner = mutableStateOf<LifecycleOwner>(owner)
        val handled = mutableListOf<Int>()
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            nextOwner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides currentOwner.value) {
                CollectEvents(
                    events = events,
                    onEvent = { handled += it },
                )
            }
        }

        composeRule.runOnIdle { channel.send(1) }
        composeRule.runOnIdle { currentOwner.value = nextOwner }
        composeRule.runOnIdle { channel.send(2) }
        composeRule.runOnIdle {
            assertEquals(listOf(1), handled)
            nextOwner.registry.currentState = Lifecycle.State.STARTED
        }
        composeRule.runOnIdle { assertEquals(listOf(1, 2), handled) }
    }

    private class EntryOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
