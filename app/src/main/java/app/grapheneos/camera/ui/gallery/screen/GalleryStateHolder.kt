package app.grapheneos.camera.ui.gallery.screen

import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import app.grapheneos.camera.ui.gallery.screen.model.GalleryScreenEffect as Effect
import app.grapheneos.camera.ui.gallery.screen.model.GalleryState

internal class GalleryStateHolder(
    initial: GalleryState,
) : ScreenStateHolder<GalleryState, Effect>(initial) {

    override fun send(effect: Effect) {
        if (effect is Effect.OtherApp && state.value.isSecureSession) return

        super.send(effect)
    }
}
