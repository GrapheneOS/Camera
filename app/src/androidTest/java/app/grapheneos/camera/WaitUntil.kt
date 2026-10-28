package app.grapheneos.camera

import android.app.Activity
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import app.grapheneos.camera.ui.activities.MainActivity
import java.util.concurrent.ExecutorService

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
    // Not the wall clock, which an NTP sync part way through a run can move either way
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        var satisfied = false
        scenario.onActivity { satisfied = condition(it) }
        if (satisfied) return
        if (SystemClock.uptimeMillis() >= deadline) break
        Thread.sleep(100)
    }
    throw AssertionError("Timed out after ${timeoutMs}ms waiting until $description")
}

/** Waits for the thumbnail loader to finish its work and for the result it posted to apply. */
fun <A : MainActivity> awaitThumbnailLoader(scenario: ActivityScenario<A>) {
    lateinit var loader: ExecutorService
    scenario.onActivity { loader = it.thumbnailLoaderExecutor }
    loader.submit {}.get()
    // Runs after the result the loader posted
    scenario.onActivity {}
}
