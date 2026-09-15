# PHASE 16 deterministic advanced-feature contract

Developer-only real-device fixture for the PHASE 16 features that can be judged deterministically in one shader-pack render without depending on the world scene.

The final image is divided into four vertical quarters, left to right:

1. **raw sampler3D** — a 1x1x2 red/blue raw volume is declared twice. The nearest copy must choose the blue logical slice at z=0.5, while the linear copy must interpolate the two slices to approximately half red / half blue.
2. **custom noise repeat** — the pack supplies a 2x2 noise image. `noisetex` is sampled one whole texture period apart; repeating sampling must return the same value.
3. **per-attachment blending** — `composite1` writes the same source into logical ranks 0 and 1 with `ONE ZERO` and `ZERO ZERO`. With `DRAWBUFFERS:31`, physical colortex3 must receive the source while colortex1 must become zero. The verdict is independent of the previous attachment contents.
4. **hardware shadow comparison** — `shadowtex0` is a `sampler2DShadow`. References -1 and 2 are outside the normalized depth range, so LEQUAL comparison must deterministically return 1 and 0 respectively regardless of the scene's shadow depth.

Each quarter is GREEN on success and MAGENTA on failure. `VerifyPhase16AdvancedScreenshot.java` judges the four quarters independently, so one failure identifies its feature rather than collapsing the whole phase to one colour count.

The fixture contains no Metal/MTL names or binding indices. For the comparison quarter, do not arm `vitrail/soft-shadow-compare`; that diagnostic deliberately measures the native comparison-sampler road.

This fixture does **not** claim to validate the remaining three PHASE 16 criteria: PBR normal/specular companions, camera motion vectors, or temporal accumulation. Those require resource-pack/camera/history state and are kept as separate checkpoints in the same real-device acceptance session rather than weakened into scene-independent fake tests.
