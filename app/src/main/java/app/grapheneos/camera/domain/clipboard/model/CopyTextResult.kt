package app.grapheneos.camera.domain.clipboard.model

sealed interface CopyTextResult {

    data object ConfirmedBySystem : CopyTextResult

    data object Unconfirmed : CopyTextResult
}
