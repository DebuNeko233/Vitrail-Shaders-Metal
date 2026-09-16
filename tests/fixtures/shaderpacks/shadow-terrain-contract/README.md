# PHASE 9 shadow terrain contract

This developer-only fixture isolates the three Sodium chunk passes as seen from the light. It deliberately ships dedicated `shadow_solid`, `shadow_cutout` and `shadow_water` programs instead of relying on the generic `shadow` fallback so each route has its own log proof.

The three shadow programs share the real chunk vertex ABI and sample the real block atlas. Their diagnostic output to `shadowcolor0` is:

- solid terrain: green
- cutout terrain: yellow, after the texture alpha discard
- translucent/water terrain: blue
- carried-vertex/atlas contract failure: magenta

`composite` samples only `shadowcolor0` and copies it to `colortex1`; `final` displays only that diagnostic target. For real-device acceptance, enter the Overworld in daylight and frame a compact scene containing substantial solid ground, cutout foliage/grass and visible water. Wait for the shadow stage to run, then capture one F2 screenshot while all three diagnostic colours are visible.

This checkpoint proves the three terrain routes enter the pack-owned shadow stage on the real Metal GPU with the Sodium chunk ABI, real atlas sampling, light-space matrices, forward shadow depth test and the pack shadow attachment. `shadowcolor0` is used only as an observation carrier here. It does **not** close PHASE 9 shadow entities, shadow depth semantics/sampling, the shadow colour contract beyond this diagnostic carrier, or shadow mipmaps; those remain independent gates.
