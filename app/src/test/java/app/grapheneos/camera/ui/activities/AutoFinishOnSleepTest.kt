package app.grapheneos.camera.ui.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoFinishOnSleepTest {

    private val activity = mockk<Activity>(relaxed = true)

    private val receiver = slot<BroadcastReceiver>()

    @Test
    fun screenOff_afterTheActivityFinished_leavesItsAffinityAlone() {
        every { activity.isFinishing } returns true

        turnScreenOff()

        verify(exactly = 0) { activity.finishAffinity() }
    }

    @Test
    fun screenOff_whileTheActivityIsShown_finishesItsAffinity() {
        every { activity.isFinishing } returns false

        turnScreenOff()

        verify(exactly = 1) { activity.finishAffinity() }
    }

    private fun turnScreenOff() {
        every {
            activity.registerReceiver(
                capture(receiver),
                any(),
                isNull(),
                isNull(),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } returns null

        AutoFinishOnSleep(activity).start()
        receiver.captured.onReceive(activity, Intent(Intent.ACTION_SCREEN_OFF))
    }
}
