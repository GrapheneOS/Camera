# Fold Camera

Fold Camera is an Android camera application based on [GrapheneOS Camera](https://github.com/GrapheneOS/Camera). It is an independent derivative and is not an official GrapheneOS project.

## Features

- Photo, video, QR scanning, front and rear cameras
- Portrait with locally bundled segmentation and depth models
- Horizontal 180° and 360° panoramas
- Concurrent Dual Photo and Dual Video with picture-in-picture and split-screen layouts
- Manual camera controls, burst, slow motion, hyperlapse and interval capture
- Customizable camera mode strip and foldable-device camera support

Dual Photo creates a JPEG from the combined camera preview and stores it in `DCIM/Dual Photo`. Dual Video recordings go into `DCIM/Dual Video`. Taking a photo while recording uses the Dual Photo directory. JPEG resolution is limited by the preview stream.

Dual Photo offers Flash Off/On/Auto, a torch toggle and optional front-camera Self Illumination in the existing camera settings. Because concurrent capture uses composed preview frames rather than ImageCapture, flash is approximated by briefly enabling the rear camera torch before the snapshot. Auto uses an approximate preview brightness measurement. Torch operation depends on device and CameraX support. Self Illumination temporarily raises screen brightness during the capture; the two lighting settings are independent.

Dual Photo shutter and Dual Video recording sounds use the existing GrapheneOS Camera audio player and honor the camera sounds preference. Recording audio is controlled separately by the microphone setting.

Dynamic dual-camera composition currently uses a CameraX prerelease; verify device compatibility before shipping.

## Build

Requires JDK 21, an Android SDK and the Gradle wrapper included in the repository. Point `ANDROID_HOME` to your SDK, or place the SDK at `~/Android/Sdk`.

```sh
printf 'sdk.dir=%s\n' "${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
./gradlew :app:assembleDebug --no-daemon
```

For an aligned and signed release APK on Arch Linux, use `./BUILD_ARCH.sh`. This uses the locally held signing key and performs Gradle dependency verification. The script never disables verification or creates a signing key.

Dependency hashes for CameraX and OpenCV must be reviewed. When the bundled verification file does not cover the current dependencies, the build script imports metadata from an explicitly trusted local build, or exits with instructions.

## Verification

Check the Android release build and lint on a configured development machine:

```sh
python3 tools/check-source-hygiene.py
./gradlew :app:lintRelease --no-daemon --console=plain
```

Camera behavior should be validated on target hardware.

## Authorship and credits

Fold Camera's fork-specific development is maintained by [@bigtechstruggler](https://github.com/bigtechstruggler). See [AUTHORS.md](AUTHORS.md) for attribution and a request to preserve the identity of Fold Camera engines and modules when reusing them.

## Licensing

GrapheneOS Camera's MIT license is retained in [LICENSE](LICENSE). Third-party model and library information is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Copyright headers and third-party attributions must be preserved when redistributing the source.
