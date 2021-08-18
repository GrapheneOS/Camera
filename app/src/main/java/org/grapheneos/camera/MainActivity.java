package org.grapheneos.camera;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GOCam";

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private AlertDialog manualPermissionDialog;

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
    }
}
