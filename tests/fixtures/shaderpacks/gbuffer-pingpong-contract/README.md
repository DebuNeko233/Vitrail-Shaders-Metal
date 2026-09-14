# GBuffer ping-pong contract

This developer-only shader pack isolates PHASE 6's final GBuffer MRT acceptance item: every same-target read must see the surface produced by the last preceding write, with Vitrail's `TargetSchedule` as the authority for the MAIN/ALT half.

`composite` writes spatial signatures to `colortex4` / `colortex5`. `composite1` samples those exact targets, validates the first signatures, and writes new signatures back to the same two logical targets. `composite2` samples the same targets again, validates the second signatures, and writes the standard red/green/blue/white result back to those same targets. `final` samples the final logical `colortex4` / `colortex5` state and exposes it as quadrants. Any stale-half read becomes magenta.

Because two consecutive passes both read and write the same logical targets, a per-frame MAIN/ALT guess cannot pass this fixture. Passing it closes only PHASE 6 authoritative colour-target ping-pong; later shadow/deferred work remains separate.
