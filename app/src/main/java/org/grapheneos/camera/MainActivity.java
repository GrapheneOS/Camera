package org.grapheneos.camera;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.ZoomState;
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

import com.google.android.material.tabs.TabLayout;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "GOCam";

    private PreviewView mPreviewView;

    // Hold a reference to the manual permission dialog to avoid re-creating it if it
    // is already visible and to dismiss it if the permission gets granted.
    private AlertDialog manualPermissionDialog;

    private Bitmap lastFrame;

    private ViewPager2 flashPager;

    private CamConfig config;

    private ImageCapturer imageCapturer;

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

    public PreviewView getPreviewView() {
        return mPreviewView;
    }

    public ViewPager2 getFlashPager() {
        return flashPager;
    }

    public void updateLastFrame() {
        this.lastFrame = mPreviewView.getBitmap();
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
            config.initializeCamera();
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

        config = new CamConfig(this);
        imageCapturer = new ImageCapturer(this);

        mPreviewView = findViewById(R.id.camera);
        scaleGestureDetector = new ScaleGestureDetector(this, this);
        dbTapGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.i(TAG, "===============Double tap detected.=========");

                final ZoomState zoomState = config.getCamera().getCameraInfo().
                        getZoomState().getValue();

                if(zoomState!=null) {
                    final float start = zoomState.getLinearZoom();
                    float end = start * 1.5f;

                    if(end<0.25f) end=0.25f;
                    else if(end>zoomState.getMaxZoomRatio()) end = zoomState.getMaxZoomRatio();

                    final ValueAnimator animator = ValueAnimator.ofFloat(start, end);
                    animator.setDuration(300);
                    animator.addUpdateListener(valueAnimator ->
                            config.getCamera().getCameraControl().setLinearZoom(
                                    (float) valueAnimator.getAnimatedValue()));
                    animator.start();
                }

                return super.onDoubleTap(e);
            }
        });

        BottomTabLayout tabLayout = findViewById(R.id.camera_mode_tabs);
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

        final View fco = findViewById(R.id.flip_camera_option);
        fco.setOnClickListener(v -> config.toggleCameraSelector());

        ImageButton capture_button = findViewById(R.id.capture_button);
        capture_button.setOnClickListener(v -> imageCapturer.takePicture());

        flashPager = findViewById(R.id.flash_pager);

        flashPager.setAdapter(new FlashAdapter());
        flashPager.setUserInputEnabled(false);
        flashPager.setOnClickListener(v -> config.toggleFlashMode());

        final ImageView captureModeView = findViewById(R.id.capture_mode);
        captureModeView.setOnClickListener(new View.OnClickListener(){

            final int SWITCH_ANIM_DURATION = 150;

            @Override
            public void onClick(View v) {

                final int imgID = config.isVideoMode() ? R.drawable.video_camera :
                        R.drawable.camera;

                config.switchCameraMode();

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

                if(config.isVideoMode()){
                    capture_button.setBackgroundResource(0);
                    capture_button.setImageResource(R.drawable.start_recording);
                } else {
                    capture_button.setBackgroundResource(R.drawable.camera_shutter);
                    capture_button.setImageResource(0);
                }
            }
        });
    }

    public CamConfig getConfig() {
        return config;
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

            config.getCamera().getCameraControl().startFocusAndMetering(
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

        final ZoomState zoomState = config.getCamera().getCameraInfo().getZoomState().getValue();
        float scale = 1f;

        if(zoomState!=null) {
            scale = zoomState.getZoomRatio() * detector.getScaleFactor();
        }

        config.getCamera().getCameraControl().setZoomRatio(scale);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) { }
}
