# deferred-mipmap-contract

PHASE 10 Deferred mipmap fixture. `deferred` writes a black/white checker to ALT. `deferred1` requests colortex0 mipmaps, must see its high LOD average, then writes a black/green checker to MAIN. `deferred2` requests the chain again and must see the newly rebuilt MAIN high LOD before producing CYAN. Missing generation, wrong half, one-time-only generation or failure to invalidate after a write becomes MAGENTA. Final only presents the latest target.
