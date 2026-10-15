package app.grapheneos.camera

import android.content.Intent
import android.view.View
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.ui.activities.CaptureActivity
import app.grapheneos.camera.ui.activities.MainActivity

fun <A : MainActivity> launchCaptureSession(
    activityClass: Class<A>,
    action: String,
): ActivityScenario<A> {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return ActivityScenario.launch(Intent(context, activityClass).setAction(action))
}

/** A capture session keeps the shutter disabled for a moment after it starts. */
fun <A : CaptureActivity> awaitShutterReady(scenario: ActivityScenario<A>) {
    waitUntil(scenario, "the shutter is ready") {
        it.previewView.previewStreamState.value == PreviewView.StreamState.STREAMING &&
                it.captureButton.isEnabled
    }
}

fun <A : CaptureActivity> awaitConfirmScreen(scenario: ActivityScenario<A>) {
    waitUntil(scenario, "the confirm screen shows") {
        it.confirmButton.visibility == View.VISIBLE
    }
}
