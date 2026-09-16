# GBuffer target-format contract

This developer-only shader pack isolates PHASE 6's second GBuffer MRT acceptance item: a pack's declared target format must survive all the way through Vitrail's target plan, Minecraft's `GpuFormat`, Metallum's Metal texture allocation and the render-pipeline colour state.

`composite` writes four targets with deliberately different formats:

- `colortex4Format = R8`
- `colortex5Format = RG8`
- `colortex6Format = RGBA16F`
- `colortex7Format = RGB10_A2`

The values are chosen so the format itself changes what `final` reads. R8 and RG8 must supply zero for absent colour channels and one for absent alpha; RGBA16F must preserve a value too precise for RGBA8; RGB10_A2 must quantise alpha to its two-bit UNORM value. `final` converts each successful check back into the same red/green/blue/white quadrants used by the existing MRT screenshot verifier, and emits magenta on a failed format check.

This fixture necessarily performs controlled writes and reads in order to observe the format, but it closes only PHASE 6's format item. Clear policy, general write/sampling semantics and authoritative ping-pong remain separate acceptance gates.
