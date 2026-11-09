package app.grapheneos.camera.data.camera.session

import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MeteringPointFactory
import app.grapheneos.camera.data.camera.model.CameraFocusEvent
import app.grapheneos.camera.data.camera.model.CameraFocusRequest
import app.grapheneos.camera.di.core.CameraSessionScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface FocusControl {

    val events: Flow<CameraFocusEvent>

    fun attach(
        control: CameraControl,
        meteringPointFactory: MeteringPointFactory,
    )

    fun detach()

    fun focus(request: CameraFocusRequest)

    fun lock()

    fun cancel()
}

internal class FocusControlImpl @Inject constructor(
    @CameraSessionScope private val scope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : FocusControl {

    private val _events = MutableSharedFlow<CameraFocusEvent>(
        extraBufferCapacity = CAMERA_EVENT_BUFFER_CAPACITY,
    )
    override val events: Flow<CameraFocusEvent> = _events.asSharedFlow()

    private val mainExecutor: Executor = mainDispatcher.asExecutor()
    private var attachment: Attachment? = null
    private var activeRequest: ActiveRequest? = null
    private var autoCancelJob: Job? = null

    override fun attach(
        control: CameraControl,
        meteringPointFactory: MeteringPointFactory,
    ) {
        detach()
        attachment = Attachment(
            control = control,
            meteringPointFactory = meteringPointFactory,
        )
    }

    override fun detach() {
        releaseRequest()
        attachment = null
    }

    override fun focus(request: CameraFocusRequest) {
        releaseRequest()

        val attached = attachment
        if (attached == null) {
            emit(CameraFocusEvent.Cancelled(requestId = request.id))
            return
        }

        val active = ActiveRequest(
            id = request.id,
            point = attached.meteringPointFactory.createPoint(request.x, request.y),
            isLocked = request.isLocked,
        )
        activeRequest = active
        emit(CameraFocusEvent.Started(requestId = active.id))
        when {
            active.isLocked -> emit(CameraFocusEvent.Locked(requestId = active.id))
            else -> scheduleAutoCancel(request.autoCancelAfter)
        }
        submit(active)
    }

    override fun lock() {
        val request = activeRequest ?: return

        stopAutoCancel()
        val locked = request.copy(isLocked = true)
        activeRequest = locked
        emit(CameraFocusEvent.Locked(requestId = locked.id))
        submit(locked)
    }

    override fun cancel() {
        if (activeRequest == null) return

        releaseRequest()
        attachment?.control?.cancelFocusAndMetering()
    }

    private fun releaseRequest() {
        stopAutoCancel()
        val request = activeRequest ?: return

        activeRequest = null
        emit(CameraFocusEvent.Cancelled(requestId = request.id))
    }

    private fun scheduleAutoCancel(autoCancelAfter: Duration) {
        if (autoCancelAfter <= Duration.ZERO) return

        autoCancelJob = scope.launch(mainDispatcher) {
            delay(autoCancelAfter)
            cancel()
        }
    }

    private fun stopAutoCancel() {
        autoCancelJob?.cancel()
        autoCancelJob = null
    }

    private fun submit(request: ActiveRequest) {
        val control = attachment?.control ?: return

        val result = control.startFocusAndMetering(fixedFocusAction(request.point))
        val deliverResult = { onResult(request = request, result = result) }

        result.addListener(deliverResult, mainExecutor)
    }

    private fun onResult(
        request: ActiveRequest,
        result: ListenableFuture<FocusMeteringResult>,
    ) {
        if (activeRequest !== request) return

        when (outcomeOf(result)) {
            FocusOutcome.FOCUSED -> emit(CameraFocusEvent.Focused(requestId = request.id))
            FocusOutcome.CANCELLED -> releaseRequest()
            FocusOutcome.NOT_FOCUSED -> Unit
        }
    }

    private fun emit(event: CameraFocusEvent) {
        _events.tryEmit(event)
    }

    private fun fixedFocusAction(point: MeteringPoint): FocusMeteringAction {
        return FocusMeteringAction.Builder(point).disableAutoCancel().build()
    }

    private fun outcomeOf(result: ListenableFuture<FocusMeteringResult>): FocusOutcome {
        if (result.isCancelled) return FocusOutcome.CANCELLED

        return try {
            when {
                result.get().isFocusSuccessful -> FocusOutcome.FOCUSED
                else -> FocusOutcome.NOT_FOCUSED
            }
        } catch (exception: ExecutionException) {
            when (exception.cause) {
                is CameraControl.OperationCanceledException -> FocusOutcome.CANCELLED
                else -> FocusOutcome.NOT_FOCUSED
            }
        }
    }

    private enum class FocusOutcome {
        FOCUSED,
        NOT_FOCUSED,
        CANCELLED,
    }

    private data class Attachment(
        val control: CameraControl,
        val meteringPointFactory: MeteringPointFactory,
    )

    private data class ActiveRequest(
        val id: Long,
        val point: MeteringPoint,
        val isLocked: Boolean,
    )
}
