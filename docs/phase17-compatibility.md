# PHASE 17 real shader-pack compatibility evidence

PHASE 17 records compatibility for real shader packs without assigning a status from a pack name, family, static source scan, or CI fixture. A final status is derived only from a completed real-device evidence record for an exact pack artifact and exact Vitrail/Metallum heads.

The roadmap matrix starts with Photon, Complementary, BSL-family, Sildur-family, and MakeUp. `tests/fixtures/phase17/catalog.json` names those rows but intentionally contains no status. The pack files themselves are not redistributed by this repository.

## Recorded outcome

No compatibility status is recorded for any of the five rows, and none can appear in `catalog.json`: that file names the matrix and deliberately carries no status column, so a status only ever comes from a reviewed evidence record. On 2026-09-18 the project owner closed PHASE 17 on the strength of the device sessions for one pack, which is a judgement that the phase is no longer worth blocking on rather than the per-row evidence this page defines. Nothing below was loosened to permit that: the classifier still refuses exactly the records it refused before, and a status still cannot be inferred from a pack name, family, source scan or CI fixture.

What the closure does and does not say:

- Photon v1.3b is the only row ever exercised on device. Complementary, BSL-family, Sildur-family and MakeUp have never run under Vitrail on Metal, so those rows are unverified rather than supported, and no session was recorded for any of them.
- Photon's own record was not written either, so its row carries no status in the matrix. The real-device observations behind the judgement are on Vitrail `484fdd2d` and its behaviour-neutral documentation successor `5ab260ab`, with code-bearing Metallum `82a0c75e`; they were kept in the agent memory rather than in this evidence path, and the section below is them, at the length a reader needs and no longer.
- Two Photon findings were open when the phase closed and are still open: the view-centred LPV voxel-centre mismatch around a stationary Nether-portal emissive light, and the abrupt terrain/render-distance boundary at the shared 256-block horizon with `BORDER_FOG` on. Closing the phase closes neither, and the absence of a status does not hide them.

## What the audit established, and what may not be reused as evidence

The sessions themselves, and what each one closes, are recorded where the port's status is kept:
[Current validation status](metallum-port.md#current-validation-status) carries the newest evidence, including the
hardware confirmation of the camera-list repair and the shadow-matrix publish defect that no run had named. What
follows is the part that is about *evidence* rather than status: the audit material behind those conclusions, the
things in the log that must not be read as findings, and the mechanism each open finding was narrowed to.

- **Five subsystems were audited against Iris 26.2** (`IrisShaders/Iris`, branch `26.2`) for the five Photon
  settings the ghosting symptom had been attributed to. Everything else the audit found is either unreachable for
  this pack or declared, and the lists are what a future symptom in these areas should start from: ten
  shadow-sampler divergences (declaration-driven comparison against `shadowHardwareFiltering`, the depth-channel
  swizzle Iris sets and this engine does not, attachment feedback, the HW names, the comparison sampler's filter and
  LOD, the `shadowcolor` chain, lookup LOD pinning); twelve depth divergences (depthtex0 on world programs,
  depthtex2's fall-through, the third image's lifetime, image-side against lookup-side conversion, `dhDepthTex*`,
  the sentinel comparison, `R2_FLOAT` colour targets, `gdepthtex` reach, centre depth); the lightmap and sky
  uniforms (`lightmap` is never sampled by this pack, `skyIntensity` is an engine uniform in neither); and the
  kept-target machinery, where the flip convention, the halves, the copy-back set, the clear set and the
  previous-frame matrices all match.
- **Two diagnostics must not be reused as evidence.** `docs/sky-and-shadows.md` said the shadow map is drawn at the
  end of a frame for the next one and that the one-frame lag is "the first thing to suspect for any shadow
  artefact"; that passage, and the same premise in `FrameState`, `ViewMatrices`, `TerrainDraw` and
  `ShadowGeometry`, is what let the publish path stay behind when the draw moved, and the documentation has been
  corrected. And this engine's "N targets are copied back from their far half" / "reads the frame before" lines are
  computed from the pack's **declaration text**, so they over-count: on Photon, `deferred` names `colortex6` and
  `colortex7` because those are the pack's own 3D-worley overrides (`program/d0_sky_map.fsh:44,46` over the blobs
  `shaders.properties:350-351` declares), not colour-target reads.
- **Eight supplied logs separate two symptoms that were being read as one**, told apart by the module-cache
  directory each records. `run1.log` is Vitrail `60ff5610`, the broken build, whose shadow stage stops on
  `ArrayIndexOutOfBoundsException: Render list is full` and then logs `Vitrail stopped drawing the shadow map after an error in the stage, so every shadowtex lookup of the pack reads the far plane` - which is the whole of the
  reported "the effects flashed and fell back to the original look". `run2.log` to `run6.log` are `16b051c1`, the
  repair's documentation-only successor, and each reaches the 600-frame census with the shadow map drawn 598 to 600
  times and no failure of any kind. The repair's shape is worth keeping: the restore walk now runs under a third
  frame token - a bit-30 one, distinct from the real camera token and from the sign-bit-flipped shadow token - so
  both shadow-overwritten and camera-only regions reset before being rebuilt, and the original manager frame, the
  top-level render lists, the saved camera tree and the task lists are restored afterwards. `latest.log` is
  `80b7131f`, the six-pack session, and `run7.log` a short `4dfb16c1` launch.
- **Shadow amortisation explains none of it.** It is armed at its default of one frame in every one of those logs
  and can only make an every-other-frame artefact on a pack that does not voxelise. Photon voxelises, so
  `amortisable` is false and its map is drawn every frame - which is why every Photon run counts 598 to 600 draws in
  a 600-frame window. The `300 times in the last 600 frames` that reads as an alternation appears only in the
  multi-pack session, in the loads whose pack is not Photon.
- **The collector's fatal markers missed the failure the shadow stage emits**, and this evidence is what found it
  rather than an inspection. `HIGH_CONFIDENCE_FATAL` matched `Vitrail stopped drawing this pack after an error`
  alone, while `TerrainDraw`, `EntityDraw`, `SkyDraw`, `CloudDraw`, `ParticleDraw`, `WeatherDraw` and `DistantDraw` each emit a stage-scoped form; the shadow one reads `Vitrail stopped drawing the shadow map after an error in the stage`. `run1.log` therefore collected with `fatal_failure=false` and the classifier would not have returned
  `Broken` for it. The pattern now matches the family, `tests/test_phase17_collect_session.py` pins the real message
  as its own fixture, and `run1.log` re-collects as `Broken`.
- **An earlier Mixin-shape regression is worth remembering for its shape**: module cache `44742449` reached the
  first level frame and then aborted before sky drawing because `SkyRendererMixin.vitrail$claim` required seven
  `RenderPass.draw(IIII)` matches while the 26.2 class contains two - the crash reported `(2/7) succeeded`, the
  other five sky methods using `drawIndexed(IIIII)`. An injector count asserted without a fixture is the fault.
- **OPEN - the view-centred LPV voxel-centre mismatch**, and the mechanism it was narrowed to. Photon computes its
  ahead-centred voxel origin from `floor(get_voxel_volume_center(gbufferModelViewInverse[2].xyz))`-equivalent source
  arithmetic and reprojects the previous light volume with both the camera-block and the previous/current
  view-centre deltas. This engine's one-frame shadow split physically reanchors only the cleared identity volume, by
  whole-block camera translation (`StorageImages.reanchor`), so a pure view rotation can leave `voxel_img` written
  under the previous integer view centre while the current shadow compute indexes it under the current one - and
  because the pack quantises that centre with `floor`, the mismatch changes in integer voxel steps, matching the
  observed small positional jumps and flicker. The tester's stationary A/B isolates it: `Voxel Volume Center =
  Ahead` shows the displacement while `Player` makes it essentially disappear under the same view-only rotation,
  and the resource path is healthy (the 128³ cleared `voxel_img` and the persistent `light_img_a/b` volumes
  allocate, `world0/shadowcomp` compiles and dispatches as groups `(4, 128, 128)` with local `(32, 1, 1)`, the pack
  reaches full frames and the session exits cleanly) - so the defect is in Vitrail's pack scheduling and semantics,
  not in Metallum execution. Iris's model-view-bobbing path was checked as an alternative explanation and ruled
  out: Iris likewise moves bobbing and nausea/spinning/portal transforms from the projection into the model view
  before publishing `gbufferModelView`, so this engine's corresponding matrix split is not the divergence to fix.
- **OPEN - the terrain/render-distance boundary transition**, and the hypothesis it is not. The earlier candidate was
  a reversed literal `smoothstep` in the cloud fade; the later `60ff5610` hardware run showed no improvement, the
  candidate was fully reverted (`c3a619e`) rather than carried as speculative compatibility policy, and no
  native-Metal smoothstep special handling remains. The tester now reads the abrupt transition as more likely a
  terrain/render-distance boundary colour transition. Photon enables `BORDER_FOG` by default precisely to hide the
  render-distance/chunk boundary, and in that run the view distance is 16 chunks while this engine's Iris-shaped
  horizon cone is also drawn at 256 blocks - so the next investigation is the terrain-depth / reconstructed
  scene-position / `border_fog` / sky-seed boundary at that shared distance, not cloud raymarching. No production
  fix is justified yet.
