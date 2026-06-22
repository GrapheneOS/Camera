package app.grapheneos.camera.util

import android.util.Log
import java.io.ByteArrayOutputStream


/**
 * Data class to hold the extracted ICC profile information.
 */
data class IccProfile(
    val start: Int,       // Start index of the APP2 section containing the ICC profile
    val end: Int,         // End index of the APP2 section containing the ICC profile
    // The full ICC profile bytes segment [signature + header + data] (this will be smaller than the total APP2 section)
    val iccBytes: ByteArray
)

/**
 * Extracts the ICC profile from a JPEG byte array.
 * Based off iccDEV -> iccJpegDump
 * @param jpegBytes   The JPEG file contents.
 * @return            The ICC profile bytes, or null if none was found.
 */
fun extractIccFromJpeg(jpegBytes: ByteArray): IccProfile? {
    val tag = "ICC_PROFILE"

    if (jpegBytes.size < 2 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
        Log.e(tag, "Input data is not a valid JPEG (missing SOI).")
        return null
    }

    val iccSig = byteArrayOf(
        'I'.code.toByte(), 'C'.code.toByte(), 'C'.code.toByte(), '_'.code.toByte(),
        'P'.code.toByte(), 'R'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte(),
        'I'.code.toByte(), 'L'.code.toByte(), 'E'.code.toByte(), 0x00.toByte()
    )   // "ICC_PROFILE\0"

    var pos = 2     // skip SOI
    var totalChunks = 0
    var seenChunks: BooleanArray? = null
    val chunks: MutableList<ByteArray?> = mutableListOf()

    // Track indices within the original jpegBytes
    var minStart = Int.MAX_VALUE
    var maxEnd = 0

    while (pos < jpegBytes.size) {
        if (jpegBytes[pos] != 0xFF.toByte()) {
            pos++
            continue  // resync – skip stray byte
        }

        val magicPos = pos //FF magic marks beginning of section before marker

        // Skip any padding 0xFF bytes that can appear before the marker code
        while (pos < jpegBytes.size && jpegBytes[pos] == 0xFF.toByte()) pos++
        if (pos >= jpegBytes.size) break

        //Log.d(tag, "marker - %d: %02x %02x".format(markerPos, jpegBytes[markerPos].toInt() and 0xFF, jpegBytes[markerPos+1].toInt() and 0xFF))
        val marker = jpegBytes[pos].toInt() and 0xFF
        pos++   // move past the marker code

        // EOI, or SOS (entropy data follows)
        if (marker == 0xD9 || marker == 0xDA) break

        // standalone markers (no length)
        if (marker == 0x01 || (marker in 0xD0..0xD7)) continue

        if (pos + 1 >= jpegBytes.size) break    // malformed JPEG

        val segLength = ((jpegBytes[pos].toInt() and 0xFF) shl 8) or
                (jpegBytes[pos + 1].toInt() and 0xFF)
        pos += 2

        if (segLength < 2 || pos + (segLength - 2) > jpegBytes.size) break

        val payloadLength = segLength - 2
        val segStart = pos

        // Handle APP2 segments that may contain ICC data
        if (marker == 0xE2 && payloadLength >= 14) {
            if (jpegBytes.copyOfRange(segStart, segStart + 12).contentEquals(iccSig)) {
                //Log.d(tag, "sig found @ $segStart. starting pos $magicPos. Payload length $payloadLength bytes")
                val seq = jpegBytes[segStart + 12].toInt() and 0xFF
                val total = jpegBytes[segStart + 13].toInt() and 0xFF

                if (seq == 0 || total == 0 || seq > total) {
                    Log.e(tag, "Invalid ICC_PROFILE APP2 chunk sequence (seq=$seq, total=$total).")
                    return null
                }

                if (totalChunks == 0) {
                    totalChunks = total
                    chunks.clear()
                    repeat(total) { chunks.add(null) }
                    seenChunks = BooleanArray(total) { false }
                } else if (total != totalChunks) {
                    Log.e(tag, "Inconsistent ICC_PROFILE APP2 chunk count (expected $totalChunks, found $total).")
                    return null
                }

                if (seenChunks!![seq - 1]) {
                    Log.e(tag, "Duplicate ICC_PROFILE APP2 chunk number $seq.")
                    return null
                }

                val chunkEnd = segStart + payloadLength
                if (magicPos < minStart) minStart = magicPos
                if (chunkEnd > maxEnd) maxEnd = chunkEnd

                // Include header in the returned bytes,
                // store the FULL segment (signature + header + data) for the first chunk,
                // and only the data for subsequent chunks.
                val chunkData = if (seq == 1) {
                    jpegBytes.copyOfRange(segStart, segStart + payloadLength)
                } else {
                    jpegBytes.copyOfRange(segStart + 14, segStart + payloadLength)
                }

                chunks[seq - 1] = chunkData
                seenChunks[seq - 1] = true

                val chunkSize = if (seq == 1) payloadLength - 14 else payloadLength //exclude header on first section
                Log.d(tag, "ICC_PROFILE APP2 chunk ${seq}/${total} (${chunkSize} bytes). found at [$segStart-$chunkEnd]. (${chunkEnd-segStart} bytes)")
            }
        }

        // Advance to next marker
        pos += payloadLength
    }

    if (totalChunks == 0) {
        Log.e(tag, "No ICC_PROFILE APP2 markers found in JPEG.")
        return null
    }

    for ((index, seen) in seenChunks!!.withIndex()) {
        if (!seen) {
            Log.e(tag, "Missing ICC_PROFILE APP2 chunk number ${index + 1}.")
            return null
        }
    }

    val iccStream = ByteArrayOutputStream()
    for (chunk in chunks) {
        chunk?.let { iccStream.write(it) }
    }
    val iccBytes = iccStream.toByteArray()

    //debug output
    /*
    Log.d(tag, "--- ICC Profile Extraction Complete ---")
    Log.d(tag, "Start Index: $minStart")
    Log.d(tag, "End Index: $maxEnd")
    Log.d(tag, "Total APP2 size: ${maxEnd - minStart}")
    Log.d(tag, "ICC Size:  ${iccBytes.size} bytes") //including header
    val preview = iccBytes.joinToString(" ") { "%02X".format(it) }
    Log.d(tag, "Data Preview: $preview...")
    Log.d(tag, "---------------------------------------")*/
    Log.d(tag, "ICC Profile found! Size: ${iccBytes.size} bytes. Total chunks: ${totalChunks}.")

    return IccProfile(minStart, maxEnd - minStart, iccBytes)
}

/**
 * Returns a new ByteArray with the specified section removed.
 *
 * @param original The source byte array.
 * @param start The starting index of the section to remove.
 * @param length The number of bytes to remove.
 * @return A new ByteArray containing the remaining bytes.
 */
fun stripBytes(original: ByteArray, start: Int, length: Int): ByteArray {
    if (length <= 0) return original.copyOf()

    require(start >= 0) { "Start index must be non-negative (was $start)" }
    require(length >= 0) { "Length must be non-negative (was $length)" }
    require(start + length <= original.size) {
        "Strip range ($start to ${start + length}) exceeds array size (${original.size})"
    }

    /*
    Log.d("ICC strip","start - %d: %02x".format(start, original[start].toInt() and 0xFF))
    Log.d("ICC strip","end - %d: %02x".format(start+length, original[start+length].toInt() and 0xFF))
    Log.d("ICC strip","length   - $length:")*/
    val newSize = original.size - length
    val result = ByteArray(newSize)

    // copy section BEFORE
    original.copyInto(result, destinationOffset = 0, startIndex = 0, endIndex = start)
    // copy section AFTER
    original.copyInto(result, destinationOffset = start, startIndex = start + length)

    return result
}
