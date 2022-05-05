package app.grapheneos.camerax

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.ImageFormat
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.IntRange
import androidx.annotation.NonNull
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.Exif
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability
import androidx.camera.core.internal.utils.ImageUtil
import androidx.camera.core.internal.utils.ImageUtil.CodecFailedException
import androidx.camera.core.internal.utils.ImageUtil.CodecFailedException.FailureType
import androidx.exifinterface.media.ExifInterface
import app.grapheneos.camera.clearExif
import app.grapheneos.camera.fixExif
import app.grapheneos.camerax.OutputFileOptions.OutputFileOptionsMediaStore
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@SuppressLint("RestrictedApi")
class ImageSaver(
    @NonNull val mImage: ImageProxy,
    @NonNull val mOutputFileOptions: OutputFileOptions,
    private val mOrientation: Int,
    @IntRange(from = 1, to = 100) val mJpegQuality: Int,
    @NonNull val mUserCallbackExecutor: Executor,
    @NonNull val mSequentialIoExecutor: Executor,
    @NonNull val mCallback: OnImageSavedCallback
) : Runnable {

    private val pending = 1
    private val notPending = 0

    override fun run() {
        mSequentialIoExecutor.execute {
            val savedUri: Uri? = saveImageToFile()
            postSuccess(savedUri)
        }
    }

    private fun saveImageToFile(): Uri? {
        var saveException: SaveException? = null
        var uri: Uri? = null
        try {
            when (mOutputFileOptions) {
                is OutputFileOptionsMediaStore -> {
                    val (contentResolver, saveCollection, values) = mOutputFileOptions
                    setContentValuePending(values, pending)
                    val outputUri = contentResolver.insert(saveCollection, values)!!
                    saveException = writeImageAndExif(contentResolver, outputUri)
                    setUriNotPending(outputUri, contentResolver)
                    uri = outputUri
                }
                is OutputFileOptions.OutputFileOptionsOutputStream -> {
                    val (contentResolver, outputUri) = mOutputFileOptions
                    saveException = writeImageAndExif(contentResolver, outputUri)
                }
            }
        } catch (e: IOException) {
            saveException = SaveException(
                SaveError.FILE_IO_FAILED,
                "Failed to write destination file.",
                e
            )
        } catch (e: IllegalArgumentException) {
            saveException = SaveException(
                SaveError.FILE_IO_FAILED,
                "Failed to write destination file.",
                e
            )
        } finally {
            mImage.close()
        }

        if (saveException != null) {
            postError(saveException)
        } else {
            postSuccess(uri)
        }

        return uri
    }

    data class SaveException(
        val saveError: SaveError? = null,
        val errorMessage: String? = null,
        val exception: Exception? = null,
    )

    private fun writeImageAndExif(
        contentResolver: ContentResolver, uri: Uri
    ): SaveException? {
        var saveException: SaveException? = null
        try {
            val metadata = mOutputFileOptions.metadata
            contentResolver.openOutputStream(uri)?.use { os ->
                val bytes = imageToJpegByteArray(mImage, mJpegQuality)
                os.write(bytes)
            }

            contentResolver.openAssetFileDescriptor(uri, "rw")?.createInputStream()
                ?.use { inputStream ->
                    val exifSavedImage = Exif.createFromInputStream(inputStream)
                    val exifCapturedImage = Exif.createFromImageProxy(mImage)
                    exifCapturedImage.copyToCroppedImage(exifSavedImage)

                    metadata.location?.let { exifSavedImage.attachLocation(it) }
                    if (metadata.isReversedHorizontal) {
                        exifSavedImage.flipHorizontally()
                    }
                    if (metadata.isReversedVertical) {
                        exifSavedImage.flipVertically()
                    }
                    if (!ExifRotationAvailability().shouldUseExifOrientation(mImage)) {
                        exifSavedImage.rotate(mOrientation)
                    }
                    exifSavedImage.save()
                }
            contentResolver.openAssetFileDescriptor(uri, "rw")?.createInputStream()?.use {
                    ExifInterface(it).fixExif().clearExif().saveAttributes()
                }
        } catch (e: IOException) {
            saveException = SaveException(
                SaveError.FILE_IO_FAILED,
                "Failed to write temp file",
                e
            )
        } catch (e: IllegalArgumentException) {
            saveException = SaveException(
                SaveError.FILE_IO_FAILED,
                "Failed to write temp file",
                e
            )
        } catch (e: CodecFailedException) {
            val saveError: SaveError?
            val errorMessage: String?
            when (e.failureType) {
                FailureType.ENCODE_FAILED -> {
                    saveError = SaveError.ENCODE_FAILED
                    errorMessage = "Failed to encode mImage"
                }
                FailureType.DECODE_FAILED -> {
                    saveError = SaveError.CROP_FAILED
                    errorMessage = "Failed to crop mImage"
                }
                FailureType.UNKNOWN -> {
                    saveError = SaveError.UNKNOWN
                    errorMessage = "Failed to transcode mImage"
                }
                else -> {
                    saveError = SaveError.UNKNOWN
                    errorMessage = "Failed to transcode mImage"
                }
            }
            saveException = SaveException(
                saveError,
                errorMessage,
                e
            )
        } catch (e: UnsupportedEncodingException) {
            saveException = SaveException(
                SaveError.FILE_IO_FAILED,
                "Un supported image format",
                e
            )
        }

        return saveException
    }

    @Throws(UnsupportedEncodingException::class)
    private fun imageToJpegByteArray(
        image: ImageProxy,
        @IntRange(from = 1, to = 100) jpegQuality: Int
    ): ByteArray {
        val shouldCropImage = ImageUtil.shouldCropImage(image)
        when (val imageFormat = image.format) {
            ImageFormat.JPEG -> {
                return if (!shouldCropImage) {
                    ImageUtil.jpegImageToJpegByteArray(image)
                } else {
                    ImageUtil.jpegImageToJpegByteArray(image, image.cropRect, jpegQuality)
                }
            }
            ImageFormat.YUV_420_888 -> {
                return ImageUtil.yuvImageToJpegByteArray(
                    image,
                    if (shouldCropImage) image.cropRect else null,
                    jpegQuality
                )
            }
            else -> {
                println("Unrecognized image format: $imageFormat")
                throw UnsupportedEncodingException("Unrecognized image format: $imageFormat")
            }
        }
    }


    private fun setUriNotPending(outputUri: Uri, resolver: ContentResolver) {
        val values = ContentValues()
        setContentValuePending(values, notPending)
        resolver.update(outputUri, values, null, null)
    }

    private fun setContentValuePending(values: ContentValues, isPending: Int) {
        values.put(MediaStore.Images.Media.IS_PENDING, isPending)
    }

    private fun postSuccess(outputUri: Uri?) {
        try {
            mUserCallbackExecutor.execute { mCallback.onImageSaved(outputUri) }
        } catch (e: RejectedExecutionException) {
            e.fillInStackTrace()
            println(
                "Application executor rejected executing OnImageSavedCallback.onImageSaved "
                        + "callback. Skipping."
            )
        }
    }

    private fun postError(
        saveException: SaveException?
    ) {
        try {
            mUserCallbackExecutor.execute {
                mCallback.onError(
                    saveException?.saveError ?: SaveError.UNKNOWN,
                    saveException?.errorMessage ?: "",
                    saveException?.exception
                )
            }
        } catch (e: RejectedExecutionException) {
            e.fillInStackTrace()
            println(
                "Application executor rejected executing OnImageSavedCallback.onError "
                        + "callback. Skipping."
            )
        }
    }

    enum class SaveError {
        FILE_IO_FAILED, ENCODE_FAILED, CROP_FAILED, UNKNOWN
    }

    interface OnImageSavedCallback {
        fun onImageSaved(mSavedUri: Uri?)
        fun onError(
            saveError: SaveError, message: String,
            cause: Throwable?
        )
    }

}