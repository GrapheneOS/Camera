package org.grapheneos.camera;

import android.util.Log;
import android.widget.Toast;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.grapheneos.camera.ui.MainActivity;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CamConfig {

    private static final String TAG = "CamConfig";

    public static final int AUTO_FOCUS_INTERVAL_IN_SECONDS = 2;

    private Camera camera;

    private ProcessCameraProvider cameraProvider;

    private ImageCapture imageCapture;

    private int cameraSelector = CameraSelector.LENS_FACING_BACK;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;

    private boolean videoMode = false;

    private VideoCapture videoCapture;

    private final MainActivity mActivity;

    public CamConfig(final MainActivity mActivity){
        this.mActivity = mActivity;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public boolean isVideoMode() {
        return videoMode;
    }

    public ImageCapture getImageCapture() {
        return imageCapture;
    }

    public int getCameraSelector() {
        return cameraSelector;
    }

    public int getFlashMode() {
        return flashMode;
    }

    public ProcessCameraProvider getCameraProvider() {
        return cameraProvider;
    }

    public VideoCapture getVideoCapture() {
        return videoCapture;
    }

    public void setCameraProvider(ProcessCameraProvider cameraProvider) {
        this.cameraProvider = cameraProvider;
    }

    public void setCameraSelector(int cameraSelector) {
        this.cameraSelector = cameraSelector;
    }

    public void setFlashMode(int flashMode) {
        this.flashMode = flashMode;
    }

    public void setVideoCapture(VideoCapture videoCapture) {
        this.videoCapture = videoCapture;
    }

    public void switchCameraMode(){
        this.videoMode = !this.isVideoMode();
        startCamera(true);
    }

    // Tells whether flash is available for the current mode
    public boolean isFlashAvailable(){
        return camera.getCameraInfo().hasFlashUnit();
    }

    public void toggleFlashMode(){
        if(camera.getCameraInfo().hasFlashUnit()){
            if(flashMode==2) flashMode = 0;
            else ++flashMode;

            mActivity.getFlashPager().setCurrentItem(flashMode);
            startCamera(true);

        } else {
            Toast.makeText(mActivity, "Flash is unavailable for the current mode.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void toggleCameraSelector(){
        if(cameraSelector==CameraSelector.LENS_FACING_BACK)
            cameraSelector = CameraSelector.LENS_FACING_FRONT;
        else
            cameraSelector = CameraSelector.LENS_FACING_BACK;

        startCamera(true);
    }

    public void initializeCamera(){

        if(cameraProvider!=null) {
            startCamera();
            return;
        }

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(mActivity);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(mActivity));
    }

    public void startCamera(){
        startCamera(false);
    }

    // Start the camera with latest hard configuration
    public void startCamera(final boolean forced){

        if(!forced && camera!=null) return;

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(this.cameraSelector)
                .build();

        ImageCapture.Builder builder = new ImageCapture.Builder();

        if(videoMode)
            videoCapture = new VideoCapture.Builder().build();

        imageCapture = builder
                .setTargetRotation(mActivity.getWindowManager().getDefaultDisplay().getRotation())
                .setFlashMode(flashMode)
                .build();

        preview.setSurfaceProvider(mActivity.getPreviewView().getSurfaceProvider());

        mActivity.updateLastFrame();

        // Unbind/close all other camera(s) [if any]
        cameraProvider.unbindAll();

        // Get a camera instance bound to the lifecycle of this activity
        if(videoMode){
            camera = cameraProvider.bindToLifecycle(mActivity, cameraSelector,
                    preview, imageCapture, videoCapture);
        } else {
            camera = cameraProvider.bindToLifecycle(mActivity, cameraSelector,
                    preview, imageCapture);
        }

        // Focus camera on touch/tap
        mActivity.getPreviewView().setOnTouchListener(mActivity);

        start_auto_focus();

        if(!isFlashAvailable())
            mActivity.getFlashPager().setCurrentItem(2);
    }

    private void start_auto_focus(){
        final MeteringPoint autoFocusPoint = new SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(.5f, .5f);

        FocusMeteringAction autoFocusAction = new FocusMeteringAction.Builder(
                autoFocusPoint,
                FocusMeteringAction.FLAG_AF
        ).setAutoCancelDuration(CamConfig.AUTO_FOCUS_INTERVAL_IN_SECONDS, TimeUnit.SECONDS).build();

        camera.getCameraControl().startFocusAndMetering(autoFocusAction).addListener(() ->
                        Log.i(TAG, "Auto-focusing every " + CamConfig.AUTO_FOCUS_INTERVAL_IN_SECONDS + " seconds..."),
                ContextCompat.getMainExecutor(mActivity));
    }
}
