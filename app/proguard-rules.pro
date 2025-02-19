# work around CameraX 1.5.0-alpha05 regression
-keepclassmembers class androidx.camera.camera2.internal.CameraBurstCaptureCallback {
    public *;
}
