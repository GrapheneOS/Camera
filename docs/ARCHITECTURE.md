# Architecture

Fold Camera extends the GrapheneOS Camera activity and camera lifecycle. `MainActivity` owns the user interface and mode transitions. `CamConfig` manages settings and CameraX binding.

`IntegratedDualController` handles concurrent front/rear streams in the existing preview and exposes Dual Photo and Dual Video as ordinary camera modes. `DualVideoCompositionLayout` calculates the normalized picture-in-picture and split-screen geometry. The position and sizing are shared when switching between the two Dual modes.

The panorama capture pipeline resides in `capturer/` and uses sensor motion, frame sampling and OpenCV-assisted registration. Panorama processing and portrait compositing are separate from the main camera activity and do not replace its normal preview lifecycle.

Portrait matting and depth estimation use locally stored ONNX weights. Neither inference nor panorama stitching requires a network connection. See `THIRD_PARTY_NOTICES.md` for model provenance.

Android activities exposed for system camera intents and secure capture remain distinct from camera *modes*. Do not replace mode selection with an additional activity.
