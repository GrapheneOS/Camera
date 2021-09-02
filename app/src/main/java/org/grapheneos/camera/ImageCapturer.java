package org.grapheneos.camera;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageCapturer {

    private static final String TAG = "ImageCapturer";

    private String imageFileFormat = ".jpg";

    private final MainActivity mActivity;

    public ImageCapturer(final MainActivity mActivity) {
        this.mActivity = mActivity;
    }

    public String getParentDirPath(){
        return mActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                .getAbsolutePath();
    }

    public File generateFileForImage(){
        String fileName;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.US);/* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(new Date());
        fileName = "IMG_" + fileName + imageFileFormat;
        return new File(getParentDirPath(), fileName);
    }

    public void takePicture() {
        if (mActivity.getConfig().getCamera() == null) return;

        final File imageFile = generateFileForImage();

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(imageFile)
                        .build();

        mActivity.getConfig().getImageCapture().takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(mActivity),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Image saved successfully!");
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                });
    }
}
