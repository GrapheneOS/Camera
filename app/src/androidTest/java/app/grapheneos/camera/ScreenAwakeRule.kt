package app.grapheneos.camera

import android.app.KeyguardManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Turns the screen on and dismisses an insecure keyguard before each test.
 *
 * An activity launched into a dozing, locked device gets no visible window, and one without a
 * visible window is already stopped and receives no camera frames or animation callbacks. Tests
 * then time out as if the app were broken, or pause an activity that was never resumed and pass
 * without exercising anything. The screen also dozes off on its own part way through a run, so
 * every test that launches an activity needs this.
 */
class ScreenAwakeRule : ExternalResource() {

    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val power = context.getSystemService(PowerManager::class.java)!!
        val keyguard = context.getSystemService(KeyguardManager::class.java)!!

        val deadline = SystemClock.uptimeMillis() + 10_000
        while (true) {
            val awake = power.isInteractive
            val locked = keyguard.isKeyguardLocked
            if (awake && !locked) return

            // A secured keyguard only answers `wm dismiss-keyguard` with its credential prompt, so
            // waiting out the deadline would tell the same story ten seconds later.
            if (locked && keyguard.isDeviceSecure) {
                throw AssertionError(
                    "The keyguard is up and secured, and no test can enter the credential:" +
                            " unlock the device before running the suite."
                )
            }

            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError(
                    when {
                        !awake -> "The screen would not turn on."
                        else -> "The keyguard is still up."
                    }
                )
            }

            // Reissued every round rather than once: the device can doze off again between the
            // wake-up and the unlock, and a keyguard can take more than one dismissal to go away.
            if (!awake) shell("input keyevent KEYCODE_WAKEUP")
            if (locked) shell("wm dismiss-keyguard")
            Thread.sleep(200)
        }
    }

    /** Reading to EOF waits for the command to finish. */
    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }
}
