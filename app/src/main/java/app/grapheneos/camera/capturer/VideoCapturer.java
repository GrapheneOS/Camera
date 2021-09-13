package app.grapheneos.camera.capturer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import app.grapheneos.camera.CamConfig;
import app.grapheneos.camera.ui.MainActivity;
import app.grapheneos.camera.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoCapturer {

    private static final String TAG = "VideoCapturer";

    private final MainActivity mActivity;

    private boolean isRecording = false;

    private String videoFileFormat = ".mp4";

    private final Handler handler = new Handler();

    private int elapsedSeconds = 0;

    private final Runnable runnable = new Runnable() {

        @Override
        public void run() {
            ++elapsedSeconds;

            final String secs = padTo2(elapsedSeconds % 60);
            final String mins  = padTo2((elapsedSeconds / 60) % 60);
            final String hours = padTo2(elapsedSeconds / 3600);

            String timerText;

            if(hours.equals("00")){
                timerText = mins+":"+secs;
            } else {
                timerText = hours+":"+mins+":"+secs;
            }

            mActivity.getTimerView().setText(timerText);
            startTimer();
        }
    };

    public VideoCapturer(final MainActivity mActivity) {
        this.mActivity = mActivity;
    }

    private String padTo2(int time){
        return String.format("%1$" + 2 + "s", time).replace(' ', '0');
    }

    private void startTimer() {
        handler.postDelayed(runnable, 1000);
    }

    private void cancelTimer() {
        elapsedSeconds = 0;
        handler.removeCallbacks(runnable);
    }

    public File generateFileForVideo() {
        String fileName;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.US);/* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(new Date());
        fileName = "VID_" + fileName + videoFileFormat;
        return new File(mActivity.getConfig().getParentDirPath(), fileName);
    }

    public static boolean isVideo(File file){
        return CamConfig.getExtension(file).equals("mp4");
    }

    public boolean isLatestMediaVideo(){
        return VideoCapturer.isVideo(
                mActivity.getConfig().getLatestMediaFile()
        );
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

                            mActivity.getPreviewLoader()
                                    .setVisibility(View.VISIBLE);

                            final Uri videoUri = outputFileResults.getSavedUri();

                            if(videoUri!=null){
                                final String path = videoUri.getEncodedPath();

                                Bitmap tBm = null;

                                try {
                                    tBm = CamConfig.getVideoThumbnail(path);
                                } catch (Throwable throwable) {
                                    throwable.printStackTrace();
                                }

                                final File file = new File(path);

                                mActivity.getConfig().setLatestFile(file);

                                final String mimeType = MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(
                                                CamConfig.getExtension(new File(path))
                                        );

                                final Bitmap bm = tBm;
                                MediaScannerConnection.scanFile(
                                        mActivity,
                                        new String[]{file.getParent()},
                                        new String[]{mimeType},
                                        (path1, uri) -> {
                                            Log.d(TAG, "Image capture scanned" +
                                                    " into media store: " + uri);

                                            mActivity.runOnUiThread(()-> {
                                                mActivity.getPreviewLoader()
                                                        .setVisibility(View.GONE);

                                                if (bm != null)
                                                    mActivity.getImagePreview()
                                                            .setImageBitmap(bm);

                                            });
                                        }
                                );
                            }

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
        mActivity.getThirdCircle().setImageResource(R.drawable.camera_shutter);
        mActivity.getTabLayout().setVisibility(View.INVISIBLE);

        mActivity.getTimerView().setText(R.string.start_value_timer);
        mActivity.getTimerView().setVisibility(View.VISIBLE);
        startTimer();
    }

    public void afterRecordingStops(){
        mActivity.getCaptureButton().setImageResource(R.drawable.start_recording);
        mActivity.getThirdCircle().setImageResource(R.drawable.option_circle);
        mActivity.getFlipCameraCircle().setVisibility(View.VISIBLE);
        mActivity.getCaptureModeView().setVisibility(View.VISIBLE);
        mActivity.getTabLayout().setVisibility(View.VISIBLE);

        mActivity.getTimerView().setVisibility(View.GONE);
        cancelTimer();
    }

    public boolean isRecording(){
        return isRecording;
    }

    @SuppressLint("RestrictedApi")
    public void stopRecording(){
        mActivity.getConfig().getVideoCapture().stopRecording();
    }
}
