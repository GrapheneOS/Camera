package app.grapheneos.camera

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.data.sound.player.CameraSoundPlayer
import app.grapheneos.camera.data.sound.player.CameraSoundPlayerImpl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StalledMediaserverRegressionTest {

    /** The kinds of call a sound makes into mediaserver. */
    private enum class Call { LOAD, PREPARE, PLAY }

    /** Stands in for a mediaserver that stops answering one kind of call until [answer]. */
    private class Mediaserver(
        private val stalled: Call,
    ) {
        private val stalledCallMade = CountDownLatch(1)
        private val answered = CountDownLatch(1)

        val prepared = CountDownLatch(CameraSound.entries.size)

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
    private class StalledMediaPlayer(
        private val mediaserver: Mediaserver,
    ) : MediaPlayer() {
        override fun setDataSource(afd: AssetFileDescriptor) {
            mediaserver.receive(Call.LOAD)
            super.setDataSource(afd)
        }

        // The prepared event asks mediaserver for the track list just before calling the
        // listener, on the thread the event is delivered to
        override fun setOnPreparedListener(listener: OnPreparedListener?) {
            super.setOnPreparedListener(
                listener?.let {
                    OnPreparedListener { player ->
                        mediaserver.receive(Call.PREPARE)
                        it.onPrepared(player)
                        mediaserver.prepared.countDown()
                    }
                },
            )
        }

        override fun start() {
            mediaserver.receive(Call.PLAY)
            super.start()
        }
    }

    private var mediaserver: Mediaserver? = null

    private fun createSounds(stalling: Call): Pair<Mediaserver, CameraSoundPlayer> {
        val mediaserver = Mediaserver(stalling)
        this.mediaserver = mediaserver

        lateinit var player: CameraSoundPlayer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player = CameraSoundPlayerImpl(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                newMediaPlayer = { StalledMediaPlayer(mediaserver) },
            )
        }
        return mediaserver to player
    }

    private fun mainThreadIsFree(): Boolean {
        val ran = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { ran.countDown() }
        return ran.await(INPUT_DISPATCH_TIMEOUT_S, TimeUnit.SECONDS)
    }

    @After
    fun answer() {
        mediaserver?.answer()
    }

    @Test
    fun loadingSounds_whileMediaserverStalls_leavesTheMainThreadFree() {
        val (mediaserver, _) = createSounds(stalling = Call.LOAD)
        assertTrue("No sound was loaded", mediaserver.awaitStalledCall())

        assertTrue(
            "Loading a sound held the main thread while mediaserver was stalled",
            mainThreadIsFree(),
        )

        mediaserver.answer()
        assertTrue(
            "The sounds did not load once mediaserver answered",
            mediaserver.prepared.await(TIMEOUT_S, TimeUnit.SECONDS),
        )
    }

    /** A player delivers its prepared event to the thread it was created on, or to main. */
    @Test
    fun preparingSounds_whileMediaserverStalls_leavesTheMainThreadFree() {
        val (mediaserver, _) = createSounds(stalling = Call.PREPARE)
        assertTrue("No sound reached the prepared event", mediaserver.awaitStalledCall())

        assertTrue(
            "Preparing a sound held the main thread while mediaserver was stalled",
            mainThreadIsFree(),
        )
    }

    @Test
    fun playingASound_whileMediaserverStalls_leavesTheMainThreadFree() {
        val (mediaserver, player) = createSounds(stalling = Call.PLAY)
        assertTrue(
            "The sounds did not load",
            mediaserver.prepared.await(TIMEOUT_S, TimeUnit.SECONDS),
        )

        Handler(Looper.getMainLooper()).post { player.play(CameraSound.SHUTTER) }
        assertTrue("The shutter sound was not played", mediaserver.awaitStalledCall())

        assertTrue(
            "Playing a sound held the main thread while mediaserver was stalled",
            mainThreadIsFree(),
        )
    }

    private companion object {
        // Twice the input dispatch timeout
        const val TIMEOUT_S = 10L

        // The system reports an ANR once a key has waited this long for the main thread
        const val INPUT_DISPATCH_TIMEOUT_S = 5L
    }
}
