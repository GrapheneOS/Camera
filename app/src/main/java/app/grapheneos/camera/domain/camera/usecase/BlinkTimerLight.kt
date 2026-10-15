package app.grapheneos.camera.domain.camera.usecase

import app.grapheneos.camera.data.camera.session.TorchControl
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

interface BlinkTimerLight {
    suspend operator fun invoke(flashes: Int)
}

internal class BlinkTimerLightImpl @Inject constructor(
    private val torch: TorchControl,
) : BlinkTimerLight {

    override suspend fun invoke(flashes: Int) {
        if (!torch.isAvailable || torch.isOn) return

        val strengthLevel = torch.strengthLevel
        val isDimmable = torch.isStrengthSupported

        if (isDimmable) {
            torch.setStrengthLevel(DIM_STRENGTH_LEVEL)
        }

        try {
            blink(flashes)
        } finally {
            torch.enable(false)

            if (isDimmable) {
                torch.setStrengthLevel(strengthLevel)
            }
        }
    }

    private suspend fun blink(flashes: Int) {
        val flash = flashDuration(flashes)

        repeat(flashes) { index ->
            if (index > 0) {
                delay(flash)
            }

            torch.enable(true)
            delay(flash)
            torch.enable(false)
        }
    }

    private fun flashDuration(flashes: Int): Duration {
        return BLINK_DURATION / (flashes * 2 - 1)
    }

    private companion object {
        const val DIM_STRENGTH_LEVEL = 1

        val BLINK_DURATION = 240.milliseconds
    }
}
