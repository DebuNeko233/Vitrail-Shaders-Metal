# PHASE 16 deterministic advanced-feature contract

Developer-only real-device fixture for four PHASE 16 features. Three are scene-independent; the per-attachment blend checkpoint deliberately uses real opaque terrain because Iris applies per-buffer blend overrides to gbuffers geometry programs, not composite/full-screen programs.

The final image is divided into four vertical quarters, left to right:

1. **raw sampler3D** — a 1x1x2 red/blue raw volume is declared twice. The nearest copy must choose the blue logical slice at z=0.5, while the linear copy must interpolate the two slices to approximately half red / half blue.
2. **custom noise repeat** — the pack supplies a 2x2 noise image. `noisetex` is sampled one whole texture period apart; repeating sampling must return the same value.
3. **per-attachment blending** — `gbuffers_terrain_solid` writes the same marker into `DRAWBUFFERS:31`. The properties use the Iris buffer-addressed form, `blend.gbuffers_terrain_solid.colortex3=ONE ZERO` and `blend.gbuffers_terrain_solid.colortex1=ZERO ZERO`. The plan must resolve physical colortex3/1 to attachment ranks 0/1, so visible opaque terrain leaves the marker in colortex3 and zero in colortex1. The judge paints correct terrain GREEN, an incorrect blend result MAGENTA, and untouched background BLACK. Face a large opaque block surface so the third quarter contains a substantial GREEN terrain region; black-only evidence does not pass the screenshot verifier.
4. **hardware shadow comparison** — `shadowtex0` is a `sampler2DShadow`. References -1 and 2 are outside the normalized depth range, so LEQUAL comparison must deterministically return 1 and 0 respectively regardless of the scene's shadow depth.

VOLUME, NOISE and COMPARE are overwhelmingly GREEN on success and MAGENTA on failure. BLEND is GREEN only where the real solid terrain marker was drawn, BLACK where no solid terrain covered the pixel, and MAGENTA where drawn terrain produced the wrong per-attachment result. `VerifyPhase16AdvancedScreenshot.java` therefore requires substantial GREEN and very little MAGENTA in the BLEND quarter while retaining the strict whole-quarter checks for the other three.

The fixture contains no Metal/MTL names or binding indices. For the comparison quarter, do not arm `vitrail/soft-shadow-compare`; that diagnostic deliberately measures the native comparison-sampler road.

This fixture does **not** claim to validate the remaining three PHASE 16 criteria: PBR normal/specular companions, camera motion vectors, or temporal accumulation. Those require resource-pack/camera/history state and are kept as separate checkpoints in the same real-device acceptance session rather than weakened into scene-independent fake tests.
