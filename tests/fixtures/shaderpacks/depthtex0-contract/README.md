# PHASE 8 depthtex0 acceptance contract

This fixture isolates the composite-side `depthtex0` path. It deliberately does not name `depthtex1` or `depthtex2`, and it does not claim the later pre-translucent, pre-hand, or exact depth-conversion checkpoints.

`composite` samples `depthtex0` at each pixel and two nearby UVs. A valid constant-depth neighborhood is cyan; a valid neighborhood with real depth variation is green; an out-of-range/NaN-style failure falls into magenta. The real-device scene must contain both open sky/background and nearby solid terrain so both cyan and green regions are visible. A fallback white texture stays constant and therefore cannot satisfy the green requirement.

The diagnostic writes only `colortex1`; `final` reads only `colortex1`. Passing proves that the post-world full-screen path receives a live, sampleable scene-depth image through the ordinary `sampler2D depthtex0` binding. Numerical reversed-Z-to-pack-window correctness is intentionally reserved for the later PHASE 8 depth-conversion gate.
