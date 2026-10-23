# Project authorship and attribution

**Fold Camera** is an independent derivative of [GrapheneOS Camera](https://github.com/GrapheneOS/Camera).

- **Upstream foundation:** GrapheneOS Camera contributors, with the existing copyright and MIT license preserved.
- **Fork development and added features:** [@bigtechstruggler](https://github.com/bigtechstruggler), who maintains the Fold Camera modifications and integration, including the fork's panorama, portrait-processing, dual-camera, and foldable-specific work.
- **Third-party components and pretrained models:** Their respective upstream authors and licensors, documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Those external implementations and model weights are not claimed as original creations of Fold Camera.

## Request to future contributors and upstream projects

If code from Fold Camera is reused or integrated into another camera project, including GrapheneOS Camera, the fork maintainer kindly requests:

1. **Keep the original Fold Camera engine/module names where reasonably possible** — for example `OpenCvPanoramaEngine`, `PortraitEngine`, and `IntegratedDualController`.
2. **Credit @bigtechstruggler** for the Fold Camera-specific contributions that are incorporated, in the source history and/or a contributor acknowledgement.
3. **Retain the upstream GrapheneOS and third-party notices** required by the applicable licenses.

This is a request for recognition and continuity, **not a new license restriction**. Integrators may need to adapt or rename classes to satisfy their own architecture. The applicable license terms remain authoritative.
