# PHASE 7 weather acceptance contract

This fixture isolates the game's rain/snow curtain behind `gbuffers_weather`.

The accepted route is `gbuffers_weather @ RAIN_SNOW`, drawn after deferred with the game's `DefaultVertexFormat.PARTICLE` mesh. Rain and snow are two draws from the same weather buffer and pipeline; only the sampled image changes, so either a real `minecraft:textures/environment/rain.png` or `snow.png` draw is valid resource evidence for this checkpoint.

The shader keeps Position/UV0/Color/UV2-derived legacy inputs live, samples the real weather texture, writes only `colortex1`, and produces stable green for a valid ABI/resource path or magenta for ABI failure. `final` displays only `colortex1`.

This fixture does not test particles, clouds or sky, and weather writes no coverage mask because it is drawn after the scene seed/deferred stage.