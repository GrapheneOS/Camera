# Build and release verification

## Prerequisites

- JDK 21 and the Android SDK configured through `ANDROID_HOME` or `~/Android/Sdk`
- Android build-tools 37.0.0 for release signing
- The Gradle wrapper shipped with this repository
- A trusted Gradle dependency-verification file that includes the current CameraX and OpenCV dependencies

## Build

```sh
printf 'sdk.dir=%s\n' "${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
./gradlew :app:assembleDebug --no-daemon
```

Run `bash BUILD_ARCH.sh` to build and sign the release APK with the existing local key. Do not commit keystores or passwords. The build script will fail rather than silently disabling dependency verification.

If the supplied verification metadata does not yet cover the current CameraX and OpenCV versions, set `FOLDCAMERA_VERIFICATION_FILE` to the **full path** of a previously reviewed and trusted `verification-metadata.xml` with those dependencies. Do not switch off dependency verification or invent hashes.

## Device verification

1. Open Photo, Video, Portrait, QR, Pro, Burst, Slow Motion, Interval and Hyperlapse, and check capture and gallery behavior.
2. Verify Dual Photo and Dual Video when the Fold is both open and closed. Test switching modes, moving/resizing PIP, split-screen, in-video still capture and gallery thumbnails.
3. Move PIP to a non-default position in Dual Photo, switch to Dual Video and confirm the overlay and actual video stream remain aligned without another touch. Repeat in the reverse direction.
4. Test Panorama 180° and Panorama 360° independently in both sweep directions. Confirm progress, stitching and the correct tab labels. On a failed stitch, confirm that temporary frames are removed and no diagnostic ZIP or extra JPEG copies are created in Downloads.
5. Open and close the mode editor. Test independent scrolling, reordering and restoration of modes from More.
6. Confirm normal camera modes resume correctly after Dual modes and no preview goes black.
7. Verify portrait inference, retention of the original JPEG, EXIF orientation and error reporting.
8. Verify model checksums, recording permissions, microphone controls and application behavior without network access.

## Release gate

A successful JVM test suite or source comparison does not replace an Android build and physical device tests. Verify compilation and device behavior before distributing an APK.

## Code and source hygiene

```sh
python3 tools/check-source-hygiene.py
./gradlew :app:lintRelease --no-daemon --console=plain
```

Inspect `app/build/reports/lint-results-release.html` after Lint completes.
Pattern-based source hygiene checks are not a proof of privacy, security or the absence of unused code.
Review the built release APK, signing certificate and runtime behavior separately.
