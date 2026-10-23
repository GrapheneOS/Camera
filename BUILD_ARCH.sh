#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

KEYSTORE="${FOLDCAMERA_KEYSTORE:-$HOME/Android-Signing/android-release.jks}"
KEY_ALIAS="${FOLDCAMERA_KEY_ALIAS:-android-release}"
TOOLS="$ANDROID_HOME/build-tools/37.0.0"
OUTPUT="$PWD/FoldCamera-143.3.3-signed.apk"
VERIFICATION="gradle/verification-metadata.xml"

[[ -d "$ANDROID_HOME" ]] || { echo "Android SDK not found: $ANDROID_HOME" >&2; exit 1; }
[[ -f "$KEYSTORE" ]] || { echo "Release keystore not found: $KEYSTORE" >&2; exit 1; }
[[ -x "$TOOLS/apksigner" && -x "$TOOLS/zipalign" ]] || { echo "Install Android build-tools 37.0.0" >&2; exit 1; }

has_checksums() {
    grep -q 'name="opencv" version="4.13.0"' "$VERIFICATION" &&
    grep -q 'name="camera-core" version="1.7.0-alpha03"' "$VERIFICATION"
}

if ! has_checksums; then
    CANDIDATE="${FOLDCAMERA_VERIFICATION_FILE:-}"
    if [[ "$CANDIDATE" != "$PWD/$VERIFICATION" && -f "$CANDIDATE" ]] &&
       grep -q 'name="opencv" version="4.13.0"' "$CANDIDATE" &&
       grep -q 'name="camera-core" version="1.7.0-alpha03"' "$CANDIDATE"; then
        echo "Importing verified local metadata from $CANDIDATE"
        cp "$VERIFICATION" "${VERIFICATION}.backup"
        cp "$CANDIDATE" "$VERIFICATION"
    else
        echo "Dependency metadata is incomplete for CameraX/OpenCV." >&2
        echo "Set FOLDCAMERA_VERIFICATION_FILE to gradle/verification-metadata.xml" >&2
        echo "from a trusted working build with the same dependencies." >&2
        echo "Review the hashes before building. Verification will not be disabled." >&2
        exit 2
    fi
fi

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew
./gradlew :app:assembleRelease --no-daemon --console=plain

OUTDIR=app/build/outputs/apk/release
UNSIGNED="$OUTDIR/app-release-unsigned.apk"
SIGNED="$OUTDIR/app-release.apk"
if [[ -f "$UNSIGNED" ]]; then
    "$TOOLS/zipalign" -P 16 -f 4 "$UNSIGNED" FoldCamera-aligned.apk
    read -rsp "Keystore password: " KS_PASS; echo
    read -rsp "Key password (Enter = same as keystore): " KEY_PASS; echo
    KEY_PASS="${KEY_PASS:-$KS_PASS}"
    export KS_PASS KEY_PASS
    trap 'unset KS_PASS KEY_PASS' EXIT
    "$TOOLS/apksigner" sign \
        --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
        --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
        --out "$OUTPUT" FoldCamera-aligned.apk
    unset KS_PASS KEY_PASS
    trap - EXIT
elif [[ -f "$SIGNED" ]]; then
    "$TOOLS/apksigner" verify "$SIGNED"
    cp "$SIGNED" "$OUTPUT"
else
    echo "No release APK found in $OUTDIR" >&2
    exit 1
fi

"$TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT"
echo "Release APK: $OUTPUT"
