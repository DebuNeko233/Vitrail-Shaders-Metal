# PHASE 7 hand contract

This developer fixture isolates the first-person solid hand pass. `gbuffers_hand` is the only geometry-family program present; it writes `colortex1`, which is cleared to black, and `final` displays only that target.

The vertex stage checks the carried entity-polygon ABI (`mc_midTexCoord` and `at_tangent`) while the fragment samples the real `gtexture`. A served hand with a valid ABI writes opaque green; ABI failure writes magenta; no served hand remains black.

Real-device acceptance must log a `hand_*` entity piece through `gbuffers_hand` at `HAND_SOLID`. Use an ordinary opaque/non-translucent held item. A translucent block model belongs to the separate `hand-water-contract` checkpoint.
