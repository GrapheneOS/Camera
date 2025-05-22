package app.grapheneos.camera.ktx

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val DEFAULT_BUFFER_SIZE = 0x2000

// TODO: Replace calls with transferTo when minSdk becomes 33 or above
@Throws(IOException::class)
fun InputStream.transfer(out: OutputStream): Long {
    var transferred: Long = 0
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read: Int
    while ((this.read(buffer, 0, DEFAULT_BUFFER_SIZE).also { read = it }) >= 0) {
        out.write(buffer, 0, read)
        transferred += read.toLong()
    }
    return transferred
}