# PHASE 7 opaque particles contract

This developer fixture isolates the pre-deferred half of the quad-particle family. `gbuffers_particles` is the only geometry-family program present; it writes `colortex1`, which is cleared to black, and `final` displays only that target.

Minecraft 26.2's `smoke` particle uses `SingleQuadParticle.Layer.OPAQUE`, so it is a deterministic vanilla trigger for this checkpoint. A dense `/particle minecraft:smoke ...` burst in front of the camera is recommended. The vertex stage keeps all four `DefaultVertexFormat.PARTICLE` elements live through the legacy names (`Position`, `UV0`, `Color`, `UV2`) and checks the synthesized normal/light aliases; the fragment samples the real particle atlas.

Real-device acceptance must log the `particles` pass through `gbuffers_particles` at `PARTICLES`, prove that its first draw reads `minecraft:textures/atlas/particles.png`, show `[colortex1 MAIN]` plus the opaque coverage path, and produce visible green with no substantial magenta. A translucent-only draw does not close this gate even though `gbuffers_particles_translucent` may fall back to this file.