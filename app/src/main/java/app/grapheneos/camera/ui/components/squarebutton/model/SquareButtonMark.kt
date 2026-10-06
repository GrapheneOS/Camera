package app.grapheneos.camera.ui.components.squarebutton.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterCore
import app.grapheneos.camera.ui.components.shuttercore.model.ShutterTone

@Immutable
internal sealed interface SquareButtonMark {

    data class Icon(
        val icon: ImageVector,
    ) : SquareButtonMark

    data class Core(
        val core: ShutterCore,
        val tone: ShutterTone,
    ) : SquareButtonMark
}
