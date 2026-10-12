package app.grapheneos.camera

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class StalledMediaserverRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /** The kinds of call a sound makes into mediaserver. */
    private enum class Call { LOAD, PREPARE, PLAY }

    /** Stands in for a mediaserver that stops answering one kind of call until [answer]. */
    private class Mediaserver(private val stalled: Call) {
        private val stalledCallMade = CountDownLatch(1)
        private val answered = CountDownLatch(1)

        val prepared = CountDownLatch(SOUNDS)

        fun receive(call: Call) {
            if (call != stalled) return
            stalledCallMade.countDown()
            answered.await()
        }

        fun awaitStalledCall() = stalledCallMade.await(TIMEOUT_S, TimeUnit.SECONDS)

        fun answer() = answered.countDown()
    }

    /**
     * Sends its calls through [mediaserver] first. Each is a synchronous binder transaction into
     * it, so a stall holds whichever thread made the call.
     */
    private class StalledMediaPlayer(private val mediaserver: Mediaserver) : MediaPlayer() {
        override fun setDataSource(context: Context, uri: Uri) {
            mediaserver.receive(Call.LOAD)
            super.setDataSource(context, uri)
        }

        // The prepared event asks mediaserver for the track list just before calling the
        // listener, on the thread the event is delivered to
        override fun setOnPreparedListener(listener: OnPreparedListener?) {
            super.setOnPreparedListener(listener?.let {
                OnPreparedListener { player ->
                    mediaserver.receive(Call.PREPARE)
                    it.onPrepared(player)
                    mediaserver.prepared.countDown()
                }
            })
        }

        override fun start() {
            mediaserver.receive(Call.PLAY)
            super.start()
        }
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val resumed = CountDownLatch(1)
    @Volatile
    private var camera: MainActivity? = null
    private val watcher = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) {
                camera = activity
                resumed.countDown()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    private var mediaserver: Mediaserver? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private var launcher: Thread? = null

    private fun launchCamera(stalling: Call): Mediaserver {
        val mediaserver = Mediaserver(stalling)
        this.mediaserver = mediaserver
        app.registerActivityLifecycleCallbacks(watcher)
        TunePlayer.newMediaPlayer = { StalledMediaPlayer(mediaserver) }
        // launch() returns only once the activity resumed, which a camera stuck on mediaserver
        // doesn't do until the stall ends
        launcher = thread { scenario = ActivityScenario.launch(MainActivity::class.java) }
        return mediaserver
    }

    private fun mainThreadIsFree(): Boolean {
        val ran = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { ran.countDown() }
        return ran.await(INPUT_DISPATCH_TIMEOUT_S, TimeUnit.SECONDS)
    }

    @After
    fun answerAndClose() {
        mediaserver?.answer()
        launcher?.join()
        scenario?.close()
        TunePlayer.newMediaPlayer = ::MediaPlayer
        app.unregisterActivityLifecycleCallbacks(watcher)
    }

    /**
     * The camera loaded its sounds from onCreate, so a mediaserver that took seconds to answer kept
     * the window from appearing, and a key pressed meanwhile was reported as an ANR.
     */
    @Test
    fun loadingSounds_whileMediaserverStalls_stillLetsTheCameraResume() {
        val mediaserver = launchCamera(stalling = Call.LOAD)
        assertTrue(
            "The camera did not resume while mediaserver was stalled",
            resumed.await(TIMEOUT_S, TimeUnit.SECONDS)
        )

        mediaserver.answer()
        assertTrue(
            "The sounds did not load once mediaserver answered",
            mediaserver.prepared.await(TIMEOUT_S, TimeUnit.SECONDS)
        )
    }

    /** A player delivers its prepared event to the thread it was created on, or to main. */
    @Test
    fun preparingSounds_whileMediaserverStalls_leavesTheMainThreadFree() {
        val mediaserver = launchCamera(stalling = Call.PREPARE)
        assertTrue("No sound reached the prepared event", mediaserver.awaitStalledCall())

        assertTrue(
            "Preparing a sound held the main thread while mediaserver was stalled",
            mainThreadIsFree()
        )
    }

    @Test
    fun playingASound_whileMediaserverStalls_leavesTheMainThreadFree() {
        val mediaserver = launchCamera(stalling = Call.PLAY)
        assertTrue("The camera did not resume", resumed.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertTrue("The sounds did not load", mediaserver.prepared.await(TIMEOUT_S, TimeUnit.SECONDS))

        val player = camera!!.camConfig.mPlayer
        Handler(Looper.getMainLooper()).post { player.playShutterSound() }
        assertTrue("The shutter sound was not played", mediaserver.awaitStalledCall())

        assertTrue(
            "Playing a sound held the main thread while mediaserver was stalled",
            mainThreadIsFree()
        )
    }

    private companion object {
        // TunePlayer's shutter, focus, two timer, and two recording sounds
        const val SOUNDS = 6

        // Twice the input dispatch timeout. A camera blocked on the stall never gets there, so
        // this only has to cover a cold start.
        const val TIMEOUT_S = 10L

        // The system reports an ANR once a key has waited this long for the main thread
        const val INPUT_DISPATCH_TIMEOUT_S = 5L
    }
}
