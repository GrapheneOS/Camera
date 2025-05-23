package app.grapheneos.camera.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import app.grapheneos.camera.R
import app.grapheneos.camera.util.getParcelableExtra
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import androidx.core.graphics.scale
import app.grapheneos.camera.ktx.transfer
import kotlin.Exception

open class CaptureActivity : MainActivity() {

    companion object {
        private const val TAG = "CaptureActivity"
        private const val CAPTURE_BUTTON_APPEARANCE_DELAY = 1000L
    }

    lateinit var outputUri: Uri
    lateinit var bitmap: Bitmap

    private lateinit var retakeIcon: ImageView
    private lateinit var whiteOptionCircle: ImageView
    protected lateinit var selectImageIcon: ImageView

    private lateinit var flipCameraContent: ImageView

    protected var isPreviewShown = false

    lateinit var confirmButton: ImageButton

    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            confirmPickedImage(uri)
        } else {
            showMessage(R.string.no_image_selected)
        }
    }

    fun isOutputUriAvailable(): Boolean {
        return ::outputUri.isInitialized
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retakeIcon = findViewById(R.id.retake_icon)
        selectImageIcon = findViewById(R.id.select_image_icon)
        whiteOptionCircle = findViewById(R.id.white_option_circle)
        flipCameraContent = findViewById(R.id.flip_camera_icon_content)

        confirmButton = findViewById(R.id.confirm_button)

        getParcelableExtra<Uri>(intent, MediaStore.EXTRA_OUTPUT)?.let {
            outputUri = it
        }

        imagePreview.visibility = View.GONE
        whiteOptionCircle.visibility = View.GONE
        selectImageIcon.visibility = View.VISIBLE

        thirdCircle.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Disable capture button for a while (to avoid picture capture)
        captureButton.isEnabled = false
        captureButton.alpha = 0f

        // Disable capture button for a while (to avoid picture capture)
        thirdCircle.isEnabled = false
        thirdOption.alpha = 0f

        // Enable the capture button after a while
        Handler(Looper.getMainLooper()).postDelayed({

            captureButton.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    captureButton.isEnabled = true
                }

            thirdOption.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    thirdCircle.isEnabled = true
                }

        }, CAPTURE_BUTTON_APPEARANCE_DELAY)

        // Remove the modes tab layout as we do not want the user to be able to switch to
        // another custom mode in this state
        tabLayout.visibility = View.INVISIBLE

        // Remove the margin so that that the previewView can take some more space
        (previewView.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Bring the three buttons a bit down in the UI
        (threeButtons.layoutParams as MarginLayoutParams).let {
            it.setMargins(it.leftMargin, it.topMargin, it.rightMargin, 0)
        }

        // Change the drawable to cancel mode
        cancelButtonView.setImageResource(R.drawable.cancel)

        // Overwrite the existing listener to just close the existing activity
        // (in this case)
        cancelButtonView.setOnClickListener {
            finish()
        }

        captureButton.setOnClickListener {
            if (timerDuration == 0) {
                takePicture()
            } else {
                if (cdTimer.isRunning) {
                    cdTimer.cancelTimer()
                } else {
                    cdTimer.startTimer()
                }
            }
        }

        flipCameraCircle.setOnClickListener {
            if (isPreviewShown) {
                hidePreview()
                return@setOnClickListener
            }

            if (videoCapturer.isRecording) {
                videoCapturer.isPaused = !videoCapturer.isPaused
                return@setOnClickListener
            }

            val rotation: Float = if (flipCameraIcon.rotation < 180) {
                180f
            } else {
                360f
            }

            val rotate = RotateAnimation(
                0F,
                rotation,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            rotate.duration = 400
            rotate.interpolator = LinearInterpolator()

            it.startAnimation(rotate)
            camConfig.toggleCameraSelector()
        }

        confirmButton.setOnClickListener {
            confirmCapturedImage()
        }

        // Display the activity
    }

    fun takePicture() {

        showMessage(
            getString(R.string.capturing_image)
        )

        previewLoader.visibility = View.VISIBLE
        camConfig.imageCapture?.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    bitmap = imageProxyToBitmap(image, image.imageInfo.rotationDegrees.toFloat())
                    showPreview()
                    previewLoader.visibility = View.GONE
                    showMessage(getString(R.string.image_captured_successfully))

                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    exception.printStackTrace()
                    showMessage(
                        getString(R.string.unable_to_capture_image)
                    )

                    finishActivity(RESULT_CANCELED)
                }
            }

        )
    }

    open fun showPreview() {

        isPreviewShown = true

        camConfig.cameraProvider?.unbindAll()

        mainOverlay.setImageBitmap(bitmap)
        mainOverlay.visibility = View.VISIBLE

        settingsIcon.visibility = View.INVISIBLE

        flipCameraContent.visibility = View.INVISIBLE
        retakeIcon.visibility = View.VISIBLE

        captureButton.visibility = View.INVISIBLE
        confirmButton.visibility = View.VISIBLE

        previewView.visibility = View.INVISIBLE

        thirdOption.visibility = View.INVISIBLE
    }

    open fun hidePreview() {

        isPreviewShown = false

        camConfig.startCamera(true)

        settingsIcon.visibility = View.VISIBLE

        flipCameraContent.visibility = View.VISIBLE
        retakeIcon.visibility = View.INVISIBLE

        captureButton.visibility = View.VISIBLE
        confirmButton.visibility = View.INVISIBLE

        previewView.visibility = View.VISIBLE

        thirdOption.visibility = View.VISIBLE
    }

    private fun confirmPickedImage(uri: Uri) {

        val resultIntent = Intent("inline-data")

        if (::outputUri.isInitialized) {

            try {
                val fis = contentResolver.openInputStream(uri)
                if (fis != null) {
                    fis.use {
                        val fos = contentResolver.openOutputStream(outputUri)
                        if (fos != null) {
                            fos.use {
                                fis.transfer(fos)
                                setResult(RESULT_OK)
                                finish()
                            }
                        } else {
                            showMessage(R.string.unexpected_error_occurred)
                            Log.e(TAG, "Output URI's output stream found null")
                        }
                    }
                } else {
                    showMessage(R.string.unexpected_error_occurred)
                    Log.e(TAG, "Chosen Image URI's input stream found null")
                }
            } catch (e: Exception) {
                showMessage(R.string.unexpected_error_occurred)
                Log.e(TAG, "Error occurred while writing back image to output URI", e)
            }
        } else {
            try {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                bitmap = resizeImage(bitmap)
                resultIntent.putExtra("data", bitmap)
                setResult(RESULT_OK, resultIntent)
                finish()
            } catch (e: Exception) {
                showMessage(R.string.unexpected_error_occurred)
                Log.e(TAG, "Error while sending bitmap to caller activity", e)
            }
        }
    }

    private fun confirmCapturedImage() {

        val resultIntent = Intent("inline-data")

        if (::outputUri.isInitialized) {

            val bos = ByteArrayOutputStream()

            val cf: CompressFormat =
                if (outputUri.path?.endsWith(".png") == true) {
                    CompressFormat.PNG
                } else {
                    CompressFormat.JPEG
                }

            bitmap.compress(cf, 100, bos)
            val bitmapData: ByteArray = bos.toByteArray()

            var result = RESULT_CANCELED

            try {
                contentResolver.openOutputStream(outputUri)?.use {
                    it.write(bitmapData)
                }
                result = RESULT_OK
            } catch (e: Exception) {
                showMessage(getString(R.string.unable_to_save_image))
            }

            setResult(result)
        } else {
            bitmap = resizeImage(bitmap)
            resultIntent.putExtra("data", bitmap)
            setResult(RESULT_OK, resultIntent)
        }

        finish()
    }

    private fun imageProxyToBitmap(image: ImageProxy, rotation: Float): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(rotation)
    }

    private fun resizeImage(image: Bitmap): Bitmap {

        // If within supported 1 megabyte size
        if (image.byteCount <= 0x100000)
            return image

        val width = image.width
        val height = image.height

        val scaleWidth = width / 10
        val scaleHeight = height / 10

        return image.scale(scaleWidth, scaleHeight, false)
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
