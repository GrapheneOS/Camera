package app.grapheneos.camera.domain.capture.coordinator

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturedImagePipelineTest {

    private val finished = mutableListOf<String>()

    private val stages = Job()

    @After
    fun stopStages() {
        stages.cancel()
    }

    @Test
    fun enqueueWrite_finishesWritesInTheOrderTheyWereQueued() {
        runTest {
            val pipeline = pipeline()

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
    }

    @Test
    fun enqueueThumbnail_doesNotHoldBackTheNextWrite() {
        runTest {
            val pipeline = pipeline()

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
    }

    @Test
    fun enqueueExtraction_doesNotWaitForTheWriteBeforeIt() {
        runTest {
            val pipeline = pipeline()

            pipeline.enqueueExtraction {
                finished += "first extraction"
                pipeline.enqueueWrite {
                    delay(SLOW_WRITE_MS)
                    finished += "first write"
                }
            }
            pipeline.enqueueExtraction {
                delay(FAST_WRITE_MS)
                finished += "second extraction"
            }
            advanceUntilIdle()

            assertEquals(
                listOf("first extraction", "second extraction", "first write"),
                finished,
            )
        }
    }

    @Test
    fun enqueueThumbnail_finishesThumbnailsInTheOrderTheyWereQueued() {
        runTest {
            val pipeline = pipeline()

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
    }

    private fun TestScope.pipeline(): CapturedImagePipeline {
        return CapturedImagePipelineImpl(
            applicationScope = CoroutineScope(coroutineContext + stages),
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private companion object {
        val SLOW_WRITE_MS = 100.milliseconds
        val FAST_WRITE_MS = 10.milliseconds
    }
}
