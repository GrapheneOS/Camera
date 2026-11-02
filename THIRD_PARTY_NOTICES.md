# Third-party notices — Fold Camera

This is a list of the principal third-party sources bundled or referenced by
Fold Camera. Preserve all original copyright and license headers in upstream
files. Redistribution should be rechecked for newly added assets or libraries.

## GrapheneOS Camera

- Project: https://github.com/GrapheneOS/Camera
- Copyright: GrapheneOS contributors (see retained project `LICENSE`).
- License: MIT. The full license is retained in `LICENSE`.
- Fold Camera is a derivative, not an official GrapheneOS release.

## AndroidX ExifInterface fork

- Source: Android Open Source Project / AndroidX ExifInterface 1.4.2.
- Located under `app/src/main/java/androidxc/exifinterface/`.
- License: Apache License 2.0; original copyright/Apache headers retained.
- Local fork changes are documented in source; do not remove upstream attribution.

## MODNet Photographic Portrait Matting (ONNX)

- Original project: https://github.com/ZHKKKe/MODNet
- Model authors: Zhanghan Ke, Jiayu Sun, Kaican Li, Qiong Yan, Rynson W. H. Lau.
- ONNX distribution: https://huggingface.co/DavG25/modnet-pretrained-models
- File: `app/src/main/assets/portrait/modnet_photographic_portrait_matting.onnx`
- SHA-256: `07c308cf0fc7e6e8b2065a12ed7fc07e1de8febb7dc7839d7b7f15dd66584df9`
- License: Apache License 2.0, per upstream MODNet project and ONNX model listing.
- These are third-party pretrained weights, not trained by Fold Camera.

## Depth Anything V2 Small (quantized ONNX)

- Original project: https://github.com/DepthAnything/Depth-Anything-V2
- ONNX distributor: https://huggingface.co/onnx-community/depth-anything-v2-small
- Source model: https://huggingface.co/depth-anything/Depth-Anything-V2-Small
- File: `app/src/main/assets/portrait/depth_anything_v2_small_quantized.onnx`
- SHA-256: `fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d`
- License: Apache License 2.0, according to the Small model/ONNX model listings.
- These are third-party pretrained weights, not trained by Fold Camera.

## ONNX Runtime and AndroidX / CameraX

- ONNX Runtime: https://github.com/microsoft/onnxruntime (MIT).
- AndroidX / CameraX: https://developer.android.com/jetpack/androidx (Apache 2.0).
- Other Gradle packages (Material Components, AppCompat, ZXing, etc.) may carry
  additional copyrights and license conditions; consult their dependency
  metadata and licenses before distributing an upstream release.

A copy of Apache License 2.0 is included in
`licenses/Apache-2.0.txt`. The copy of the MIT license covering the original
camera application is at `LICENSE`.

## Optional pet segmentation

The source code optionally loads `lraspp_voc_pets_320.onnx` when externally
installed, but this optional model is not part of the two required model files.
If you bundle additional pet weights later, verify their provenance and
redistribution terms and append a corresponding notice before publishing.

## Application modules

Portrait, panorama, and camera modes are implemented as application modules.
These names do not alter third-party copyrights or the model licenses.

## Panorama research references: Nokia / Open Camera

- Reference: https://github.com/nokia/AndroidOpenCameraKapture (branch `kapture`)
- Upstream: https://sourceforge.net/p/opencamera/code/ci/master/tree/
- Nokia's 2022 Kapture fork identifies its additions as GPL-3.0-only.
- The panorama processing code is an independent Kotlin implementation
  based on existing Fold Camera image-alignment, projection and seam code;
  **no Nokia/Open Camera GPL implementation or RenderScript code is bundled**.
- Architectural ideas researched: adjacent-frame feature alignment,
  cylindrical projection, local exposure correction and pyramid/seam blending.
- If direct Nokia/Open Camera code is incorporated later, re-evaluate GPLv3
  compatibility and redistribution requirements before releasing the app.

## OpenCV 4.13.0 — Panorama integration

- Project: https://opencv.org/
- Android package: `org.opencv:opencv:4.13.0` (official Maven Central AAR).
- License: Apache License 2.0; see `licenses/Apache-2.0.txt`.
- Fold Camera calls the native SIFT/descriptor matcher through OpenCV Java bindings.
  **This is not the complete native `cv::Stitcher` API**; Android's standard Java
  bindings do not expose that class. Stitch placement and seam blending are
  maintained in Fold Camera's own Kotlin code.
