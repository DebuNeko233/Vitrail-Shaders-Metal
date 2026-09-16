# PHASE 7 hand_water contract

This developer fixture isolates the first-person translucent hand pass. `gbuffers_hand_water` is the only geometry-family program present; it writes `colortex1`, which is cleared to black, and `final` displays only that target.

`hand_water` means Iris-compatible hand-pass routing, not that the player is physically underwater. `HandDraw` assigns a hand to this second pass when the held item is a block item whose model is marked translucent. The vertex stage checks the carried entity-polygon ABI while the fragment samples the real `gtexture`.

Real-device acceptance must therefore hold a translucent block model (glass is recommended), log a `hand_water_*` entity piece through `gbuffers_hand_water` at `HAND_TRANSLUCENT`, and produce visible green with no substantial magenta.
