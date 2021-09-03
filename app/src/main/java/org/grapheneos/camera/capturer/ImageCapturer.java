package org.grapheneos.camera.capturer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.core.content.ContextCompat;

import org.grapheneos.camera.ui.MainActivity;

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
        return getParentDir().getAbsolutePath();
    }

    public File getParentDir(){
        return mActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    }

    public Bitmap getLatestImage(){
        File dir = getParentDir();

        final File[] files = dir.listFiles(file -> {

            if(!file.isFile()) return false;

            final String ext = getExtension(file);
            return ext.equals("jpg") || ext.equals("png");
        });

        if (files == null || files.length == 0)
            return null;

        File lastModifiedFile = files[0];

        for (File file : files) {
            if (lastModifiedFile.lastModified() < file.lastModified())
                lastModifiedFile = file;
        }

        return BitmapFactory.decodeFile(lastModifiedFile.getAbsolutePath());
    }

    private String getExtension(File file) {

        final String fileName = file.getName();
        final int lastIndexOf = fileName.lastIndexOf(".");

        if(lastIndexOf==-1)
            return "";

        else
            return fileName.substring(lastIndexOf+1);
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

        mActivity.getPreviewLoader().setVisibility(View.VISIBLE);

        mActivity.getConfig().getImageCapture().takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(mActivity),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.i(TAG, "Image saved successfully!");
                        final Uri imageUri = outputFileResults.getSavedUri();
                        if(imageUri!=null){
                            final String path = imageUri.getEncodedPath();
                            final Bitmap bm = BitmapFactory.decodeFile(path);

                            if(bm!=null)
                                mActivity.getImagePreview().setImageBitmap(bm);
                        }

                        mActivity.getPreviewLoader().setVisibility(View.GONE);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        mActivity.getPreviewLoader().setVisibility(View.GONE);
                    }
                });
    }
}
