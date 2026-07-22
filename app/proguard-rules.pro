# ImageSaver reaches androidx.camera.core.internal.utils.ImageUtil#cropJpegByteArray by
# reflection because it is private and copying it out isn't worth the maintenance burden. R8
# does recognize a getDeclaredMethod() call with a constant class, name and parameter list and
# keeps the target, but that inference is the only thing standing between this app and a
# NoSuchMethodException, and it would fail quietly: cropping only runs when a capture is
# actually cropped, so a build that lost the method still takes photos and only loses the
# cropped ones, with an error dialog rather than a crash. State the dependency rather than
# relying on it being inferred.
-keepclassmembers class androidx.camera.core.internal.utils.ImageUtil {
    private static byte[] cropJpegByteArray(byte[], android.graphics.Rect, int);
}
