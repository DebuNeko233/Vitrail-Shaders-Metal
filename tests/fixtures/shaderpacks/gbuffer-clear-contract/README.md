# GBuffer clear contract

This developer-only shader pack isolates PHASE 6's third GBuffer MRT acceptance item: each colour target must receive the clear policy and clear colour the pack declared, on the correct texture and attachment.

`composite` declares explicit per-target clears:

- colortex4 = red
- colortex5 = green
- colortex6 = blue
- colortex7 = white

Only colortex4 and colortex5 are attached by `composite`, and the fragment discards every pixel. Their visible values therefore have to come from the render-pass load-op clear. colortex6 and colortex7 are sampled by `final` but are not attached by `composite`; their pending clear debt must therefore be paid by Vitrail's `flushPending` path before the chain reads them. `final` displays all four as the established red/green/blue/white quadrants.

The first frame after allocation also performs Vitrail's mandatory full initialization clear. The fixture is intended to be observed after the world is visibly running, so steady-state frames exercise the deferred per-frame clear path as well. CI separately locks the `colortexNClear = false` parser branch even though this four-colour runtime fixture keeps all four visible targets clearing every frame.

This fixture uses sampling only as the observation mechanism and closes only PHASE 6 clear semantics. General write, general sampling and authoritative ping-pong remain separate gates.