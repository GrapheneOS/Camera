package app.grapheneos.camera.domain.camera.usecase

import app.grapheneos.camera.data.camera.session.TorchControl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlinkTimerLightTest {

    private val torch = mockk<TorchControl>(relaxed = true) {
        every { isAvailable } returns true
        every { isOn } returns false
        every { isStrengthSupported } returns true
        every { strengthLevel } returns USER_STRENGTH_LEVEL
    }

    private val blinkTimerLight = BlinkTimerLightImpl(torch = torch)

    @Test
    fun oneFlash_dimsTheTorchForTheWholeBlinkAndPutsItsLevelBack() {
        runTest {
            val switches = recordSwitches()

            blinkTimerLight(flashes = 1)

            assertEquals(listOf(true to 0L, false to 240L, false to 240L), switches)
            verifyOrder {
                torch.setStrengthLevel(1)
                torch.enable(true)
                torch.setStrengthLevel(USER_STRENGTH_LEVEL)
            }
        }
    }

    @Test
    fun twoFlashes_shareTheSameBlink() {
        runTest {
            val switches = recordSwitches()

            blinkTimerLight(flashes = 2)

            assertEquals(
                listOf(true to 0L, false to 80L, true to 160L, false to 240L, false to 240L),
                switches,
            )
        }
    }

    @Test
    fun cancelledMidFlash_turnsTheTorchOffAndPutsItsLevelBack() {
        runTest {
            withTimeoutOrNull(100.milliseconds) { blinkTimerLight(flashes = 1) }

            verifyOrder {
                torch.enable(true)
                torch.enable(false)
                torch.setStrengthLevel(USER_STRENGTH_LEVEL)
            }
        }
    }

    @Test
    fun aTorchTheUserTurnedOn_isLeftAlone() {
        runTest {
            every { torch.isOn } returns true

            blinkTimerLight(flashes = 1)

            verify(exactly = 0) { torch.enable(any()) }
            verify(exactly = 0) { torch.setStrengthLevel(any()) }
        }
    }

    @Test
    fun aCameraWithoutAFlash_staysDark() {
        runTest {
            every { torch.isAvailable } returns false

            blinkTimerLight(flashes = 1)

            verify(exactly = 0) { torch.enable(any()) }
        }
    }

    @Test
    fun aTorchWithoutAdjustableStrength_blinksAtItsOwnLevel() {
        runTest {
            every { torch.isStrengthSupported } returns false

            blinkTimerLight(flashes = 1)

            verify(exactly = 1) { torch.enable(true) }
            verify(exactly = 0) { torch.setStrengthLevel(any()) }
        }
    }

    private fun TestScope.recordSwitches(): MutableList<Pair<Boolean, Long>> {
        val switches = mutableListOf<Pair<Boolean, Long>>()
        every { torch.enable(any()) } answers { switches += firstArg<Boolean>() to currentTime }
        return switches
    }

    private companion object {
        const val USER_STRENGTH_LEVEL = 12
    }
}
