package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ExposureControl {

    /** Shows pending compensation immediately and restores the last confirmed value on failure. */
    val exposure: StateFlow<CameraExposure?>

    fun attach(camera: Camera)

    fun detach()

    fun setCompensationIndex(index: Int)
}

internal class ExposureControlImpl @Inject constructor(
    private val cameraXStateMapper: CameraXStateMapper,
    @MainImmediateDispatcher mainDispatcher: CoroutineDispatcher,
) : ExposureControl {

    private val _exposure = MutableStateFlow<CameraExposure?>(null)
    override val exposure: StateFlow<CameraExposure?> = _exposure.asStateFlow()

    private val mainExecutor: Executor = mainDispatcher.asExecutor()

    private var attachment: Attachment? = null

    override fun attach(camera: Camera) {
        detach()
        val exposure = cameraXStateMapper.map(camera.cameraInfo.exposureState)
        attachment = Attachment(
            control = camera.cameraControl,
            confirmedExposure = exposure,
        )
        _exposure.value = exposure
    }

    override fun detach() {
        attachment = null
        _exposure.value = null
    }

    override fun setCompensationIndex(index: Int) {
        val attached = attachment ?: return
        val exposure = attached.confirmedExposure
        if (!exposure.isAdjustable() || index !in exposure.compensationRange) return

        // CameraX fails the request this one replaces before it returns, and that listener runs
        // inside the call: the request has to be the latest one by then.
        val request = attached.lastRequest + 1
        attached.lastRequest = request
        _exposure.value = exposure.copy(compensationIndex = index)

        val result = attached.control.setExposureCompensationIndex(index)
        result.addListener(
            {
                onResult(
                    attachment = attached,
                    request = request,
                    result = result,
                )
            },
            mainExecutor,
        )
    }

    private fun onResult(
        attachment: Attachment,
        request: Int,
        result: ListenableFuture<Int>,
    ) {
        if (this.attachment !== attachment) return

        val index = confirmedIndex(result)
        if (index != null) {
            attachment.confirmedExposure = attachment.confirmedExposure.copy(
                compensationIndex = index,
            )
        }
        if (attachment.lastRequest == request) {
            _exposure.value = attachment.confirmedExposure
        }
    }

    private fun confirmedIndex(result: ListenableFuture<Int>): Int? {
        return try {
            result.get()
        } catch (_: ExecutionException) {
            null
        } catch (_: CancellationException) {
            null
        }
    }

    private class Attachment(
        val control: CameraControl,
        var confirmedExposure: CameraExposure,
    ) {
        var lastRequest = 0
    }
}
