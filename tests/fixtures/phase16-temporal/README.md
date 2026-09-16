# PHASE 16 motion vectors + temporal accumulation acceptance

Use a scene-preserving shader pack such as the existing `terrain-contract` fixture. Set Vitrail render scale below 100% (75% is a useful test point) and enable the temporal fold so `<game>/vitrail/temporal-fold` contains `true`.

After entering a world, allow the first resized/history frame to pass, then walk forward/backward and yaw the camera for several seconds. A one-time `Temporal fold asked for but the engine wrote no motion vectors this frame` warning immediately after resize is allowed: `MotionVectors` deliberately skips its fresh frame. The execution checkpoint is the later positive log line `Temporal fold armed: ... reprojected by the engine's motion vectors`, which is emitted only after a temporal draw succeeds and consumes a non-null motion-vector view.

Run:

`python3 tests/verify_phase16_temporal_log.py <latest.log> <game>/vitrail/pack.txt <game>/vitrail/temporal-fold>`

The verifier requires temporal enabled, render scale 25..99, a successful temporal draw consuming engine motion vectors, no hard motion/temporal allocation or pipeline failures, and clean `Stopping!`.

That log checkpoint proves execution and consumption, not vector direction or magnitude. The real-device session must therefore also visually check camera movement: static world detail should remain registered while translating and rotating the camera, with no persistent smear in the direction of travel or opposite-direction reprojection. This criterion is intentionally scoped to camera motion; per-object motion vectors remain a documented PHASE 16 limitation.
