# GBuffer attachment-location contract

This developer-only shader pack isolates PHASE 6's first GBuffer MRT acceptance item: fragment-output location must be mapped to the target named at the same rank in the pack directive.

`composite` declares `RENDERTARGETS:4,1,7,2` while its four fragment outputs remain locations 0, 1, 2 and 3. Their expected routing is therefore:

- output location 0 -> colortex4 -> red
- output location 1 -> colortex1 -> green
- output location 2 -> colortex7 -> blue
- output location 3 -> colortex2 -> white

`final` samples those four target names and lays them out with the same quadrant pattern as the MRT verifier. A backend or runtime that sorts the target numbers, treats the colortex number as the native attachment slot, or otherwise loses directive rank will show the wrong colours.

This fixture intentionally leaves target format, clear policy and ping-pong semantics at their defaults. Those are separate PHASE 6 acceptance items and must not be inferred from this test.
