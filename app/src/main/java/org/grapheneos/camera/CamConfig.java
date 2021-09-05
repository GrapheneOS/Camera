package org.grapheneos.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.Toast;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.TorchState;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.grapheneos.camera.ui.MainActivity;

import java.io.File;
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

    private File latestFile;

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

    public String getParentDirPath(){
        return getParentDir().getAbsolutePath();
    }

    public File getParentDir(){
        File[] dirs = mActivity.getExternalMediaDirs();
        File parentDir = null;

        for (File dir : dirs){
            if(dir!=null){
                parentDir = dir;
                break;
            }
        }

        if(parentDir!=null){
            parentDir = new File(parentDir.getAbsolutePath(),
                    mActivity.getResources().getString(R.string.app_name));
            if(parentDir.mkdirs()){
                Log.i(TAG, "Parent directory was successfully created");
            }
        }

        return parentDir;
    }

    public static Bitmap getVideoThumbnail(String p_videoPath)
            throws Throwable
    {
        Bitmap m_bitmap;
        MediaMetadataRetriever m_mediaMetadataRetriever = null;
        try
        {
            m_mediaMetadataRetriever = new MediaMetadataRetriever();
            m_mediaMetadataRetriever.setDataSource(p_videoPath);
            m_bitmap = m_mediaMetadataRetriever.getFrameAtTime();
        }
        catch (Exception m_e)
        {
            throw new Throwable(
                    "Exception in retriveVideoFrameFromVideo(String p_videoPath)"
                            + m_e.getMessage());
        }
        finally
        {
            if (m_mediaMetadataRetriever != null)
            {
                m_mediaMetadataRetriever.release();
            }
        }
        return m_bitmap;
    }

    public Bitmap getLatestPreview(){
        final File lastModifiedFile = getLatestMediaFile();
        if(lastModifiedFile==null) return null;

        if(getExtension(lastModifiedFile).equals("mp4")){

            try {
                return getVideoThumbnail(lastModifiedFile.getAbsolutePath());
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                return null;
            }
        }

        return BitmapFactory.decodeFile(lastModifiedFile.getAbsolutePath());
    }

    public void setLatestFile(File latestFile) {
        this.latestFile = latestFile;
    }

    public File getLatestMediaFile(){

        if(latestFile!=null) return latestFile;

        File dir = getParentDir();

        final File[] files = dir.listFiles(file -> {

            if(!file.isFile()) return false;

            final String ext = getExtension(file);
            return ext.equals("jpg") || ext.equals("png") || ext.equals("mp4");
        });

        if (files == null || files.length == 0)
            return null;

        File lastModifiedFile = files[0];

        for (File file : files) {
            if (lastModifiedFile.lastModified() < file.lastModified())
                lastModifiedFile = file;
        }

        return lastModifiedFile;
    }

    public static String getExtension(File file) {

        final String fileName = file.getName();
        final int lastIndexOf = fileName.lastIndexOf(".");

        if(lastIndexOf==-1)
            return "";

        else
            return fileName.substring(lastIndexOf+1);
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

        if(isFlashAvailable()){
            camera.getCameraInfo().getTorchState().observe(mActivity, torchState -> {
                if(torchState== TorchState.ON){
                    mActivity.getTorchToggleView()
                            .setImageResource(R.drawable.torch_on);
                } else {
                    mActivity.getTorchToggleView()
                            .setImageResource(R.drawable.torch_off);
                }
            });
        } else {
            mActivity.getFlashPager().setCurrentItem(2);
            flashMode = ImageCapture.FLASH_MODE_OFF;
        }
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
