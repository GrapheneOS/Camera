package app.grapheneos.camera.domain.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedImagePipelineTest {

    private val finished = mutableListOf<String>()

    @Test
    fun enqueueWrite_finishesWritesInTheOrderTheyWereQueued() = runTest {
        val pipeline = pipeline(backgroundScope)

        pipeline.enqueueWrite {
            delay(SLOW_WRITE_MS)
            finished += "first write"
        }
        pipeline.enqueueWrite {
            delay(FAST_WRITE_MS)
            finished += "second write"
        }
        advanceUntilIdle()

        assertEquals(listOf("first write", "second write"), finished)
    }

    @Test
    fun enqueueThumbnail_doesNotHoldBackTheNextWrite() = runTest {
        val pipeline = pipeline(backgroundScope)

        pipeline.enqueueWrite {
            finished += "first write"
            pipeline.enqueueThumbnail {
                delay(SLOW_WRITE_MS)
                finished += "first thumbnail"
            }
        }
        pipeline.enqueueWrite {
            delay(FAST_WRITE_MS)
            finished += "second write"
        }
        advanceUntilIdle()

        assertEquals(listOf("first write", "second write", "first thumbnail"), finished)
    }

    @Test
    fun enqueueThumbnail_finishesThumbnailsInTheOrderTheyWereQueued() = runTest {
        val pipeline = pipeline(backgroundScope)

        pipeline.enqueueThumbnail {
            delay(SLOW_WRITE_MS)
            finished += "first thumbnail"
        }
        pipeline.enqueueThumbnail {
            finished += "second thumbnail"
        }
        advanceUntilIdle()

        assertEquals(listOf("first thumbnail", "second thumbnail"), finished)
    }

    private fun TestScope.pipeline(scope: CoroutineScope): CapturedImagePipeline {
        return CapturedImagePipelineImpl(
            applicationScope = scope,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private companion object {
        const val SLOW_WRITE_MS = 100L
        const val FAST_WRITE_MS = 10L
    }
}
