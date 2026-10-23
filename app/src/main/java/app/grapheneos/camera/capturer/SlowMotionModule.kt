package app.grapheneos.camera.capturer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Process
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/** Records separate mono PCM because CameraX high-speed slow-motion only retimes video frames.
 * The WAV/PCM file never enters public storage; it is discarded after finalization.
 *
 * Failures MUST NOT prevent the existing silent CameraX MP4 from being published.
 */
internal class SlowMotionModule(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 48_000
        private const val TAG = "SlowMotionAudio"
    }

    val pcmFile: File = File.createTempFile("fold-slomo-mic-", ".pcm", context.cacheDir)
    private val stopped = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val bufferBytes = max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            SAMPLE_RATE / 5 * 2
        )
        try {
            val audio = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes * 2
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                return false
            }
            recorder = audio
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audio.release()
                recorder = null
                return false
            }
            started.set(true)
            worker = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val data = ByteArray(bufferBytes)
                try {
                    FileOutputStream(pcmFile).buffered().use { out ->
                        while (!stopped.get()) {
                            val size = audio.read(data, 0, data.size)
                            if (size > 0) out.write(data, 0, size and -2)
                            else if (size < 0) break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio capture interrupted", e)
                }
            }, "FoldSlowMotionMic").also { it.start() }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Microphone unavailable during high-speed capture", e)
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            return false
        }
    }

    /** Stop acquisition immediately when user presses stop (not when video muxer drains). */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        try { recorder?.stop() } catch (_: Exception) {}
    }

    /** Must be called on a background executor; audio I/O never blocks the camera UI. */
    fun finish(): File? {
        stop()
        try { worker?.join(2000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        if (!started.get() || pcmFile.length() < 1024 || worker?.isAlive == true) return null
        return pcmFile
    }

    fun discard() {
        stop()
        try { worker?.join(2000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        pcmFile.delete()
    }
}

/** Takes a finalized, silent, CameraX MP4 and creates a *different* MP4 in cache.
 * Audio is played back at (30 / captureFps) speed and thus is lower-pitched too.
 * Video is REMUXED losslessly, never transcoded. The caller owns publication and cleanup.
 * Streaming PCM and MediaCodec output keep memory bounded even for long clips.
 */
internal object SlowMotionMuxer {
    private const val TAG = "SlowMotionAudio"
    private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val INPUT_CHUNK_SAMPLES = 4096

    fun combine(context: Context, silentMp4Uri: Uri, pcmFile: File, captureFps: Int): File? {
        if (!pcmFile.isFile || pcmFile.length() < 1024 || captureFps < 60) return null
        val slowdown = max(1, captureFps / 30)
        val out = File.createTempFile("fold-slomo-mux-", ".mp4", context.cacheDir)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var muxerStarted = false
        var success = false
        try {
            extractor.setDataSource(context, silentMp4Uri, null)
            val videoIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            val videoFormat = extractor.getTrackFormat(videoIndex)
            val durationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION))
                videoFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            // If a codec omits duration, the audio duration is derived from the microphone PCM.
            val targetSamples = if (durationUs > 0L)
                durationUs * SlowMotionModule.SAMPLE_RATE / 1_000_000L
                else (pcmFile.length() / 2L) * slowdown
            if (targetSamples < 1024L) return null

            val writer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val videoTrack = writer.addTrack(videoFormat)
            val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME, SlowMotionModule.SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
            encoder = codec
            codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val stretcher = PcmStretchReader(pcmFile, slowdown)
            var sentSamples = 0L
            var inputFinished = false
            var outputFinished = false
            var audioTrack = -1
            stretcher.use { source ->
                while (!outputFinished) {
                    if (!inputFinished) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = codec.getInputBuffer(inputIndex)!!
                            input.clear()
                            val framesToSend = min(input.remaining() / 2L, targetSamples - sentSamples).toInt()
                            val count = if (framesToSend > 0) source.readSamples(input, framesToSend) else 0
                            // Pad small capture/stop timing differences with silence; leave no
                            // unexpected gaps in a long slow-motion soundtrack.
                            if (count < framesToSend) {
                                repeat(framesToSend - count) { input.putShort(0) }
                            }
                            val actual = max(0, framesToSend)
                            val ptsUs = sentSamples * 1_000_000L / SlowMotionModule.SAMPLE_RATE
                            if (actual == 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputFinished = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, actual * 2, ptsUs, 0)
                                sentSamples += actual
                            }
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrack = writer.addTrack(codec.outputFormat)
                            writer.start()
                            muxerStarted = true
                        }
                        else -> if (outputIndex >= 0) {
                            val encoded = codec.getOutputBuffer(outputIndex)!!
                            if (info.size > 0 && muxerStarted &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                encoded.position(info.offset)
                                encoded.limit(info.offset + info.size)
                                writer.writeSampleData(audioTrack, encoded, info)
                            }
                            outputFinished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            if (!muxerStarted) return null

            // Copy compressed video samples without any quality loss.
            extractor.selectTrack(videoIndex)
            val videoBuffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
            val videoInfo = MediaCodec.BufferInfo()
            while (true) {
                videoBuffer.clear()
                val size = extractor.readSampleData(videoBuffer, 0)
                if (size < 0) break
                if (size >= videoBuffer.capacity()) error("Video sample exceeds mux buffer")
                videoInfo.set(0, size, extractor.sampleTime,
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                videoBuffer.position(0)
                videoBuffer.limit(size)
                writer.writeSampleData(videoTrack, videoBuffer, videoInfo)
                extractor.advance()
            }
            writer.stop()
            muxerStarted = false
            success = out.length() > 4096
            return if (success) out else null
        } catch (e: Exception) {
            Log.e(TAG, "Audio stretch/mux failed: original video remains intact", e)
            return null
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
            if (!success) out.delete()
        }
    }

    /** 16-bit mono 48 kHz PCM linear interpolation; the sample clock stays 48 kHz,
     * so repeating each source interval 'factor' times lowers pitch as well as speed.
     */
    private class PcmStretchReader(file: File, private val factor: Int) : AutoCloseable {
        private val stream = BufferedInputStream(FileInputStream(file), 64 * 1024)
        private val chunk = ByteArray(INPUT_CHUNK_SAMPLES * 2)
        private var stretched = ByteArray(0)
        private var offset = 0
        private var exhausted = false

        fun readSamples(dst: ByteBuffer, maxFrames: Int): Int {
            var emitted = 0
            while (emitted < maxFrames) {
                if (offset >= stretched.size && !refill()) break
                val n = min(maxFrames - emitted, (stretched.size - offset) / 2)
                dst.put(stretched, offset, n * 2)
                offset += n * 2
                emitted += n
            }
            return emitted
        }

        private fun refill(): Boolean {
            if (exhausted) return false
            val size = stream.read(chunk) and -2
            if (size <= 0) { exhausted = true; return false }
            val samples = size / 2
            stretched = ByteArray(samples * factor * 2)
            var output = 0
            for (i in 0 until samples) {
                val p = i * 2
                val sample = ((chunk[p + 1].toInt() shl 8) or (chunk[p].toInt() and 255)).toShort().toInt()
                val next = if (i + 1 < samples) {
                    ((chunk[p + 3].toInt() shl 8) or (chunk[p + 2].toInt() and 255)).toShort().toInt()
                } else sample
                for (step in 0 until factor) {
                    val interp = sample + (next - sample) * step / factor
                    stretched[output++] = interp.toByte()
                    stretched[output++] = (interp shr 8).toByte()
                }
            }
            offset = 0
            return true
        }
        override fun close() { stream.close() }
    }
}
