package app.grapheneos.camera

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario

/**
 * Polls [condition] on the activity until it holds, and fails the test if it never does.
 * [description] completes the sentence "waiting until ...".
 */
fun <A : Activity> waitUntil(
    scenario: ActivityScenario<A>,
    description: String,
    timeoutMs: Long = 10_000,
    condition: (A) -> Boolean,
) {
    pollUntil(description, timeoutMs) {
        var satisfied = false
        scenario.onActivity { satisfied = condition(it) }
        satisfied
    }
}

/**
 * Waits for the activity to be destroyed, which [waitUntil] cannot do: it reaches the activity
 * through onActivity, and that refuses a destroyed one.
 */
fun <A : Activity> waitUntilDestroyed(
    scenario: ActivityScenario<A>,
    description: String,
    timeoutMs: Long = 10_000,
) {
    pollUntil(description, timeoutMs) {
        scenario.state == Lifecycle.State.DESTROYED
    }
}

private fun pollUntil(
    description: String,
    timeoutMs: Long,
    condition: () -> Boolean,
) {
    // Not the wall clock, which an NTP sync part way through a run can move either way
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        if (condition()) return
        if (SystemClock.uptimeMillis() >= deadline) break
        Thread.sleep(100)
    }
    throw AssertionError("Timed out after ${timeoutMs}ms waiting until $description")
}
