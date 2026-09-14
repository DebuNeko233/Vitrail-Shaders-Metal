# GBuffer sampling contract

This developer-only shader pack isolates PHASE 6's fifth GBuffer MRT acceptance item: a later full-screen pass must sample the concrete colortex surfaces produced by an earlier pass.

`composite` writes deterministic spatial signatures only to `colortex4` and `colortex5`. `composite1` is the actual sampling checkpoint: it reads only those two samplers, validates the values at the current texel, and writes `colortex6` / `colortex7`. `final` reads only `colortex6` / `colortex7` and exposes the result as the standard red/green/blue/white quadrants; any failed upstream sample becomes magenta.

No pass both reads and writes the same target. Passing this fixture therefore closes only the **sampling** gate; authoritative same-target ping-pong remains the next, separate acceptance item.
