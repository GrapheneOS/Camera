package org.grapheneos.camera;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.animation.Animator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "GOCam";

    private static final int AUTO_FOCUS_INTERVAL_IN_SECONDS = 2;

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

    private ScaleGestureDetector scaleGestureDetector;

    private GestureDetector dbTapGestureDetector;

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

        // Focus camera on touch/tap
        mPreviewView.setOnTouchListener(this);

        start_auto_focus();
    }

    private void animateFocusRing(float x, float y) {
        ImageView focusRing = findViewById(R.id.focusRing);

        // Move the focus ring so that its center is at the tap location (x, y)
        float width = focusRing.getWidth();
        float height = focusRing.getHeight();
        focusRing.setX(x - width / 2);
        focusRing.setY(y - height / 2);

        // Show focus ring
        focusRing.setVisibility(View.VISIBLE);
        focusRing.setAlpha(1F);

        // Animate the focus ring to disappear
        focusRing.animate()
                .setStartDelay(500)
                .setDuration(300)
                .alpha(0F)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        focusRing.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}

                }).start();
    }

    private void start_auto_focus(){
        final MeteringPoint autoFocusPoint = new SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(.5f, .5f);

        FocusMeteringAction autoFocusAction = new FocusMeteringAction.Builder(
                autoFocusPoint,
                FocusMeteringAction.FLAG_AF
        ).setAutoCancelDuration(AUTO_FOCUS_INTERVAL_IN_SECONDS, TimeUnit.SECONDS).build();

        camera.getCameraControl().startFocusAndMetering(autoFocusAction).addListener(() ->
                        Log.i(TAG, "Auto-focusing every " + AUTO_FOCUS_INTERVAL_IN_SECONDS + " seconds..."),
                ContextCompat.getMainExecutor(this));
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
        check_camera_permission();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPreviewView = findViewById(R.id.camera);
        scaleGestureDetector = new ScaleGestureDetector(this, this);
        dbTapGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.i(TAG, "===============Double tap detected.=========");

                final ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();

                if(zoomState!=null) {
                    camera.getCameraControl().setZoomRatio(zoomState.getZoomRatio() * 1.5f);
                }

                return super.onDoubleTap(e);
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        dbTapGestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);

        if(event.getAction()==MotionEvent.ACTION_DOWN)
            return true;

        else if(event.getAction()==MotionEvent.ACTION_UP) {

            final float x = event.getX();
            final float y = event.getY();

            MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(
                    mPreviewView.getWidth(), mPreviewView.getHeight()
            );

            final MeteringPoint autoFocusPoint = factory.createPoint(x, y);

            animateFocusRing(x, y);

            camera.getCameraControl().startFocusAndMetering(
                    new FocusMeteringAction.Builder(
                            autoFocusPoint,
                            FocusMeteringAction.FLAG_AF
                    ).disableAutoCancel().build()
            );

            return v.performClick();
        }

        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        final ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        float scale = 1f;

        if(zoomState!=null) {
            scale = zoomState.getZoomRatio() * detector.getScaleFactor();
        }

        camera.getCameraControl().setZoomRatio(scale);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) { }
}
