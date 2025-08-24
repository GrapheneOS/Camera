package app.grapheneos.camera.inputfilter

// Ensures that the field is within the min/max limits for a number type input field
class NumLimitFilter(
    val min: Float,
    val max: Float,
    val onOutOfRange: () -> Unit,
) : CustomNumFilter(
    shouldAcceptNumber = { num ->
        if (num in min..max) {
            true
        } else {
            onOutOfRange()
            false
        }
    }
) {
    constructor(min: Int, max: Int, onOutOfRange: () -> Unit) : this(min.toFloat(), max.toFloat(), onOutOfRange)
}