# Terrain contract smoke shader pack

This directory is a developer-only shader-pack fixture. It is not packaged into Vitrail and it is not a compatibility sample for end users.

It is the PHASE 5 terrain smoke for the Vitrail + Metallum Apple-Silicon path. The three camera terrain passes are deliberately served by different files so pass routing is visible in the final image instead of inferred from logs:

- `gbuffers_terrain_solid` writes opaque red.
- `gbuffers_terrain_cutout` writes green but preserves the sampled block texture alpha in legacy `gl_FragColor`. Vitrail must therefore inject the terrain cutout alpha test after the pack main. Leaves, flowers, fire and grass-overlay geometry must keep their real silhouettes instead of becoming green rectangles/cubes.
- `gbuffers_water` writes blue at 0.75 alpha. The translucent terrain pass must blend it over what was already in colortex0 rather than replacing the destination as an opaque draw.
- `final` only samples colortex0. There is no composite colour generator hiding a terrain mistake.

The root `gbuffers_terrain` is also present as a red fallback for terrain-family programs not named by this smoke. The direct solid/cutout/water programs must still win for their own passes.

## Real-device scene

Put representative content in one camera view before taking the screenshot:

1. ordinary opaque blocks such as stone/dirt;
2. grass-block side overlay;
3. at least one flower;
4. leaves;
5. fire;
6. water with opaque terrain visible behind or below it.

Expected result: opaque terrain is red, cutout geometry is green with its transparent texels still absent, and water is visibly blue but blended with the destination. The automated screenshot verifier only checks that substantial red, green and blue regions all reached the final framebuffer. It cannot prove the shape of transparent texels, so the cutout silhouettes are a separate visual acceptance item.

Run through the experimental Metal smoke launcher with `--terrain-fixture`. Press F2 once with all three pass classes visible, then exit normally. Record the launcher verifier output, the screenshot, first-draw/pass logs for solid/cutout/translucent, and the absence of Metal/render-thread/native failures.
