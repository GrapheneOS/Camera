package app.grapheneos.camera.domain.capture.coordinator

import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface CapturedImagePipeline {

    fun enqueueExtraction(extraction: suspend () -> Unit)

    fun enqueueWrite(write: suspend () -> Unit)

    fun enqueueThumbnail(thumbnail: suspend () -> Unit)
}

internal class CapturedImagePipelineImpl @Inject constructor(
    @ApplicationScope applicationScope: CoroutineScope,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : CapturedImagePipeline {

    private val extractions = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)
    private val writes = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)
    private val thumbnails = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    init {
        listOf(
            extractions,
            writes,
            thumbnails,
        ).forEach { stage ->
            applicationScope.launch(defaultDispatcher) {
                for (work in stage) {
                    work()
                }
            }
        }
    }

    override fun enqueueExtraction(extraction: suspend () -> Unit) {
        extractions.trySend(extraction)
    }

    override fun enqueueWrite(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    override fun enqueueThumbnail(thumbnail: suspend () -> Unit) {
        thumbnails.trySend(thumbnail)
    }
}
