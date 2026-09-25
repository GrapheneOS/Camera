package app.grapheneos.camera.testutil

internal const val CAPTURED_IMAGE_SIZE = 16

internal fun capturedImageBytes(): ByteArray {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(FIXTURE_NAME)

    return requireNotNull(stream) { "missing the $FIXTURE_NAME test fixture" }.use {
        it.readBytes()
    }
}

private const val FIXTURE_NAME = "captured_image.jpg"
