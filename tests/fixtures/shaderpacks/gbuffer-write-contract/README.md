# GBuffer write contract

This developer-only shader pack isolates PHASE 6's fourth GBuffer MRT acceptance item: fragment outputs must write all requested colour channels into the concrete colortex attachments selected by Vitrail and handed to the backend.

`composite` writes four full-screen targets (`colortex4..7`) with different spatial/channel signatures. Every target also declares the same grey clear colour, so a missed draw, a disabled write mask or a stale clear cannot accidentally look correct. `final` samples the four results only as an observation mechanism, checks every channel against the signature that should have been written at that pixel, and emits the standard red/green/blue/white quadrants only when the corresponding target is correct; a failure becomes magenta.

This fixture closes only the **write** gate when both CI and real-device checks pass. It intentionally uses sampling to observe the stored pixels, but it does not claim PHASE 6's general sampling semantics or authoritative ping-pong behavior.