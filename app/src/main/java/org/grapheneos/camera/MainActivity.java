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
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

    private ProcessCameraProvider cameraProvider;

    private int cameraSelector = CameraSelector.LENS_FACING_BACK;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;

    private Camera camera;

    private ImageCapture imageCapture;

    private VideoCapture videoCapture;

    private boolean videoMode = false;

    private Bitmap lastFrame;

    private ViewPager2 flashPager;

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

    private void initializeCamera(){

        if(cameraProvider!=null) {
            startCamera();
            return;
        }

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                startCamera();
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void startCamera(){
        startCamera(false);
    }

    // Start the camera with latest hard configuration
    void startCamera(final boolean forced){

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
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .setFlashMode(flashMode)
                .build();

        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        lastFrame = mPreviewView.getBitmap();

        // Unbind/close all other camera(s) [if any]
        cameraProvider.unbindAll();

        // Get a camera instance bound to the lifecycle of this activity
        if(videoCapture!=null){
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture);
        } else {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        }

        // Focus camera on touch/tap
        mPreviewView.setOnTouchListener(this);

        start_auto_focus();

        if(!camera.getCameraInfo().hasFlashUnit())
            flashPager.setCurrentItem(2);
    }

    private void toggleCameraSelector(){
        if(cameraSelector==CameraSelector.LENS_FACING_BACK) cameraSelector = CameraSelector.LENS_FACING_FRONT;
        else cameraSelector = CameraSelector.LENS_FACING_BACK;
        startCamera(true);
    }

    private void toggleFlashMode(){
        if(camera.getCameraInfo().hasFlashUnit()){
            if(flashMode==2) flashMode = 0;
            else ++flashMode;

            flashPager.setCurrentItem(flashMode);
            startCamera(true);
        } else {
            Toast.makeText(this, "Flash is unavailable for the current mode.",
                    Toast.LENGTH_LONG).show();
        }
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
            initializeCamera();
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
                    final float start = zoomState.getLinearZoom();
                    float end = start * 1.5f;

                    if(end<0.25f) end=0.25f;
                    else if(end>zoomState.getMaxZoomRatio()) end = zoomState.getMaxZoomRatio();

                    final ValueAnimator animator = ValueAnimator.ofFloat(start, end);
                    animator.setDuration(300);
                    animator.addUpdateListener(valueAnimator ->
                            camera.getCameraControl().setLinearZoom(
                                    (float) valueAnimator.getAnimatedValue()));
                    animator.start();
                }

                return super.onDoubleTap(e);
            }
        });

        CenteringTabLayout tabLayout = findViewById(R.id.camera_mode_tabs);
        TabLayout.Tab selected;

        tabLayout.addTab(tabLayout.newTab().setText("Night Light")); // NIGHT
        tabLayout.addTab(tabLayout.newTab().setText("Portrait")); // BOKEH
        tabLayout.addTab(selected=tabLayout.newTab().setText("Camera")); // AUTO
        tabLayout.addTab(tabLayout.newTab().setText("HDR")); // HDR
        tabLayout.addTab(tabLayout.newTab().setText("Beauty")); // Beauty
//        tabLayout.addTab(tabLayout.newTab().setText("AR Effects"));

        selected.select();

        final ImageView mainOverlay = findViewById(R.id.main_overlay);

        mPreviewView.getPreviewStreamState().observe(this,  state -> {
            if(state.equals(PreviewView.StreamState.STREAMING)){
                mainOverlay.setVisibility(View.GONE);
            } else {
                if(lastFrame!=null){
                    mainOverlay.setImageBitmap(blurRenderScript(lastFrame));
                    mainOverlay.setVisibility(View.VISIBLE);
                }
            }
        });

        View fco = findViewById(R.id.flip_camera_option);
        fco.setOnClickListener(v -> toggleCameraSelector());

        ImageButton capture_button = findViewById(R.id.capture_button);
        capture_button.setOnClickListener(new View.OnClickListener(){

            final String IMAGE_FILE_FORMAT = ".jpg";

            public String getImageFileName() {
                String fileName;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                        Locale.US);/* w  ww .  j av  a  2s.  co  m*/
                fileName = sdf.format(new Date());
                return "IMG_" + fileName + IMAGE_FILE_FORMAT;
            }

            @Override
            public void onClick(View v) {
                if(camera==null) return;

                File imageFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), getImageFileName());

                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions.Builder(imageFile)
                        .build();

                imageCapture.takePicture(
                        outputFileOptions,
                        ContextCompat.getMainExecutor(MainActivity.this),
                        new ImageCapture.OnImageSavedCallback() {

                            @Override
                            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                                Log.i(TAG, "Image saved successfully!");
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
//                                Toast.makeText(MainActivity.this, "", Toast.LENGTH_LONG).show();
                                exception.printStackTrace();
                            }
                        });
            }
        });

        flashPager = findViewById(R.id.flash_pager);
        flashPager.setAdapter(new FlashAdapter());

        flashPager.setUserInputEnabled(false);

        flashPager.setOnClickListener(v -> toggleFlashMode());

        ImageView captureModeView = findViewById(R.id.capture_mode);

        captureModeView.setOnClickListener(new View.OnClickListener(){

            final int SWITCH_ANIM_DURATION = 150;

            @Override
            public void onClick(View v) {

                final int imgID = videoMode ? R.drawable.video_camera : R.drawable.camera;

                videoMode = !videoMode;

                startCamera(true);

                final ObjectAnimator oa1 = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0f);
                final ObjectAnimator oa2 = ObjectAnimator.ofFloat(v, "scaleX", 0f, 1f);
                oa1.setInterpolator(new DecelerateInterpolator());
                oa2.setInterpolator(new AccelerateDecelerateInterpolator());
                oa1.setDuration(SWITCH_ANIM_DURATION);
                oa2.setDuration(SWITCH_ANIM_DURATION);
                oa1.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        captureModeView.setImageResource(imgID);
                        oa2.start();
                    }
                });
                oa1.start();

                if(videoMode){
                    capture_button.setBackgroundResource(0);
                    capture_button.setImageResource(R.drawable.video_shutter);
                } else {
                    capture_button.setBackgroundResource(R.drawable.camera_shutter);
                    capture_button.setImageResource(0);
                }
            }
        });
    }

    private Bitmap blurRenderScript(Bitmap smallBitmap) {

        final float defaultBitmapScale = 0.1f;

        int width  = Math.round(smallBitmap.getWidth() * defaultBitmapScale);
        int height = Math.round(smallBitmap.getHeight() * defaultBitmapScale);

        Bitmap inputBitmap  = Bitmap.createScaledBitmap(smallBitmap, width, height, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript renderScript = RenderScript.create(this);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        Allocation tmpIn = Allocation.createFromBitmap(renderScript, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap);
        theIntrinsic.setRadius(4);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
    }

    private boolean isZooming = false;

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        dbTapGestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);

        if(event.getAction()==MotionEvent.ACTION_DOWN)
            return true;

        else if(event.getAction()==MotionEvent.ACTION_UP) {

            if(isZooming) {
                isZooming = false;
                return true;
            }

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

        isZooming = true;

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
