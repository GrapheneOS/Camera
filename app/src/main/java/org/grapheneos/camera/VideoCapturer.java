package org.grapheneos.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Environment;

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

            mActivity.getConfig().getVideoCapture().startRecording(
                    outputOptions,
                    ContextCompat.getMainExecutor(mActivity),
                    new VideoCapture.OnVideoSavedCallback(){
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                            isRecording = false;
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            isRecording = false;
                        }
                    });

            isRecording = true;
        }
    }

    public boolean isRecording(){
        return isRecording;
    }

    @SuppressLint("RestrictedApi")
    public void stopRecording(){
        mActivity.getConfig().getVideoCapture().stopRecording();
    }
}
