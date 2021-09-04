package org.grapheneos.camera.capturer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.core.content.ContextCompat;

import org.grapheneos.camera.CamConfig;
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

    public File generateFileForImage(){
        String fileName;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.US);/* w  ww .  j av  a  2s.  co  m*/
        fileName = sdf.format(new Date());
        fileName = "IMG_" + fileName + imageFileFormat;
        return new File(mActivity.getConfig().getParentDirPath(), fileName);
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

                            mActivity.getConfig().setLatestFile(new File(path));

                            final String mimeType = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(
                                            CamConfig.getExtension(new File(path))
                                    );

                            MediaScannerConnection.scanFile(
                                    mActivity,
                                    new String[]{path},
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
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        mActivity.getPreviewLoader().setVisibility(View.GONE);
                    }
                });
    }
}
