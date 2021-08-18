package org.grapheneos.camera;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GOCam";

    private PreviewView mPreviewView;

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private AlertDialog manualPermissionDialog;

    private Camera camera;

    // Used to request permission from the user
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "Permission granted for camera on request.");
                } else {
                    Log.i(TAG, "Permission denied/unavailable for camera on request.");
                }

                check_camera_permission();
            });

    private void start_camera(){

        // Don't do anything if camera has already started
        if(camera!=null) return;

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Bind the camera to the camera (Preview) view
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .build();

        ImageCapture.Builder builder = new ImageCapture.Builder();

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        // Unbind/close all other camera(s) [if any]
        cameraProvider.unbindAll();

        // Get a camera instance bound to the lifecycle of this activity
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
    }

    private void check_camera_permission(){

        Log.i(TAG, "Checking camera status...");

        // Check if the app has access to the user's camera
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {

            // If the user has manually granted the permission, dismiss the dialog.
            if(manualPermissionDialog!=null && manualPermissionDialog.isShowing())
                manualPermissionDialog.cancel();

            Log.i(TAG, "Permission granted.");

            // Setup the camera since the permission is available
            start_camera();
        }

        // Check if the user has default denied the camera permission for app
        // and display an educational dialog based on it.
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Log.i(TAG, "The user has default denied camera permission.");

            // Don't build and show a new dialog if it's already visible
            if(manualPermissionDialog!=null && manualPermissionDialog.isShowing()) return;

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(R.string.manual_permission_dialog_title);
            builder.setMessage(R.string.manual_permission_dialog_message);

            final AtomicBoolean positive_clicked = new AtomicBoolean(false);

            // Open the settings menu for the current app
            builder.setPositiveButton("Settings", (dialog, which) -> {
                positive_clicked.set(true);
                Intent intent = new
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package",
                        getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            });

            builder.setNegativeButton("Cancel",  null);
            builder.setOnDismissListener((p1) -> {

                // The dialog could have either been dismissed by clicking on the
                // background or by clicking the cancel button. So in those cases,
                // the app should exit as the app depends on the camera permission.
                if (!positive_clicked.get()) {
                    finish();
                }
            });

            manualPermissionDialog = builder.show();
        }

        // Request for the permission (Android will actually popup the permission
        // dialog in this case)
        else {
            Log.i(TAG, "Requesting permission from user...");
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check camera permission again if the user switches back to the app (maybe
        // after enabling/disabling the camera permission in Settings)
        // Will also be called by Android Lifecycle when the app starts up
        Log.i(TAG, "onResume");
        check_camera_permission();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreviewView = findViewById(R.id.camera);
    }
}
