package app.grapheneos.camera

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.capturer.VideoCapturer
import app.grapheneos.camera.data.media.store.videoCollectionUri
import app.grapheneos.camera.ui.activities.MainActivity
import io.mockk.every
import io.mockk.mockk

/** Starts the recording as soon as it is asked to, skipping the sound. */
fun immediateTunePlayer(): TunePlayer {
    return mockk(relaxed = true) {
        every { playVRStartSound(handler = any(), onPlayed = any()) } answers {
            secondArg<Runnable>().run()
        }
    }
}

/** Pending entries are how in-flight and orphaned outputs appear in MediaStore. */
fun pendingVideoCount(): Int {
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val args = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaColumns.IS_PENDING} = 1" +
                    " AND ${MediaColumns.DISPLAY_NAME} LIKE '$VIDEO_NAME_PREFIX%'",
        )
    }
    return resolver.query(videoCollectionUri, arrayOf(MediaColumns._ID), args, null)
        ?.use { it.count } ?: 0
}

fun <A : MainActivity> lastCapturedUri(scenario: ActivityScenario<A>): Uri? {
    var uri: Uri? = null
    scenario.onActivity { uri = it.capturedItemSession.lastCapturedItem?.uri }
    return uri
}

/**
 * Waits for the recorder itself to start: the camera counts as recording from the press, while the
 * output is still being created.
 */
fun <A : MainActivity> awaitRecordingStarted(scenario: ActivityScenario<A>) {
    waitUntil(scenario, "the recording has started") { it.timerView.visibility == View.VISIBLE }
}

/**
 * A finished recording's output is saved or discarded off the main thread, and only published once
 * saved: waits for both, so that what the camera shows as captured is final.
 */
fun <A : MainActivity> awaitOutputSettled(scenario: ActivityScenario<A>) {
    VideoCapturer.awaitOutputWork(timeoutS = 10)
    // Runs after what the output work posted back
    scenario.onActivity {}
}

/**
 * Every step is best-effort: this also runs after a failed assertion, where the activity may
 * already be gone and where throwing would replace the failure the test is reporting.
 */
fun <A : MainActivity> stopAndDeleteRecording(
    scenario: ActivityScenario<A>,
    capturedBefore: Uri?,
) {
    runCatching { scenario.onActivity { it.videoCapturer.stopRecording() } }
    runCatching {
        waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
    }
    runCatching { awaitOutputSettled(scenario) }
    runCatching { deleteNewCapture(scenario, capturedBefore) }
}

/** Keeps test runs from accumulating videos on the device. */
private fun <A : MainActivity> deleteNewCapture(scenario: ActivityScenario<A>, previous: Uri?) {
    scenario.onActivity { activity ->
        val item = activity.capturedItemSession.lastCapturedItem ?: return@onActivity
        if (item.uri != previous) {
            try {
                activity.contentResolver.delete(item.uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
