package org.grapheneos.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoCapturer {

    private static final String TAG = "VideoCapturer";

    private final MainActivity mActivity;

    private boolean isRecording = false;

    private String videoFileFormat = ".mp4";

    public VideoCapturer(final MainActivity mActivity) {
        this.mActivity = mActivity;
    }

    public String getParentDirPath() {
        return mActivity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                .getAbsolutePath();
    }

    public File generateFileForVideo() {
        String fileName;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.US);/* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(new Date());
        fileName = "VID_" + fileName + videoFileFormat;
        return new File(getParentDirPath(), fileName);
    }

    @SuppressLint("RestrictedApi")
    public void startRecording() {

        if (mActivity.getConfig().getCamera() == null) return;

        final File videoFile = generateFileForVideo();

        VideoCapture.OutputFileOptions outputOptions =
                new VideoCapture.OutputFileOptions.Builder(videoFile)
                        .build();

        // Will always be true if we reach here
        if (ActivityCompat.checkSelfPermission(mActivity,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

            beforeRecordingStarts();

            mActivity.getConfig().getVideoCapture().startRecording(
                    outputOptions,
                    ContextCompat.getMainExecutor(mActivity),
                    new VideoCapture.OnVideoSavedCallback(){
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                            isRecording = false;
                            afterRecordingStops();
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            isRecording = false;
                            afterRecordingStops();

                            if(videoCaptureError==6){
                                Toast.makeText(mActivity,
                                        "Video too short to be saved", Toast.LENGTH_LONG)
                                        .show();
                                return;
                            }

                            Toast.makeText(mActivity, "Unable to save recording." +
                                    "\nError Code: " + videoCaptureError, Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

            isRecording = true;
        }
    }

    public void beforeRecordingStarts(){
        mActivity.getCaptureButton().setImageResource(R.drawable.stop_recording);
        mActivity.getFlipCameraCircle().setVisibility(View.INVISIBLE);
        mActivity.getCaptureModeView().setVisibility(View.GONE);
        mActivity.getFlashPager().setVisibility(View.GONE);
        mActivity.getThirdCircle().setImageResource(R.drawable.camera_shutter);
        mActivity.getTabLayout().setVisibility(View.INVISIBLE);
    }

    public void afterRecordingStops(){
        mActivity.getCaptureButton().setImageResource(R.drawable.start_recording);
        mActivity.getThirdCircle().setImageResource(R.drawable.circle);
        mActivity.getFlipCameraCircle().setVisibility(View.VISIBLE);
        mActivity.getCaptureModeView().setVisibility(View.VISIBLE);
        mActivity.getTabLayout().setVisibility(View.VISIBLE);
        mActivity.getFlashPager().setVisibility(View.VISIBLE);
    }

    public boolean isRecording(){
        return isRecording;
    }

    @SuppressLint("RestrictedApi")
    public void stopRecording(){
        mActivity.getConfig().getVideoCapture().stopRecording();
    }
}
