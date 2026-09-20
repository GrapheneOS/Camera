package app.grapheneos.camera.data.media.model

sealed interface CaptureOutputResult<out T> {

    fun valueOrNull(): T?

    data class Success<out T>(
        val value: T,
    ) : CaptureOutputResult<T> {

        override fun valueOrNull(): T {
            return value
        }
    }

    data class Failure(
        val cause: Exception,
    ) : CaptureOutputResult<Nothing> {

        override fun valueOrNull(): Nothing? {
            return null
        }
    }
}
