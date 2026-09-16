# PHASE 12 Final chain contract

Developer-only fixture for the complete offscreen-chain -> Shader Pack `final` -> Minecraft main
target path.

`composite` writes RED on the left and GREEN on the right into `colortex0` ALT. `final` is the only
program that can turn those markers into BLUE on the left and YELLOW on the right. Anything stale,
clear, aliased, or sampled from the wrong half becomes MAGENTA.

Real-device acceptance requires Metal, `composite -> colortex0 ALT`, `final -> game's own target`,
real `colortex0` sampling, an F2 screenshot with LEFT BLUE / RIGHT YELLOW and negligible MAGENTA,
no chain/backend error, and clean shutdown.
