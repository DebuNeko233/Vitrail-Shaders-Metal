# PHASE 7 translucent particles contract

This developer fixture isolates the post-deferred half of the quad-particle family. Only `gbuffers_particles_translucent` is present, so the opaque half has no particle fallback to borrow from this fixture. The program writes isolated `colortex1`, cleared to black, and `final` displays only that target.

Minecraft 26.2's `witch` particle uses `SingleQuadParticle.Layer.TRANSLUCENT`, making it a deterministic vanilla trigger. A dense `/particle minecraft:witch ...` burst in front of the camera is recommended. The same four-element particle vertex ABI and real particle-atlas sample are kept live as in the opaque checkpoint.

Real-device acceptance must independently log `particles_translucent` through `gbuffers_particles_translucent` at `PARTICLES`, prove a first draw from `minecraft:textures/atlas/particles.png`, show `[colortex1 MAIN]`, and produce visible green with no substantial magenta. Passing the opaque checkpoint is not evidence for this post-deferred route.