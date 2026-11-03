package app.grapheneos.camera.data.core.model

enum class LensFacing {
    FRONT,
    BACK,
    ;

    fun opposite(): LensFacing {
        return when (this) {
            FRONT -> BACK
            BACK -> FRONT
        }
    }

    fun supportedOrOpposite(isSupported: (lensFacing: LensFacing) -> Boolean): LensFacing {
        return when {
            isSupported(this) -> this
            else -> opposite()
        }
    }
}
