# Active Tasks

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## P0 - PHASE 17 real shader-pack compatibility (closed by owner judgement, evidence unrecorded)

The synthetic/runtime capability phases are closed, and PHASE 17 was closed on 2026-09-18 by the project owner's judgement on the Photon device sessions rather than by the reviewed per-row evidence its own policy defines. No compatibility status is recorded for any row, and none can be: `catalog.json` rows are contractually limited to `id` and `label` (`test_phase17_compatibility_contract.py`), and the classifier refuses an unreviewed or unclean session, so a status has exactly one road and nothing travelled it. `docs/phase17-compatibility.md` records the outcome. The items below are a record of what was checked and what was left unverified, not a gate that still blocks.

- [x] Define the exact five public statuses: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`.
- [x] Refuse incomplete/unreviewed evidence instead of inferring compatibility from CI, warnings or a plausible image.
- [x] Collect Metal/device/exact heads, pack/log/screenshot SHA-256, real Vitrail draws, raw fallback observations, fatal observations and clean shutdown from one launcher-managed hardware session.
- [x] Keep the shader-pack artifact out of the review bundle.
- [x] Require exactly one fresh F2 screenshot per future launcher run.
- [x] Hash the staged artifact Minecraft actually tested for future launcher runs.
- [x] Add a Vitrail-owned bundle verifier that rejects malformed/extra files and verifies log/screenshot hashes without extracting the archive.
- [x] Fix the generic optional-Distant-Horizons warm-up bug by making warm-up eligibility backend-neutral and refusing unavailable distant families before detached precompile.
- [x] Fix generic active-path pack macro redefinition so a later pack `#define` replaces an earlier active pack definition without blanket-undefining engine/compiler environment macros.
- [x] Real-device verify those two fixes with Vitrail `54f454bc`: Photon, MakeUp and Complementary no longer fail warm-up on unavailable `distant*` programs, and Bliss no longer fails on `diagonal3` redefinition.
- [x] Re-run Photon and verify it compiles past the former `world0/prepare/vertex` blocker, reaches a first full frame and clean shutdown.
- [x] Verify real Photon compute execution after the compile blocker: `world0/deferred4_a` compiles and dispatches on Metal as groups `(1, 1, 1)` / local `(256, 1, 1)` while its fixed oversized shared allocation uses the existing transient-storage fallback.
- [x] Reconfirm a second real compute path with Solas `shadowcomp`, dispatched as groups `(24, 12, 24)` / local `(8, 8, 8)`.
- [x] Real-device verify Metallum `82a0c75e`: Bliss `world0/composite2` no longer receives a false wide-resource promotion from Minecraft 26.2's fixed 16-slot vertex-format array, while Photon's genuinely wide `deferred4` remains on the Argument Buffer path.
- [ ] Do not promote Photon, Bliss or any other pack from runtime logs alone. Collect/review the screenshot/reference evidence required by `docs/phase17-compatibility.md` before changing a public compatibility status.
- [x] Read the next hardware run's fatal marker and not only its frame count.
  - The marker the shadow stage emits is not the one the collector matched. `HIGH_CONFIDENCE_FATAL` held `Vitrail stopped drawing this pack after an error` alone; `TerrainDraw`, `EntityDraw`, `SkyDraw`, `CloudDraw`, `ParticleDraw`, `WeatherDraw` and `DistantDraw` each emit a stage-scoped form, and the shadow one reads `Vitrail stopped drawing the shadow map after an error in the stage`. The `60ff5610` log is the case: it collected with `fatal_failure=false`, so a run whose whole scene lost its shadows would not have been classified `Broken`.
  - The pattern now matches the family, `tests/test_phase17_collect_session.py` carries that run's own message as a fixture, and re-collecting the real log yields `fatal_failure=true` and `Broken`.
  - Retained as a habit rather than a task: a run is read for the marker family, not only for its frame count and shadow-draw census.
- [x] Close the reported position-shifted ghosting on cutout vegetation, found by auditing against Iris rather than by a run.
  - The four names `ShadowMatrixValues` publishes were the `map` fields, whose premise - the map on hand was drawn at the end of the previous frame - `cd52b13f` removed when it moved the draw into the frame for voxelising packs. Every pack shadow lookup was therefore displaced by one draw: invisible while the camera was still, a displaced shadow as soon as it moved, worst on leaf and grass self-shadowing, and read by `SHADOW`, `SSS` and `AO_IN_SUNLIGHT` alike. `484fdd2d` publishes `drawnShadowModelView` and its three siblings, which is the pair `ShadowGeometryValues` has used for the shadow programs all along, and `tests/test_shadow_terrain_contract.py` pins the four names to that accessor.
  - Confirmed on hardware at `484fdd2d` by the tester. Turning Photon's `WAVING_PLANTS`/`WAVING_LEAVES` off before the fix changed nothing, and that is the observation which ruled the waving-vertex explanations out.
- [ ] Correct the four remaining code comments that still describe the removed end-of-frame draw: `render/ViewMatrices.java:86`, `render/TerrainDraw.java:582`, `render/ShadowGeometry.java:38` and `uniform/values/ShadowGeometryValues.java:20`. Their surrounding reasoning has to be re-read against the current order before it is rewritten, which is why the documentation and `FrameState` were corrected first.
- [ ] Do not reuse Vitrail's "reads the frame before" count as evidence. It is computed from the pack's **declaration text**, and on Photon it names `colortex6`/`colortex7` for `deferred`, which are the pack's own 3D worley overrides rather than colour-target reads.
- [ ] Decide whether `ShadowGeometryValues`'s override of those four names should narrow. After `484fdd2d` both halves answer the same thing on every frame, and the layer is kept only because it also publishes `of_ModelViewProjectionMatrix` and the basis.
- [ ] Decide whether Bliss, Solas and Sundial Lite belong in the PHASE 17 catalog.
  - `tests/fixtures/phase17/catalog.json` carries five rows (`photon`, `complementary`, `bsl`, `sildur`, `makeup`) and `test_phase17_compatibility_contract.py` asserts that list exactly, so the three packs the recent hardware runs actually exercised cannot have observations recorded against the matrix.
  - A new row is a catalog change plus a contract change. The roadmap's PHASE 17 wording covers "other large OptiFine/Iris packs", so the decision is about which packs are worth a standing row rather than whether they are eligible.
- [ ] The matrix rows other than Photon remain unexercised: Complementary, BSL-family, Sildur-family and MakeUp have never run under Vitrail on Metal. PHASE 17 closed before they did, so this is unverified coverage rather than a blocking item, and any future session still has to go through the same evidence discipline.

The latest hardware run pairs Vitrail `5ab260ab` (behaviour-neutral over code-bearing `484fdd2d`) with Metallum `82a0c75e` on Apple M5 Pro / macOS 27 / native Metal, over eight pack opens and eight first full frames. It closes the cutout-vegetation ghosting: the four shadow matrices a pack reads are now the pair the shadow stage actually drew with, and the tester reports the ghosting gone at that head. The earlier `60ff5610` Sodium `Render list is full` overflow is closed on hardware as well, across the five `16b051c1` sessions that each reach the 600-frame shadow census with 598 to 600 draws and no failure of any kind. Still open at this head: the view-centre gap behind the stationary Nether-portal emissive light, ordinary shadow terrain and a non-view-centred voxel pack after the scheduling move, and the 256-block terrain/sky boundary transition.

## P1 - Close remaining source-classified rendering gaps

### Vertex / sampler diagnostics

- [x] Correct the entity-shadow `at_midBlock` diagnostic without extending the entity vertex ABI.
  - Iris 26.1 `IrisVertexFormats.ENTITY` carries `iris_Entity`, `mc_midTexCoord` and `at_tangent`, but not `at_midBlock`.
  - Iris 26.1 shader keys use `ENTITY` for ordinary entities and shadow entities.
  - `VertexInputDiagnostics` keeps real mesh-backed answers separate from reference-unbacked inputs; the entity bridge contributes `at_midBlock` only to the missing-input diagnostic filter for regular entity rows.
  - Hardware runs after the fix contain no `at_midBlock` diagnostic while shadow entity draws remain active.
- [x] Classify particle/weather `mc_Entity`, `mc_midTexCoord` and `at_tangent` as reference-unbacked inputs rather than missing real mesh fields.
  - Iris 26.1 `ShaderKey` uses `DefaultVertexFormat.PARTICLE` for both particle keys and weather; that format has no backing element for those three pack extension locations.
  - Iris's core transformer leaves `mc_Entity` untouched when the format reports zero entity components and does not synthesize real `mc_midTexCoord` or `at_tangent` fields for this format.
  - Vitrail keeps the real particle/weather answer set empty and uses its existing deterministic synthesized constants. This is a diagnostic classification only: it does **not** claim value-for-value parity with OpenGL generic-attribute state.
  - Hardware runs after `37d06c12` show particle and weather draws across the exercised pack set with the former warnings absent.
- [x] Classify the observed sky `mc_Entity` / `mc_midTexCoord` warnings without changing sky mesh formats.
  - Iris 26.1 sky keys use `POSITION`, `POSITION_COLOR`, `POSITION_TEX` or `POSITION_TEX_COLOR`; none carries either extension input.
  - With zero entity components, `VanillaCoreTransformer` leaves a pack-declared `mc_Entity` unbacked. An explicit `mc_midTexCoord` likewise remains an input rather than becoming a sky vertex element.
  - Vitrail keeps the four existing sky formats unchanged and places only those two names in the sky reference-unbacked diagnostic set.
- [x] Classify the observed line-row `vaUV2` / `mc_Entity` warnings without changing the line mesh ABI.
  - Iris 26.1 `LINES` uses `DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH`, which carries neither UV2 nor an entity id.
  - `VanillaCoreTransformer` renames `vaUV2` to `iris_UV2` even when `hasLight()` is false and still declares it as an input; no UV2 element backs it on the line format. Zero entity components likewise leave `mc_Entity` unbacked.
  - `LinesVertex.ANSWERED` remains only `vaPosition`, `vaNormal` and `vaColor`; no line stride or format changes.
- [x] Hardware-verify the exercised `bc5e180a` sky/line diagnostic paths.
  - Photon sky and line draws remain active with the former `mc_midTexCoord` / `vaUV2` warnings absent.
  - Solas sky draws remain active with the former `mc_Entity` warning absent.
  - Solas lines did not draw in that particular rerun, so its previously observed line `mc_Entity` path remains source-classified but should be reconfirmed the next time it is naturally exercised.
- [ ] Keep comparison-vs-ordinary shadow sampler warnings explicit unless source/reference evidence identifies a defined behavior Vitrail is missing. The current mixed declaration is already diagnosed as undefined under Iris too.
- [ ] Treat `nothing fills them yet` first-frame resource warnings as evidence to investigate only when they correspond to a persistent semantic/visual mismatch; do not convert warning count into a compatibility score.

### Claimed sky ownership

- [x] Source-classify the Bliss sky anomaly as a scene-seed ownership problem rather than a Metal attachment problem.
  - The translated coverage write is after the pack fragment `main`; a pack-authored `discard` therefore kills the coverage epilogue as well as the colour output.
  - `SceneSeed` sees only the depth-valued coverage image, so without another ownership mark it interprets that pixel as unanswered and can paint the game's own sky back into the pack target.
  - `SkyDraw.Element.covers` already expresses the missing semantic: disc, dark disc and End sky claim every pixel their mesh spans. Terrain/entity discard semantics are different and must not be widened.
- [x] Implement sky-only ownership replay without a pack-name special case.
  - `SkyOwnership` builds a sibling pipeline with the same translated pack vertex stage, vertex layout, topology, culling and bind-group layouts.
  - All pack colour slots are unused on the sibling; only the existing coverage slot is written, with raster `gl_FragCoord.z`.
  - The ordinary sky draw records first, then the same vertex range is replayed for ownership. The horizon cone does the same immediately after its own draw while its vertex buffer is still bound.
  - The pack fragment stage is not executed on the replay, so pack fragment `discard` cannot turn semantic sky ownership into scene-seed fallback.
  - No terrain, entity, particle, weather, hand or Metallum code changes.
- [x] Match the ownership replay hook to Minecraft 26.2's two draw forms after the first hardware attempt exposed a Mixin count failure.
  - `renderDarkDisc` and `renderSunriseAndSunset` use direct `draw(IIII)`; the direct hook requires exactly 2 matches.
  - `renderStars`, `renderSun`, `renderMoon`, `renderEndSky` and `renderEndFlash` use `drawIndexed(IIIII)`; the indexed hook requires exactly 5 matches and replays the same index arguments.
  - The failed `44742449` run reached `(2/7) succeeded` before the first sky draw, so it does not validate the ownership picture.
- [x] Allow a zero-colour-output fragment to reserve coverage at rank 0, so claimed sky ownership has an attachment even when `gbuffers_skybasic` only discards.
  - `GlslTranslator.planCoverage` no longer rejects `maxFragmentOutput == -1`; `Emitter` already places coverage at `maxFragmentOutput + 1`, which is 0 in that case.
  - `GeometryProgram` treats zero pack outputs as a coverage-only pass and omits the inert default colortex attachment, so rank 0 is not occupied by an image the shader never writes.
  - Other families keep fragment-survival coverage: without the sky sibling, a fragment `discard` still prevents the ordinary coverage epilogue from writing.
- [x] Fix the zero-output coverage wrapper exposed by the `0caa74ba` hardware run.
  - `gbuffers_skybasic` correctly receives `ofCoverage` at rank 0, but the generated wrapper called `ofOrderOutputs()` without emitting the helper because `orderFragmentOutputs` still treated `maxFragmentOutput == -1` as "no output".
  - Coverage now counts as the one output for ordering, so the helper is emitted and names `ofCoverage` before the pack body.
- [x] Make the sky ownership sibling attachment-compatible with the render pass it reuses.
  - Minecraft 26.2 validates every non-null render-pass colour attachment against a non-null pipeline target state of the same format at `RenderPass.setPipeline`.
  - The sibling now preserves each ordinary pack slot's format and blend state with `WRITE_NONE`, and writes only the existing coverage slot; genuinely unused/null slots remain unused.
- [x] Real-device verify the repaired ownership replay structure at `ac33fed3 + 82a0c75e`.
  - Solas `gbuffers_basic` disc records its draw and horizon cone and continues without the former `RenderPass.setPipeline` attachment-format rejection.
  - Photon outputless `world0/gbuffers_skybasic` reports coverage-only rank 0, records its first draw and horizon cone, and continues without a missing `ofOrderOutputs()` compile failure or sky sibling warning.
  - This is structural runtime evidence only; the run did not load Bliss and did not exercise End sky.
- [ ] Turn the tester's simple Bliss visual check into reviewed PHASE 17 screenshot/reference evidence; the reported frame has no obvious issue, but informal observation alone does not promote the pack.
- [ ] Exercise the End sky branch if practical, because it is the other `covers=true` branch besides the two overworld discs.

### GLSL compiler-builtin shadowing

- [x] Source-fix Sundial Lite v1.1.0's first deterministic blocker without a pack-name special case.
  - Its `root/final/fragment` declares pack-local `min3` and `max3` helpers and shaderc reports a parameter-precision overload mismatch before the final pass can compile.
  - Vitrail already renames a pack-defined function when the compiler target reserves the same function name; the trinary min/max family was missing from that set.
  - The generic shadowable-builtin set now includes `min3`, `max3` and `mid3`, and only fires when the unit declares the function itself.
- [x] Hardware-rerun Sundial Lite v1.1.0 at `97dcf76d`: leftover pipelines compile, the chain can draw and a first full frame completes, so the former `root/final/fragment` trinary min/max blocker is closed. Continue from a new owning failure only if one is observed.

### View-centred voxel identity timing

- [x] Source-classify Photon's camera-following coloured-light position as a Vitrail one-frame scheduling mismatch.
  - Photon can center its voxel volume ahead of the player from `gbufferModelViewInverse[2]`, so rotating the view changes the integer voxel-center offset even when camera position is fixed.
  - Vitrail's shadow terrain writes the identity volume at the end of the previous frame while shadow compute runs at the head of the current one. The current storage-image reanchor compensates whole-block camera translation only.
  - The current compute therefore may read identities centered for the previous view while propagating/sampling light under the current view center, matching the observed emissive-source drift on view rotation.
- [x] Hardware-reconfirm at `97dcf76d + 82a0c75e` that Photon still shifts and slightly flickers around a stationary emissive Nether portal while its storage-image and `shadowcomp` execution remain healthy.
  - `voxel_img` is allocated as a cleared 128³ identity volume and physically camera-block reanchored; `light_img_a/b` are persistent 128³ floodfill volumes.
  - `world0/shadowcomp` compiles and dispatches as groups `(4, 128, 128)` / local `(32, 1, 1)`, and the pack reaches full frames and clean exit.
  - Iris's matrix-bobbing path matches Vitrail's placement of bob/nausea/portal effects in `gbufferModelView`, so that matrix split is not the remaining divergence.
- [x] A/B Photon with `Voxel Volume Center = Player` while standing still and only rotating: the displacement/flicker basically disappears compared with `Ahead`. This isolates the missing view-centre term; do not auto-force the option in production.
- [x] Implement the generic source correction without Photon names, Photon center arithmetic, shader-option forcing or Metallum pack policy.
  - The shadow terrain/voxel writer now runs at the frame head after Sodium's camera cull and before `shadowcomp`, so writer and compute share current camera/view uniforms as they do under Iris.
  - Sodium's exact camera `Viewport` / fog inputs are captured from `setupTerrain`. The light walk uses a sign-bit-flipped frame token and the synchronous private out-of-graph builder, then draws through Sodium 0.9.2's `drawChunkLayer`. Camera restoration uses the saved `renderTree` to choose the matching private list-builder. Neither path calls `finalizeRenderLists`, so the extra shadow scope never mutates camera timing control; the original render-list/tree/task references are restored afterwards.
  - The existing whole-block storage-image reanchor remains as a conservative fallback; the normal same-frame path should now request a zero move.
- [x] CI-verify the same-frame shadow scheduling/Mixin signatures at code-bearing head `11e2569f`: build #280 passes the smoke/contracts step and the full `./gradlew build` on the macOS arm64 runner.
- [x] Hardware-rerun Photon in `Ahead` mode at Vitrail `c296caec`: the stationary Nether-portal emissive light no longer shifts or slightly flickers while the view rotates; `shadowcomp` and the shadow draw paths remain active through a full frame.
- [x] Hardware-run the later Sodium-list isolation at `60ff5610` and capture its first owning regression: after normal Photon startup, `ShadowTerrain.restoreCameraWalk` eventually causes Sodium `ChunkRenderList.add` to throw `Render list is full`, so Vitrail disables the shadow stage.
- [x] Fix camera-list restoration generically at code-bearing `faed8e`.
  - A full camera repair traversal must not reuse the real camera frame token: camera-only regions already have that token and an already-filled persistent list, so revisiting them appends duplicates instead of resetting.
  - The restore walk now uses a third bit-30 token distinct from both the real camera token and sign-bit shadow token. Every camera-visible region therefore resets before the repair traversal; the original frame/list/tree/task references are restored afterwards.
  - No pack name, voxel-center formula or Metallum behavior is involved.
- [x] CI-verify `faed8e`: build #290 passes optional Metallum/smoke contracts and full `./gradlew build`; commit policy is green.
- [ ] Hardware-rerun current Vitrail long enough to cross the former list-overflow point, require no `Render list is full` / shadow-stage shutdown, then reconfirm Photon Ahead-mode portal lighting remains stable.
- [ ] Reconfirm ordinary shadow terrain and one non-view-centred voxel pack after the scheduling move.

### Distant terrain / sky boundary transition

- [x] Reject the reversed-`smoothstep` cloud hypothesis for the observed abrupt distant transition.
  - The `60ff5610` hardware run showed no visual improvement, and the tester now identifies the transition as likely where terrain stops loading rather than a cloud cutoff.
  - The native-Metal reversed-smoothstep candidate was fully reverted in `c3a619e`; no speculative cloud compatibility rewrite remains in production.
- [x] Source-classify the next diagnostic path without changing rendering.
  - Photon enables `BORDER_FOG` by default and describes it as thick fog at the render-distance edge to hide chunk borders.
  - Without a LoD mod, `border_fog` derives its fade from reconstructed terrain `scene_pos.xz / far`; Vitrail publishes Iris-compatible `far = effectiveRenderDistance * 16`.
  - The failing run uses 16 chunks, hence `far = 256` blocks, and Vitrail's Iris-shaped horizon cone is also drawn at 256 blocks. The shared boundary makes terrain depth/reconstruction, border fog and sky/scene-seed handoff the owning area to inspect next.
- [ ] After the shadow-list regression is hardware-closed, inspect the same horizon with Photon `BORDER_FOG` default-on. A/B it off only as a diagnostic: if the edge barely changes, trace why the intended terrain border fade is not affecting the final image; if it changes materially, compare the border-fog colour/terrain-to-sky handoff rather than cloud raymarching.

## P1 - Make the pack load and switch wait legible

A pack compile holds the world back, so the screen used to be the frame from before the pack was replaced with only a small corner mark on it. The wait is 2.5 to 3.7 s per switch in the latest session.

- [x] Give the held-world wait a loading page instead of a bare black screen, drawn in the shape the game's own terrain loading screen uses.
  - `LoadPage` stands exactly while `PackChain.warming()` holds the level back and leaves when the world returns, fading over the corner card's own 300 ms ramp with the corner already drawn underneath it, so the handoff is one mark moving rather than two marks taking turns.
  - The bar is `LevelLoadingScreen.drawProgressBar`'s, carried to the pixel: 200 by 2 at `centreX - 100`, black track, green fill, and the bar's top at the sentence's top plus the font's line height plus three. The page's flat black background is `LoadingOverlay.LOGO_BACKGROUND_COLOR_DARK` rather than an invented dim, because the buffer underneath holds the pre-switch frame and a veil would show the wrong world.
  - The sentence and the bar are one reading of the chain (`PackChain.compilingState()`), so the count in the words and the fill of the bar cannot be a program apart.
  - The corner is unchanged for the background compiles that follow the world's return, where a page over a world being played would be in the way. `tests/test_load_page_contract.py` pins the geometry, the colours, the single read, the shared mark clock and the draw order, and `build.yml` names it.
  - Adversarial review of the first cut confirmed three state defects and one geometry error, all fixed and each now pinned by a test that fails without the fix. **The page is gated on `warming()` alone, not behind the corner's own guard**: the two answer different questions, and `compilingState()` is empty on frames where the warm-up workers have finished while `drawable()` is still false, so sharing the corner's answer left the held world covered by nothing. **The lift is timed from the last frame the world was seen held, not from the first frame that notices it is back**: the page is not drawn at all under F3, F1 or where the corner has gone quiet, so that frame can be arbitrarily late and the page would then paint itself opaque over a world already being played. **The fraction follows the count instead of latching its high-water mark**: the total grows as each family's translation lands, so a pinned bar sits full while the sentence above it reads otherwise, and a `Math.max` also carried a previous load's fill into a load whose count was not known yet. **The words and the fraction are reset with the rest of the per-load state**, which the first cut missed. The geometry error was the reviewer's sharpest catch: the terrain loading screen's `12` is its line height *plus* three, and applying it as an extra gap drew the bar a whole line lower than the screen the file claims to copy, which the original test could not see because it asserted the constant rather than the expression.
- [ ] Have the page itself reviewed on device. It is source and CI evidence: no screenshot of it has been judged, and the durations above come from the `5ab260ab` log rather than from a measured page.

## P1 - Performance, Metal 4 and MetalFX (see `docs/performance.md`)

- [ ] **M3's render half: the frame-path move was tried and reverted, and what it cost is now measured.**
  Moving `MetalCommandEncoder`, `MetalRenderPass` and `MetalFence` into `com.metallum.render.metal3` as a
  pure move produced **100 compile errors, every one of them "class/member is not public"**: the render
  package is package-private throughout, so a move out of it either widens a dozen engine-internal classes
  and dozens of their members in place, or is done together with the shared layer where public is the design.
  The attempt was reverted rather than pushed through - an API widened to satisfy a move is not the same
  decision as an API widened because it is the neutral vocabulary - and the tree is green at the wrapper
  milestone. **Next attempt's order: (1) `com.metallum.render.shared` with the version-neutral set
  (`MetalGpuBuffer`, `MetalGpuTexture`, `MetalGpuTextureView`, `MetalGpuSampler`, `MetalTransientMemory`,
  `MetalDestructionQueue`, `MetalGpuQueryPool`, `MetalPipelineSupport`, `MetalCompiledRenderPipeline`,
  `MetalFrameProbe`, `AttachmentContents`, `Stats`), public by design; (2) then `render.metal3` for the frame
  path importing `render.shared.*`; (3) `MetalDevice` stays a facade and loses the concrete generation - the
  queue must come from `MetalExecutionServices`, not from the device, or the split will force imports between
  the two generation packages.**
- [ ] **M3 of the dual-execution plan: isolate the existing implementation as Metal 3 (wrapper half done).**
  Done: `com.metallum.mtl.metal3` with the six command wrappers and `com.metallum.mtl.metal4` with the three
  Metal 4 wrappers, imports and contracts repointed, behaviour
  unchanged on hardware. Remaining: `com.metallum.render.metal3` (the frame path: `MetalCommandEncoder`,
  `MetalRenderPass`, `MetalComputeBridge`, `MetalDepthMipmapBridge`, `MetalSurface` and the `Metal3*` seats
  the specification names) and `com.metallum.render.shared` for the version-neutral resource classes.
  **Shared layer done** (`com.metallum.render.shared`, eleven classes, public by design, verified on both
  scenes). **Prerequisite ①'s second piece is a finding, not an extraction: the pipeline identity does not exist.**
  `MetalDevice`'s pipeline cache is an `IdentityHashMap<RenderPipeline, MetalCompiledRenderPipeline>` keyed
  by the **game's own pipeline object**, and the argument-buffer state is a
  `HashMap<ArgumentBufferLayout, MTLBuffer>` keyed by a Metal 3 layout inside `MetalRenderPass`. So the
  migration table's "shared logical pipeline + generation-specific compile artifacts" has to *introduce* a
  `MetalPipelineKey` rather than move one - which makes that piece a design step with its own evidence (the
  key must be what two generations can both look up: pack program, stage, defines, profile, and the layout
  mode), not a rename. `MetalResourceBinding` is out (previous commit); the remaining neutral part of
  `ArgumentBufferLayout` (`stageMask`, `descriptorSet`, `bufferIndex`, `encodedLength`) can follow once the
  key exists, with `MTLArgumentEncoder` staying on the generation side.
**The cache-switch question is answered, and the answer changes the step: the key is not yet sufficient.**
  Read from the code rather than reasoned about: the artifact a compile produces also depends on **five**
  things `MetalPipelineKey` does not name - the depth and stencil state (`info.getDepthStencilState()`), the
  polygon mode (`getPolygonMode()`), culling (`isCull()`), the primitive topology (`getPrimitiveTopology()`)
  and the vertex format bindings (`getVertexFormatBindings()`) - each read by `MetalCompiledRenderPipeline`
  while it builds, with the colour-target formats arriving through the depth state. So two pipelines that
  differ only in depth state would collide under the current key and one would be handed the other's
  artifact. The hypothesis that the game's `sortKey` marks a rebuild episode was **checked and is false**
  (`sortKeySeed` is randomised only under `DEBUG_SHUFFLE_UI_RENDERING_ORDER`, and `sortKey` is a fixed
  per-object field for draw ordering). **So the switch needs those five fields added first**, after which the
  cache may move to the key; until then the key is a diagnostic and the cross-generation marker, and the
  cache stays on object identity.
**Prerequisite ① is done, and the cache switch it was waiting for is the step the measurement says not
  to take.** The key now names the five things it was missing - the depth and stencil state (which carries
  the colour-target formats), the polygon mode, culling, the primitive topology and the vertex format
  bindings - composed into one `renderingState` description rather than five fields, so a further piece of
  rendering state cannot be left out silently the way a fields-based key forgets one (metallum `085e0b2`).
  Then the switch was measured before it was made: two counters ride the frame probe, distinct
  `RenderPipeline` **identities** and distinct `MetalPipelineKey`s the device was asked for from process
  start (pipelines compile during startup, so a census armed with the marker would report nothing). On the
  settled pack scene they came back **345 and 345** - equal. Equal means a keyed cache and the identity
  cache hold the same entries, so the move buys **zero** pipeline compilations while costing the eviction
  contract (`evictCachedPipelines` takes a `Predicate<RenderPipeline>` and returns `List<RenderPipeline>`)
  and a hoist of the argument-buffer decision out of `MetalCrossShaderCompiler.compile`, where it is derived
  from the layout entries. **So the cache stays on object identity, deliberately**, and the key stays what
  it already was: the diagnostic and the cross-generation marker.
  The census is kept rather than thrown away after one reading, because it is the only thing that would say
  if a future path - Metal 4 argument tables, a pack loader - starts handing the device freshly built equal
  pipelines; it costs one identity-set insertion per pipeline request and hashes the key only for a new
  identity (metallum `01f10a1`). The run that produced 345/345 read **7.27 ms** with `gpuM3Ms=4368.15`,
  inside the configuration's settled band, so the instrument did not move the number it measures. The
  behaviour question ("may two semantically equal pipeline objects share one artifact?") is moot for the
  cache and stays open for the Metal 4 path, where argument tables are per-pipeline objects.
**Prerequisite ②'s remaining cost is counted by a contract, and the count says design from debt.** The
  architecture guard (`metallum/tools/ci-architecture.py`) carries a ledger of every file outside the
  generation packages that still names the frame path's concrete generation, requiring ledger and tree to
  agree in **both** directions: a new coupling fails, and finishing one without deleting its line fails too.
  It also holds a second ledger for the couplings that must stay the direction they are, and checks that
  direction (the neutral class has to be called from inside a generation package). Current reading:
  `the frame path's isolation still owes 17 couplings in 6 files, and 1 the other way` - the one the other
  way is `MTLBuiltinPipelines`, the neutral home of the built-in pipelines, which the Metal 3 wrappers call
  **into**; counting it as debt argued for pulling encode bodies into the wrappers, which is the opposite of
  the split, so it is listed separately. `MTLStorageTexturePipelines` stays in debt because its caller is
  still `render`; it becomes a delegation the day the encoder moves.
**What has been removed so far, in the order it was removed.** (1) `MetalTransientMemory` took the encoder
  only to retire its rotated blocks, so it now takes the encoder's own `MetalDestructionQueue` - same
  instance, so the semantics hold by construction - and the shared layer stops naming the frame path's class
  (7.31 ms against the same session's 7.31 ms baseline, every counter equal). (2)
  `mtl/MTLDevice.newCommandQueue()` was dead code: the services build the queue from the device handle, so the
  method, its `Msg` and its import simply went - a generation name carried by dead code is the cheapest line
  in the ledger. (3) **The bridge step split in two, and the code decided which way.**
  `MetalAttachmentBridge` and `MetalScaleBridge` only *ask* the encoder things, so those questions became
  `render/shared/MetalFrameExtras` (four methods on shared and game types), implemented by the encoder and
  dispatched on by both bridges. `MetalComputeBridge` and `MetalDepthMipmapBridge` **cannot** be abstracted:
  they encode, driving `MTLComputeCommandEncoder`/`MTLRenderCommandEncoder` directly, so an interface able to
  express them would hand a generation's encoder out of the neutral layer. They move to `render.metal3` with
  the encoder and keep `instanceof` as their seam - this corrects the plan in this file, which had all four
  bridges going behind one interface. (4) **The sodium draw path followed the same recipe**:
  `MetalDrawContext` named `MetalRenderPass` for two members (transient memory and a uniform binding), so
  those became `render/shared/MetalPassUniformWriter`; a backend that is not one now gets an
  `IllegalArgumentException` naming its class instead of an unchecked cast, and `allocateTransient` became
  `public` because the interface needs it - opened **by contract**, which is what the three failed moves did
  by hand. (5) **The surface asks a presentation contract too**: `render/shared/MetalFramePresentation` (the
  take and the submit), kept separate from `MetalFrameExtras` because scaling and presenting are different
  capabilities. **The seam it does not yet decide**: whether the frame presents through Metal 3 or Metal 4 is
  still asked *inside* the Metal 3 encoder (`Metal4Path.presenting(...)`), so the present-only Metal 4 path
  is reachable only by the Metal 3 path asking for it.
**That next step is two decisions, not one** - established by reading the condition rather than by moving
  it. `Metal4Path.presenting(...)` conjoins **policy** (today the system property `metallum.metal4Present`,
  standing in for a question the selector should answer) with **readiness** (`carrying`, queue, command
  buffer, frame event and its value, argument table, four consulted selectors, non-nil layer and picture).
  Policy goes to `MetalExecutionServices`; readiness stays with the Metal 4 objects. The step therefore
  **does not shrink the ledger** - the encoder keeps naming the present path to ask whether it is ready - and
  it moves a **safety interlock**, so it needs a two-arm session on the settled scene (property on and off
  against the same baseline) plus the picture check, not a single run. That pair is why it was not started at
  the end of a session; it is the next thing to do. **Done, and the pair answered two things.** The policy now
  lives in `MetalExecutionServices.presentsThroughMetal4()` (the property parsed there and nowhere else) and
  the encoder asks policy then readiness, short-circuiting so a session that does not want the road never
  records a layer for it; it deliberately does not consult `selected()` yet. The two-arm run: `plain` wallP50
  7.20 ms, `gpuM3Ms=4355.64`, 0 presents; `m4present` wallP50 7.27 ms, `gpuM3Ms=4359.57`, **600 presents, 600
  frames, `gpuM4Ms=28.97`**, +0.1 per cent wall. **The picture comparison does not reproduce the standing
  claim**: mean channel difference 3.65, 88.84 per cent of pixels differing at all, 9.24 per cent by more than
  8. That is not the 0.14-0.41 per cent one configuration repeats to, and the arms' counters differ by their
  own present encoding (600 fewer viewports, 4200 more buffers in the m4 arm), so the frames differ too. The
  Metal 4 present is verified as **running**, not as **equivalent**, and the old claim is now contradicted
  rather than merely unverified. **The difference is now classified**, by re-reading the two screenshots with
  the compare tool's own PNG reader: **not a flip and not a shift** (mean channel difference - identity
  2.447 against vertical flip 50.910 and best row shift 4.472, so identity is the minimum by a wide margin),
  and **concentrated at edges** - flat pixels mean max-channel difference 1.652, edge pixels 8.027, worst 174.
  A global tone or gamma shift would offset everything equally; an edge-weighted error five times the flat one
  is the signature of **filtering**, which fits the two roads: the Metal 4 present *draws* the picture through
  a pipeline with `presentSampler(scaling)` while Metal 3 *blits* it. The actionable item is therefore a
  present-path one at the time of writing. **Reading the two present implementations then moved the suspect
  off the present entirely**: both use the same `presentPipeline` from the same `PRESENT_MSL`, the same
  three-vertex triangle and the same sampler rule read from the same size predicate (`requiresScaling` in
  Metal 3, `scaling` in Metal 4), differing only in direct binding versus argument table - so "the roads filter
  differently" is unavailable, and the edge-weighted split (1.652 flat against 8.027 edge) fits **MetalFX's
  temporal work on a frame that is now synchronised differently** (the m4 arm's present waits for the drawable
  and the frame's commit signals the event that wait is on; it also sets 600 fewer viewports and binds 4200
  more buffers). **The separating test is a same-road repeat - `m4present` against `m4present` in one session -
  and it has not been run.** Tight repeat means the difference is road-specific and the frame's synchronisation
  is the next thing to vary; loose repeat means the road is less stable frame to frame. The claim stays
  "running" until then, but **the present is no longer the suspect**. **The repeat was run and it settles the
  question the other way round**: same-road (`m4a` against `m4b`, both `-Dmetallum.metal4Present=true`, one
  session, 600 frames each) gives wallP50 7.24 ms both times, `gpuM4Ms` 28.82 against 29.49, and a picture
  difference of **mean 4.10, 90.95 per cent of pixels differing at all, 11.35 per cent by more than 8** -
  **larger than the 3.65 / 88.84 per cent the two roads differed by**. So the cross-road difference is not the
  road's: it is inside this scene's own run-to-run picture noise, and that noise is the size of the effect
  being measured. Frame time repeats tightly; **the picture does not**. Consequence for this file and for the
  docs: no picture-equivalence claim about the Metal 4 road is supportable by a single screenshot pair in
  either direction, and a valid measurement needs the temporal state pinned (scaler off for the comparison, a
  fixed frame index, or several shots per arm compared by median). **This is the "这个测试方式不对" lesson again,
  this time about pictures rather than about hand-run clients.** **The fix is measured too**: the same
  two-run comparison **without a pack** gives a mean channel difference of **0.05** (1.61 per cent of pixels
  differ at all, 0.09 per cent by more than 8) against the pack scene's 4.10 - so the pack's temporal upscaler
  is the noise, the no-pack scene is where picture equivalence can be measured, and that is the scene a
  frame-path change is judged in first anyway. **The same run found a second thing**: its two arms reported
  `selectedGeneration=metal3` and `selectedGeneration=metal4` - two identical runs, one session, same build,
  different selections. **The capability verdict is not deterministic run to run**, which breaks the one rule
  this migration leans on hardest (AUTO from capability, never a chip name) and is therefore **ahead of M4**,
  whose behaviour depends on that answer. Both live in `render.execution` and the log already prints the
  capability line, so it is small to chase. **The cause is now located.** The two arms' capability lines
  differ in **exactly two fields** - arm `a` `argumentTable=false render=false`, arm `b`
  `argumentTable=true render=true`, everything else equal - and in `MetalDeviceCapabilities` those two are the
  only fields read through **`Metal4.canBindAndDraw()`**: `argumentTable` is
  `respondsTo(device, "newArgumentTableWithDescriptor:error:") || respondsTo(..., ":")` **AND** that functional
  probe, and `render` is `renderEncoder && canBindAndDraw()`. A selector question cannot flip between
  processes, so the flipping part is the probe that actually makes a table, binds through it and draws: **its
  first answer in a session is a false negative**, and AUTO currently reads it as "this device cannot". The
  fix is two small things - do not turn a transient failure into a capability verdict (retry or warm up before
  the verdict is read), and make the failure say what failed so a false negative is distinguishable from an
  absence. It stays ahead of M4, whose behaviour depends on this answer.
  **And it did not reproduce**: three identical no-pack arms in a later session all reported
  `selectedGeneration=metal4` with identical capability lines and all three probes saying they drew what they
  were told to. So the flip is **observed once, not reproduced on demand**, and the leading explanation is a
  **cold** first attempt (the flipping session was the first to touch Metal 4 argument tables in a while; the
  session that did not ran minutes after runs that had each presented 600 frames through them). The test needs
  a genuinely cold state - reboot, long idle, or the first run of the day - and **the `lastFailure()` plumbing
  has not fired yet**, so its usefulness is unproven rather than proven. It stays ahead of M4. **The
  equivalence question in the sharp scene is answered, though**: no-pack, camera pinned, 600 frames each,
  `plain` against `-Dmetallum.metal4Present=true` gives a picture difference of **mean 0.04, 0.08 per cent of
  pixels differing at all** - the same number two runs of one configuration show in that scene (0.04-0.05) - so
  **the Metal 4 present is equivalent to the Metal 3 road**, verified where the test is sharp, with `wallP50`
  1.75 ms in both arms and counters differing only by the presentation itself (600 fewer viewport sets, the
  present's GPU time accounted on the Metal 4 queue: `gpuM4Ms` 47.03). The pack scene stays unresolvable, and
  that is now a statement about the test bed rather than about the road. **The pack scene was checked with a
  control at 100 per cent too** (scaler out of the picture, both roads on the nearest sampler): cross-road
  `plain` against `m4present` **2.06**, but two runs of `plain` alone **36.37**. So at both scales measured the
  road-against-road difference is smaller than the scene against itself (3.65 vs 4.10 at 55 per cent, 2.06 vs
  36.37 at 100 per cent): **the pack's own temporal history is the noise, no picture claim is supportable in a
  pack scene, and the no-pack scene (0.04-0.05 same-config) is the only bed for this test.** Frame time does
  not follow the picture - that 100 per cent pair read `wallP50` 10.75 and 10.73 ms while the pictures differed
  by 36.
**The services now carry the selection rather than a constant.** `MetalDevice` built them for
  `MetalApiGeneration.METAL3` *before* the selector ran and took the selection a few lines later - harmless
  while nothing read `selected()`, but the one object every seam asks disagreed with the selection the same
  constructor logged, and an AUTO-Metal-4 launch would have looked identical to a forced Metal 3 one. The
  contract that pinned the seam pinned the constant and failed the moment it went, which is the pin working;
  it now pins the selection. No-pack run after it: 1.79 ms, `selectedGeneration=metal4`, counters unchanged.
  **And the seam's answer is now printed**, because a value nothing prints cannot be checked: two arms in one
  session read `servicesSelected=metal4 servicesExecuting=metal3 referenceShell=true` (AUTO) and
  `servicesSelected=metal3 ... referenceShell=false` (forced `-Dmetallum.execution=metal3`), both executing
  Metal 3 at 1.78 ms wallP50 with a picture difference of 0.03. The AUTO line is the evidence: before the fix
  that arm's services would have answered `metal3`.
  **Done, and it took three contract failures with it.** `executing()` is a parameter of the services now,
  `isReferenceShell()` asks whether the executing generation is the selected one (it used to ask whether the
  selection was Metal 3 - the right answer for the wrong reason), and the device asks `framePathReady()`, which
  reads `false` with `servicesSelected=metal4 servicesExecuting=metal3` on this machine and is printed. The
  device passes METAL3 for what executes with the reason beside it; **that the caller passes the generation
  which really executes is not something a contract can prove** - only M4's own frame path can, which is what
  `framePathReady()` is for. Three pins fired while writing it (the seam's log format, the queue seam's factory
  call, the `executing()` literal), each naming the line that moved; the third had pinned an implementation
  detail as if it were the design and now pins the property it was about.
  **The next pack run reproduced the flip and exposed a trap in that seam.** The capability probe answered
  `argumentTable=false render=false` again - a second independent observation, different session - and with the
  selection degraded to Metal 3 the seam read `servicesSelected=metal3 servicesExecuting=metal3
  referenceShell=false framePathReady=true`. **`framePathReady()` is true while the frame is drawn by Metal 3**,
  because the selected generation is the one executing: the predicate is a self-consistency check, not "the
  Metal 4 path is available". **M4 must not gate on it to decide whether the new path can be used** - that
  question is the capability record's. Frame time was unmoved either way (`wallP50` 7.24 ms, `gpuM3Ms=4368.17`,
  inside the session's 7.24-7.31 band), because both selections execute Metal 3.
  **Tally after eight more warm arms: two flips in fourteen arms, and both were a session's first arm.** The
  instrument added for this has not fired. The pattern is a tendency, not a rule (other first-arm runs did not
  flip), but it is the first thing about the flip that is not "sometimes": the leading explanation is **the
  first Metal 4 argument-table attempt in a session**, which matches the original observation too. **The next
  attempt wants a cheaper trigger than a reboot: a fresh process against an idle GPU, as the first arm, several
  times** - eight warm arms cost six minutes and answered nothing.
  **The cheap trigger's in-process half exists, and its first reading argues against "the probe is flaky"**:
  `-Dmetallum.probeRepeat=N` runs the probe N more times in the same process (the one thing the client never
  does, since the answer is cached where the capability record reads it) and logs each answer with stage and
  reason. One arm at N=25 gave **26 calls in one process, all true, no stage** - so a false negative is not
  something the probe does every few calls. Cold first use therefore stands, untested: that process ran warm,
  so its first call was warm too. The next experiment must vary what a warm run cannot - a fresh process
  against an idle GPU, reading the first position - and the cross-process harness (create a device and exit,
  without Minecraft) is still not built.
  **Registered and moved off the main line**: `Blocks: Metal4 AUTO production enable`;
  `Does not block: M1 / M2 / M3 / Metal4 implementation work` (nothing gates on `selected()`; the frame
  executes Metal 3 with `executing` passed explicitly; the forced switches pin the selection meanwhile).
  **Wording corrected**: 26/26 true proves only that **no in-process high-frequency or deterministic repeat
  failure was observed** - it does **not** prove that a failure must come from cold/first use. Low-frequency
  race, object lifetime and driver state stay open. Remaining work is only: (1) build the cross-process harness
  (`new process → create MTLDevice → probe once → report → exit`) before Metal 4 is AUTO by default; (2) count
  cold-first against warm/repeated separately; (3) if it fails, read the existing stage/reason; (4) no root-cause
  claim about argument-table first use without that evidence; (5) `-Dmetallum.probeRepeat` is a development
  diagnostic switch and stays out of the production hot path.
  **The probe is staged now, and the cheap trigger is the missing piece.** Every exit names a stage and a
  reason (nineteen call sites: selectors, objects, uniform, table, vertex, target, pipelines, pass, attachment,
  encoder, commit, completion, pixel, exception), `lastFailureStage()` is printed by the capability record, and
  the two exits that had no reason at all - the selector pre-check and a nil `newTarget` result - now do.
  **But 600-frame arms are the wrong instrument**: ~70 s each, and two flips in fourteen means no experiment can
  be run often enough to catch one on demand. The next step is a harness that does `create device → probe once →
  report → exit`, so `first probe in a process` can be compared with `later probes in the same process` dozens
  of times. **2/14 remains a hypothesis about cold first use, not a root cause**, and nothing should be written
  as though it were until a run is captured with its stage named.
  **And the readiness seam nothing asks**: `framePathReady()` and `isReferenceShell()` have exactly one reader
  in the whole source tree - the log line just added. `framePathReady()` is `!isReferenceShell() && selected()
  == executing()`, `executing()` is a constant `METAL3`, and AUTO's `selected()` is `metal4`, so readiness is
  structurally false and nobody notices because nobody asks. **The first M4 change is therefore not a Metal 4
  command buffer but making `executing()` a property of the session rather than a literal** - the same fix
  `selected()` just needed - so that a forced Metal 3 launch and an AUTO launch that chose Metal 4 stop being
  indistinguishable where it decides which frame path runs.
**Prerequisite ①'s last piece (`MetalPipelineKey`) is specified down to the lines it touches, and not
  started.** Where the identity material is: `MetalDevice.getOrCompilePipeline(RenderPipeline)` (line ~408)
  is the only place a compiled pipeline is made - `this.compiledPipelines.computeIfAbsent(pipeline, p ->
  MetalCrossShaderCompiler.compile(this, p, this.defaultShaderSource))` - and the object it makes takes
  `MetalDevice, RenderPipeline info, vertexMsl, fragmentMsl, vertexEntryPoint, fragmentEntryPoint,
  List<MetalResourceBinding>, usesArgumentBuffers, vertexArgumentBufferSets, fragmentArgumentBufferSets`
  (`MetalCompiledRenderPipeline` line 81). So the key can be built at exactly that one place and stored on
  the compiled object first, with the cache left on identity - the strangler order, and the only order that
  does not change caching semantics in the same commit that introduces the key. Open questions that need the
  game's own `RenderPipeline` read rather than assumed (its shader-location and defines accessors), and the
  one semantic question: whether two distinct `RenderPipeline` objects that are semantically equal may share
  one compiled pipeline - which is a behaviour decision, not a refactor. Verification recipe, now executable:
  forced Metal 3 and AUTO, pack scene at the settled default (`--settle 25`, camera pinned), requiring the
  cache to be cold on the first run of each arm (`compiles`/`pipeline` counters) and the frame time inside
  the configuration's settled range (7.25-7.28 ms on this machine today).
**Prerequisite ①'s second piece is done**: `MetalArgumentBufferLayout` (where an argument buffer sits, how
  big it is, which stages and descriptor set) is in `render.shared`, with the Metal 3 encoder composed beside
  it in the pipeline record - verified on the pack scene at the settled default (7.247 against 7.277 ms, 0.41
  per cent). **Remaining in ①: the pipeline key/identity, which has to be *designed* (the cache is keyed by
  the game's pipeline object today); in ②: the encoder also coming from `MetalExecutionServices`, and
  `MetalDevice` losing the concrete generation. Then the frame path's facade move.**
**Prerequisite ②'s first piece is done**: the frame's queue comes from `MetalExecutionServices` (its first
  consumer), the handle crossing as an address, with a contract refusing `this.metalDevice.newCommandQueue()`
  again. **Next: the rest of ② - `MetalDevice`'s facade losing the concrete generation and the *encoder* also
  coming from the services; then prerequisite ①'s `MetalPipelineKey` (which needs designing, see below);
  then the frame path's own facade move.**
**Prerequisite ① has its first piece**: `MetalResourceBinding` (the neutral record describing one binding
  a compiled pipeline declares) is in `com.metallum.render.shared`, with the generation-specific remainder -
  the argument-buffer encoder, the pipeline states, cull/fill/topology - still in the Metal 3 pipeline object.
  Both scenes unchanged. **Next pieces of ①: the pipeline's *key/identity* and the argument-buffer layout's
  neutral part; then ② `MetalDevice`'s facade with the queue coming from `MetalExecutionServices`; then the
  frame path.**
**The facade was started and stopped on a third measurement: the 17-method surface is not the problem.**
  Renaming the implementation to `Metal3CommandEncoder`, moving `MetalRenderPass` beside it and letting the
  compiler name what crosses produced **144 errors**, and their *content* is the finding: not the encoder's
  own API but its coupling into **`MetalDevice`'s pipeline cache** (`getOrCompilePipeline`,
  `metalDeviceHandle`, `useLabels`), **`MetalCompiledRenderPipeline`** (itself package-private and, per the
  migration table, still needing its shared-logical / generation-artifact split) and the bridges. So the
  order is the migration table's, read literally: **split `MetalCompiledRenderPipeline` and give
  `MetalDevice` its facade plus the execution services first**, and only then can the frame path move
  without the engine's internals being opened to satisfy a move. All three attempts are recorded with their
  numbers (100, 118, 144) so none is repeated; the tree is green at the fence milestone.
**The facade's shape is now measured, not guessed** (from the game's own interface and the actual call
  sites, so the next session does not re-derive it): `CommandEncoderBackend` declares **17 methods** -
  `submit`, `transientMemory`, `createRenderPass`, `submitRenderPass`, `clearColorTexture`,
  `clearColorAndDepthTextures` (two overloads), `clearDepthTexture`, `writeToBuffer`, `copyToBuffer`,
  `writeToTexture`, `copyBufferToTexture`, `copyTextureToBuffer` (two overloads), `copyTextureToTexture`,
  `createFence`, `writeTimestamp` - and the classes that stay outside it call the encoder for
  `queueForDestroy`, `flushPendingClear`, `renderCommandEncoder`, `endEncoder`, `encodedLength`,
  `commandBuffer`, `waitForSubmittedGpuWork`, `awaitSubmitCompletion`, `setNextPassReadsStorageImage`,
  `transientMemory`. So the work is: rename the implementation to `Metal3CommandEncoder` in
  `render.metal3` (beside `MetalFence`, and with `MetalRenderPass` moving in the same step because the two
  are coupled through package-private state), write the 17 delegations plus those extras as the facade in
  `render`, and open exactly the members that list names - which is the seam, as opposed to the 118 a plain
  move wanted. Verified: the interface's method list was read from the game's own `CommandEncoderBackend`
  class, not from memory.
**Started the facade route instead**: `com.metallum.render.metal3` now exists with `MetalFence` (two
  members opened, vs 118 for a move), which is the `Metal3Synchronization` seat. **Next: the facade for
  `MetalCommandEncoder`** - define its outward shape (it already implements `CommandEncoderBackend`), rename
  the implementation to `Metal3CommandEncoder` in the same package as the fence, and delegate method by
  method, starting with one method and a real run before the rest.
**The frame-path move was tried again, now that the shared layer exists, and it produced 118 visibility
  crossings** (`MetalCommandEncoder` 的构造器/`close`/`renderCommandEncoder`/`presentTextureToDrawable`/
  `flushPendingClear`、`MetalDevice.getOrCompilePipeline`/`metalDeviceHandle`、`MetalCompiledRenderPipeline`
  本身，等等)。结论比上次更清楚：**帧路径不是"移动"能隔离的，必须先把 facade 写出来**——把
  `MetalCommandEncoder` 变成对外 facade、把实现抽成 `Metal3CommandEncoder` 并逐方法委托，否则隔离的代价
  是把引擎内部 API 大面积放开。两次尝试都已回退，工作树在已验证的提交上。
  **Next for M3: `com.metallum.render.metal3`** - the frame path (`MetalCommandEncoder`,
  `MetalRenderPass`, `MetalFence`) now has a shared layer to import, so the move no longer needs the API
  widened ad hoc; then `MetalDevice` stays a facade and loses the concrete generation, with the queue coming
  from `MetalExecutionServices`. After that, M4 (the Metal 4 frame submission shell).
- [ ] **M2 of the dual-execution plan: shader language profiles (done, not yet merged).**
  `MetalShaderLanguageProfile` pairs the SPIRV-Cross MSL version with `MTLLanguageVersion`; the Metal 3
  ladder is probed by compiling (3.2 -> 3.1 -> 3.0) and the Metal 4 pairing is 4.0; render and compute both
  read it; `MTLCompileOptions.languageVersion` is set explicitly; the function cache identity names the
  profile. The profile follows what executes, so Metal 3 sessions emit `msl3.2` today.
  **Next: M3 (the pure refactor that isolates the existing implementation as Metal 3 into its own packages,
  moved and not rewritten).**
- [ ] **M1 of the dual-execution plan: runtime selector + capability record (done, not yet merged).**
  `render/execution/` holds `MetalApiGeneration`, `MetalExecutionPreference` (`-Dmetallum.execution`),
  `MetalSystemProfile`, `MetalDeviceCapabilities`, `MetalExecutionSelector`, `MetalExecutionServices`,
  `MetalShaderLanguageProfile`. AUTO prefers Metal 4 on this device and forced preferences fail loudly; the
  executing generation is still Metal 3 and the services say so. **Next: M2 (shader language profiles: the
  MSL target must be chosen with the generation instead of the single hardcoded profile the capabilities
  record now reports).**
- [ ] **M0 of the dual-execution plan: architecture guard + measurement readiness (done, not yet merged).**
  The guard is `metallum/tools/ci-architecture.py` (shared/metal3/metal4 import rules, plus "one file never
  names both command generations", mutation-proven) and Vitrail's
  `tests/test_backend_neutrality_contract.py` (no command-generation type name in `common/`, mutation-proven);
  both are named by their CI. The generation is said once (`Metal execution: metal3 selected (...)`), the
  probe line carries `selectedGeneration` and splits GPU time into `gpuM3Ms`/`gpuM4Ms`/`gpuMs` - the last of
  which used to be the Metal 3 road alone and made every Metal 4 submission invisible. **Next: M1 (the
  runtime selector: `MetalDeviceCapabilities`, `MetalExecutionSelector`, AUTO/FORCE_M3/FORCE_M4, MetalFX
  parity, package skeleton), which M0 deliberately did not start.**

- [ ] P5 (item 3) - **The Metal 4 path, and the first bounded step of it.** Detection and advertisement are
  done (ask the device, cache the negative, `MetallumApi.supportsMetal4CoreApi()`); the path itself is
  unwritten. Two things it must not be: a migration (item 1 keeps the `MTLCommandQueue` path as the
  fallback, the opt-in is a runtime choice) and a rewrite (item 2 - a Metal 4 type name reaching `common/`
  breaks the seam the same way a native handle does, and `AGENTS.md` already forbids that). What it has to
  express first is **"the same attachment set, and then a different one"**, with the backend free to decide
  whether that is a new encoder - because the platform's own attachment map is the answer to the
  encoder-merging question this plan started from, and the boundary count is where the phase's exit
  criterion lives. Size it against the measurement recorded in `docs/performance.md`: the engine's own
  steady overhead is **one boundary a frame** against the pack chain's twenty-nine, so what this path can
  move is encoder switching and not the chain's shape. Item 6 says what the ordering is built from: under
  Metal 4 every resource is untracked and ordering is explicit, so P1's per-pass reads and writes
  (`AttachmentContents`, `setNextPassContents`) stop being documentation and become barriers. Exit: both
  paths run one session with the same images, the new path shows fewer boundaries or lower store traffic on
  the P0 numbers, the fallback is exercised deliberately at least once, and no Metal 4 type appears
  anywhere under `common/`.

  - **The skeleton is done, submission included: the objects are made and one is committed on device** (`MTL4Probe`: one queue, one
    allocator and a command buffer carrying a 64x64 colour-target render pass, encoded, committed and released at device creation, nothing in a frame path; the queue signals a shared event after the committed work and the event's
    CPU wait is what proves the GPU ran it). What it taught is a rule for the rest of the path: **a device implements a
    subset of the factory surface its header declares** - `newCommandAllocatorWithDescriptor:` is
    declared in this machine's SDK and is not implemented by the device, and sending it is an
    Objective-C exception that ends the process - so every selector is asked for with
    `respondsToSelector:` first, and `preferredGraphicsBackend` in the instance's `options.txt`
    has to be checked after any startup crash, because Vitrail puts it back to Vulkan by design
    and the next run then measures MoltenVK.
  - **What remains is the path itself.** Reconnaissance done, so the first edit is named rather than guessed.** The engine has no way today to
    say "the same attachment set, and then a different one": on the seam a pass boundary *is*
    `CommandEncoder.createRenderPass(RenderPassDescriptor)`, and every caller that asks for one gets a new
    encoder. The call sites that would express the distinction are the mixins hooking that method -
    `mixin/sodium/MixinDefaultChunkRenderer.java:130`, `mixin/CloudRendererMixin.java:76`,
    `mixin/QuadParticleFeatureRendererMixin.java:64`, `mixin/WeatherEffectRendererMixin.java:120` - and the
    capability surface they would carry it on is `mixin/metallum/MetalCommandEncoderMixin.java:31` (beside
    `ComputeCommands`, `AttachmentCommands`, `ScaleCommands`). On the backend the decision lives in three
    places: `MetalCommandEncoder.renderCommandEncoder` (`:358`) opens an encoder, `submitRenderPass`
    (`:555`) closes one, and `invalidateEncoderState` (`:287`) is the existing "this encoder may no longer
    be reused" signal. **The first bounded step is therefore a semantic seat** - one capability that says
    "the attachment set is the one already open; only its contents or state differ" - published by Vitrail
    and answered by the backend, with the backend free to keep using one encoder or to end it. No Metal 4
    type crosses: the capability carries the attachment description and the contents facts the engine
    already computes (`AttachmentContents`, `setNextPassContents`), which is what item 2 and `AGENTS.md`
    require, and it is the same rule P1's capabilities were built under.

The roadmap is a page rather than this list: it records what is already implemented so it is not built twice, what is actually absent, the phases, and their exit criteria. What this list owns is the intent and the order.

- [ ] P0 - Instrument. Three counters behind a marker: render encoders per frame, bytes stored and loaded per attachment, bindings per frame split by kind. A phase whose number was never captured does not proceed.
- [ ] P1 - Publish attachment lifetime from Vitrail and consume it for load/store actions. Store is unconditionally `STORE` today (`mtl/MTLCommandBuffer.java:128-148`), which is the largest avoidable cost on a tile-based GPU. Exit criterion is the screenshot comparison, not the counter: a wrong `dontCare` is a wrong image.
- [ ] P2 - Binding dedup by value rather than by name (`markDescriptorDirty` marks unconditionally), and confirm every uniform path writes into the transient allocator.
- [ ] P3 - Persistent pipeline cache for the measured 2.5 to 3.7 s pack-switch stall and the 155 leftover pipelines per load. Metal 4's unspecialised-then-specialised pipeline states are the better long-term form, so build the key so it can be superseded rather than in competition with it.
- [ ] P4 - Reachability-driven dead resource elimination over the SPIR-V the engine already produces. The current basis is declaration text and it over-counts, which the repository has already recorded on Photon.
- [ ] P5 - Remove the Vulkan path, last, in reviewable batches. The Metal smoke switch and the `HostReport` production-path stance are already gone, so what remains is: the rest of the page's Vulkan guidance (which still sends a non-Metal session to a graphics API this removes), the Vulkan-only mixins and their accessors, the contract test `tests/test_vulkan_recording_contract.py` with its `build.yml` line in one commit, and the prose last. The seam stays: one backend, not no boundary.
- [ ] MetalFX is a decision before it is code: where it sits relative to a pack's `final`, what happens to the existing `vitrail_scale_upscale_*` modules, and whether the resolution is pack-visible at all. Default off.

## P0 - Measure before optimising (baseline taken)

- [x] P0 - The frame probe exists and is armed by `-Dmetallum.probeFrames=true` or `metallum/probe-frames`, and one real 600-frame run is recorded in `docs/performance.md`. Four counters: encoder boundaries with reason, attachment bytes loaded and stored, bindings by kind, pipeline creations with cost.
  - The marker used to be asked once, before the first frame, so a window could only cover frames a launch reached by itself, and the second supplied log spent its whole budget on frames with no pack applied. `metallum#5` asks it again while the probe is off, at most once a second, and opens a window on the marker's return rather than on its presence, so a marker left in place still arms one window and no more. `tools/ci-frame-probe.py` on that side pins the shape, and three mutations check the new assertions can fail rather than merely pass.
- [x] P0 - **The instrument reads the GPU's own time, and the frame's shape is measured: 20 of 26.80 ms scale with pixels.** `gpuMs` over `gpuFrames` comes from the driver (`GPUStartTime`/`GPUEndTime`, read once per completed submit behind the probe's guard), and four window sizes of one scene give `gpu ms a frame = 6.92 + 2.726 x megapixels` (R2 = 0.9993) with wall clock and GPU time never more than 0.3 per cent apart - so the frame is GPU-bound at every size and the pack's 34.9 frames a second is the GPU and nothing else. **This is how P6 is judged**: 70 per cent linear scale is 1.60x, 60 per cent is 1.90x, 50 per cent is 2.25x, before the upscaler's own cost. The 6.92 ms floor is the part no resolution change can touch.
- [x] P4 - Reachability-driven dead resource elimination. **CLOSED, below the measurement floor.** The copy consumer is the whole of what remained (the binding half already runs at bind time), the switch `-Dvitrail.elideTargetCopies` is written and off by default, and the probe's blit counter proves it removes exactly the three copies the engine names - eleven a frame to eight, 6.24 MiB a frame of the 225 stopped. Its frame-time effect is below what a comparison resolves: on the frozen world the two arms still differ by one depth-attaching pass, worth one to two per cent, against a twentieth of a millisecond for the copies. Also recorded: no pass in Photon's frame has all its targets unread, so there is no dead-pass elimination to take on this pack.
- [ ] P4 - (superseded) Reachability-driven dead resource elimination. **Measured, and it has a named target rather than a negative result.** The engine already logs `10 targets are copied back from their far half at the end of every frame ... and 3 of those are read by nothing in the frame, so moving them is work nothing asked for`: three of ten copies move a target nothing reads, about 87 MiB read and 87 MiB written a frame at 1800x1019, of the order of a millisecond of 27.58 ms. Owed: (1) a **blit counter in the probe** - copies and the mebibytes they move - because a copy-back is a blit and no current counter sees one; (2) skip the copy for a target the frame's read set excludes, off by default, and read the difference as `gpuMs`; (3) record the per-pack evidence. The binding half of this phase is already done at bind time (`Samplers bound from what a module reaches`), so it owes no reflection project. Also recorded: no pass in Photon's frame has *all* its targets unread (one line in a whole frame reports any unread target at all, `composite4` at one of two), so there is no dead-pass elimination to take on this pack.
- [x] P0 - **A deterministic fixture exists for the counters, and a comparison repeats to a quarter of a per cent.** `metallum/tools/freeze-world.py` (lossless NBT rewrite of the staged `level.dat`: time pinned to mid-morning, daylight/weather cycles off, mob spawning and patrols and traders off, random ticks and fire spread off, `--still-life` takes the world's entities out, `--spectator` puts the player into spectator mode) is called by the harness before every run. On the spectator fixture two runs of one configuration read the depth and byte counters identical, textures and samplers within 0.23 per cent (where they were five to nine apart before the player left the scene), encoders 0.15 and the frame's own GPU time 1.4 per cent, which is the floor a time claim has to beat. **Closed for the counters.** The rules and the clock had been written into `level.dat`, which this schema does not read: they live in `data/minecraft/game_rules.dat` (namespaced - `minecraft:advance_time`), `world_clocks.dat` and `weather.dat`, and until that was fixed the fixture ran a moving sun in a night world. With all four files written the clock reads 4000 after two runs and the scene is daylight; two runs of one configuration now differ in 2.03 mean levels (worst pixel in the nether portal's box), and on a ground-only view aimed with `--at/--yaw/--pitch` in 0.994 per cent of pixels above 8 levels, scattered over 141 grid cells rather than gathered. **Owed for an image verdict: the shape test written down as a check**, since a pixel-identical scene is not reachable while the pack draws its own dither, and its two causes are now named: a nether portal whose texture and particles animate with the world's age (335431 pixels of a 520x700 box moving by up to 223 levels), and a per-frame noise or dither pattern over a dark scene that no translation or brightness ratio accounts for (equal means, 98 per cent of a terrain window differing). It needs a scene that is bright and still, with no animated block texture, particle or cloud in frame - the companion repository's smoke-fixture style scene, not this world.
- [ ] P0 - (superseded) **A deterministic fixture was the top instrument item.** Two launches of this world do not draw the same frame: with the world restaged before every run (the harness now does that, `--continue-world` to opt out, which removed the sun's drift), the two arms of one A/B still differ by eleven per cent in pipelines and nineteen in depth attachments, so a four per cent frame-time difference could not be attributed to its switch. Chunk loading, entities and weather cannot be pinned by copying a save. Until a scene the harness controls exists, the resolution of a comparison is a few per cent and the picture half of P1 and the copy verdict of P4 both stay unproven; the companion repository's smoke fixtures are the model.
- [~] P6 - MetalFX. **The seat is implemented and running** (65 per cent scale: `brings the picture back with MetalFX`, no upscale pass in the census), with FSR 1.0 and the fold deleted and the bilinear blit as the fallback. **Owed: its cost against the bar.** The recorded bar is the 2.31 ms the FSR 1.0 pair took at 1800x1019, and measuring it needs a paired comparison of one scene with the scaler on and off - the two runs available so far differ in their pass set, so they cannot be subtracted. Then the per-pack picture review under the compatibility policy.
- [x] P0 (instrument) - **The last thing that varies is what is drawn inside one frame's pass structure, and it was the player.** The fixture repeated the pass set and the bytes while texture and sampler counts still differed five to nine per cent between runs of one configuration, which is why the `narrowStorageBoundary` arm read +18 per cent in a session whose arms were not the same frame. The owner chose the first of the two ways out: **spectator game type**, built as `freeze-world.py --spectator` and asked for by the harness on every run. It worked - the counts are a quarter of a per cent apart now - and what is left of the picture difference is not the player but the scene's own animation and noise, above.
- [x] P1 (re-opened in part) - **Re-measured on the spectator fixture, one session, four arms.** The load half removes 40.4 per cent of the frame's loads (503.8 MiB a frame) and the store half 27.99 MiB a frame, the same one-target figure the earlier session measured on a different scene; the depth and copy-back counters are identical in all four arms and each switch moves the one counter it owns. **The time columns read nothing**: +0.1 per cent with both halves elided, -0.8 for the boundary switch alone, +0.7 for both, against a 1.4 per cent floor - so the verdict is "buys nothing measurable" rather than "unmeasurable". The boundary switch merges 14 encoder boundaries in 600 frames. The pictures show no region emptied, which is the exit criterion's failure mode, and the 1.9 per cent of pixels above 8 levels is the portal's animation plus particles. `tests/test_backend_capability_reach.py` is the rule that keeps the seam fault from being re-learned.
- [ ] P6 - MetalFX. **Decided and unblocked** (owner-approved 2026-09-19): engine level after the pack's final pass, off by default, never inside the chain - the seat FSR 1.0 already occupies; one upscaler chosen deliberately, with Temporal Fold on the FSR 1.0 side; and nothing pack-visible changes. Headroom is measured: 1.60x at 70 per cent linear scale, 1.90x at 60, 2.25x at 50, taken at `renderscale=100` so the replaced upscaler's fixed cost comes out of it. Work in order: (1) measure the FSR 1.0 upscale's and sharpen's own `gpuMs` at a few scales, so 'not slower than what it replaced' has a number; (2) implement MetalFX as the second occupant of that seat with the off switch restoring today's path; (3) verify per pack under the compatibility evidence policy.
- [ ] P0 - **The per-pass report is the host clock; the GPU time a frame costs is now read from the driver.** `-Dvitrail.passTimings=N` ranks every pass with the game's own labels, which is the attribution the trace cannot give, but both of its encoders fill the pool with `device.getTimestampNow()`, which is `System.nanoTime()`, so a row is the CPU cost of *encoding* a pass: the table's total is 1.188 ms in a window whose frames take 25.15 ms, and it grows when the window shrinks while the frame gets faster. Owed, with the design known: real counter sample buffers (`sampleCounters(sampleBuffer:sampleIndex:barrier:)` + `resolveCounters`), one sample per pass *boundary* taken on the closing side because there is no encoder to sample on when a pass opens, and a barrier that serialises the frame and therefore belongs behind the existing off-by-default switch. The probe now carries `gpuMs` over `gpuFrames` from `GPUStartTime`/`GPUEndTime`, and one window reads 25.25 ms of GPU time a frame against 25.21 ms of wall-clock - that is the yardstick P4 and P6 are judged on.
- [ ] P0 - **The trace is taken, the first pack window is taken by a harness rather than by hand, and the window now carries what it cost in time** (`metallum/tools/run-vitrail-performance.sh`: it stages the pack with the options file beside it, launches into a world, arms the probe on the pack's own first full frame and collects the window; `windowMs` is read from the window's own first frame, and the comparison prints milliseconds a frame). Still owed: the second (light) pack's numbers, and a picture comparison on a scene that repeats - two launches of one scene do not draw the same frame, because the world's clock runs while a session is loaded, so the pictures differ in lighting and particles rather than in what the switch did. A warm number needs the same build twice, or two loads inside one session: both disk stores name their directory after `Vitrail.cacheEdition()`, which adds the commit for a development build, so a rebuilt jar reads an empty edition and comparing across a rebuild compares two cold starts.
- [x] P1 - Attachment lifetime and precise load/store. **CLOSED by measurement on 2026-09-19, and closed off by default.** Both halves are wired behind `-Dvitrail.elideTargetTraffic`; the load half removes **32.3 per cent of the frame's attachment traffic - 509 MiB a frame - and returns 0.2 per cent of frame time** (28.69 ms against 28.74), the store half removes nothing because twelve of the pack's fifteen colour targets are kept between frames and almost every write is read by the next frame, the depth slot - the only attachment left, at 444 MiB a frame or 13.0 per cent of the traffic - has no lifetime door at all and is worth less than the half that already measured zero, and `-Dvitrail.narrowStorageBoundary` moved no boundary. Apple prices the actions (`load` "is significantly slower" than `dontCare` or `clear`; `storeAction.dontCare` is "[t]ypically the correct action for depth and stencil render targets") and this frame does not pay them: it is bound in the fragment shader, 12.63 of the 14.04 traced seconds. `docs/performance.md` carries the verdict, the unified-memory reading (a private attachment is *system memory* even under unification; only `memoryless` is tile memory), and the two levers Apple documents *above* the action level - `memoryless` storage for a target "used only within a single pass", and tile shaders keeping intermediates in tile memory - neither of which applies to a chain whose targets are read across passes. The mechanism stays because P4's reachability work consumes the same lifetime facts. The switch stays off because a change to what Metal is told owes a picture comparison this phase never finished: two launches of one scene do not draw the same frame. **Reopens only on a frame bound on memory rather than on the fragment shader** - a higher resolution, or a heavier pack - which is the deciding experiment if the default is ever reconsidered.
  - **Both halves are written, behind `-Dvitrail.elideTargetTraffic`, off by default.** A pass that writes every pixel of a target - no `discard`, no sampler naming a target it writes on the same half, and a draw over the whole screen - has a clear handed to the descriptor where the null stood, which is a tile fill instead of the target's bytes read back. A clear the frame already owes is never replaced. The run it needed - same scene with the property on and off, `loadedMiB` down, and `storedMiB`, encoder boundaries and bindings not regressing - was taken, and the default did not flip: the bytes fell and the frame time did not move.
  - **Measured, and the answer is that the bytes go and the milliseconds do not.** `metallum/tools/run-vitrail-performance.sh` took the run the line above asks for: one build, one scene, Photon v1.3b at its owner's options, an 1800x1019 window (3600x2038 drawable), 600 frames a run. `loadedMiB` **935403 to 633137 (-32.3%, 1559 to 1055 MiB a frame)**, `storedMiB` **identical on both sides** (1122529), `encoders` 19712 to 19718, and **`windowMs` 17212.8 against 17245.5 - 28.69 ms a frame against 28.74**. The windows drew the same frame (viewport, scissor, texture, sampler and submit counts within 0.06 per cent), so that is a null result and not noise.
  - **The store half goes through the seam.** The public descriptor says clear-or-load and nothing about a store, so telling the backend that a target's contents are dead when the pass ends is a narrow capability rather than a call that exists today, and the plan's own note says that is the part to get right because P5 depends on it. **Wired** behind the same switch: `AttachmentContents` carries the two booleans across, and the measurement above is the first reading of it.
- [ ] New: **the encoder boundary an untracked storage-image write owes is owed to the reader.** A boundary costs a pass's whole attachment set, loaded and stored again, and it used to be taken after every graphics `imageStore` whether or not anything read what was written. `MetalCommandEncoder` now remembers the write and breaks the encoder where the pass that reads it is built, and both sides default to today's behaviour: the backend answers "may read" for a pass nobody described, and Vitrail states the fact only under `-Dvitrail.narrowStorageBoundary`. Owed: one session with the switch on against one with it off, image for image, comparing the probe's encoder count and both byte totals. **The first such session is taken and the switch moved nothing** (19712 encoders off against 19718 on), which is the expected reading where the frame already opens about one encoder per pass: the pack declares four storage images and reads two, so the mechanism has something to narrow in principle and nothing to narrow in this frame. Recorded rather than removed, because the boundary it removes is a whole attachment set whenever it does apply.
- [ ] New, and measured rather than assumed: **the pack-switch stall is the world being rebuilt**, not compilation. The block-id table moving (23877 to 32194 states in the 02:41 session) makes every section build again, and the client submits no frame for four seconds while it happens, with the GPU idle and the driver's compiler idle throughout. The same switch took one second in the 02:23 session with the same compile figures, so the question is what the rebuild costs and what it depends on. It has no phase number yet and sits behind P1; it is recorded here so that a later reader does not attribute the switch to P3, which owns the load rather than the switch.
- [ ] P3 - Re-aimed at the module store, and the third supplied log has answered what the re-aim asked. A warm load compiles no module at all: 1317 served and **0 built** across four loads, at 32 to 100 ms of `making modules`, with the translation store serving every program it was asked for. A cold new edition built 124 over 328 modules for 3524 ms by comparison. So the seconds are the modules a new edition has to compile and nothing else in either store misses; the reading recorded here that a reopen built 73 of 324 was a misattributed interval, and `docs/performance.md` now carries the correction. What is left to decide is whether that is worth changing at all - a developer pays it once per rebuild and a player only on a version change - so the phase's value is measured now rather than assumed, and P1 keeps its place ahead of it.
- [ ] P4 - Narrowed. Sampler reachability already runs at bind time (194 dropped in one load), so what is left is the part of the plan still reading declaration text, `TargetCopies` first.

## P2 - Keep acceptance and documentation synchronized

- [x] Both requests are merged and a version is out. Companion `metallum#1` landed on `master` first, then this repository's `#1`, and `v0.12.0-metal-beta` was released from `main` at `55d6d6a8` with the merged jar attached; `dev` has opened `0.13.0-dev`. Nothing was promoted by it: PHASE 17's closure recorded no status for any row and the release changes none.
  - The release needed one rule change ahead of it, because `release/*` carries the version bump and nothing else: `ci: accept a named pre-release in a released version` widened the shape both `release.yml` and `.githooks/commit-msg` ask for from `-alpha|-beta` to any lower case dashed pre-release, so `0.12.0-metal-beta` could be named. `tests/test_workflow_contract.py` feeds the same cases to both ends, running the hook and asking the gate through `grep` rather than through a Python copy of its pattern.
  - Merge method is a manual choice rather than an enforced one on either fork: neither repository has a ruleset or branch protection, `rulesets` is empty on both, and both leave all three merge methods enabled, so `CONTRIBUTING.md`'s "rebase merge alone" and its required `build`/`commits`/`label` checks are convention here. The checks are green on every pushed head, but nothing would refuse a green-looking squash.
- [x] Keep `.context/STATE.md` and `.context/TASKS.md` synchronized with the `60ff5610 + 82a0c75e` list-overflow hardware evidence, the CI-green `faed8e` repair, the reverted smoothstep false lead and the open terrain/border-fog boundary investigation.
- [x] Bring `docs/metallum-port.md` forward from its older hardware-head wording in a dedicated documentation synchronization pass; do not silently treat its stale SHA as current evidence.
  - The page named `bc5e180a` as the latest broad baseline and described the `0caa74ba` sky-ownership failures as current. It now records the `ac33fed3` structural validation, the `97dcf76d` Sundial Lite closure, the `c296caec` Ahead-mode LPV closure, the `60ff5610` Sodium list overflow and the CI-green `faed8e` repair awaiting hardware, plus the current head pair.
  - Metallum's `README.md` validation section was synced in the same pass: it cited heads `81295f04` / `4e160946` and workflow runs `34768563289` / `34771694544` and still called runtime validation outstanding, and it claimed the depth mip chain had no backend implementation while `MetalDepthMipmapBridge` exists and `MetalCommandEncoderMixin` reaches it by reflection.
- [x] Delete the leftover self-pushing patch workflow and refuse the shape in CI.
  - Metallum's `apply-graphics-storage-image-fix.yml` rewrote two source files, committed and pushed them back to the branch that triggered it; it is deleted, and the new push carries one check run (`ci`) instead of two.
  - `tools/ci-contracts.py` and `tests/test_workflow_contract.py` both refuse a workflow that could author a commit, reserve `contents: write` for `release.yml`, and require explicit `permissions:`; both were verified against positive and negative cases, including that `prefix.yml`'s documented `git push` does not trip the guard.
  - Any future CI change must pass that contract rather than be allowlisted around it.
- [x] Harden that guard after review showed the first version could be walked past.
  - The first version matched the literal text `git commit` / `git push` and the anchored regex `contents: write`. `git -c user.email=... commit`, a run of spaces, a `\` continuation, `permissions: write-all`, `contents: "write"` and `contents: write  # comment` all passed it while granting exactly what it exists to refuse. Both copies now parse the workflow: the git subcommand each `git` invocation runs, and the workflow-level `permissions:` mapping with quoting and trailing comments removed.
  - Both sides now pin the variants with tests that feed them through the matcher, because the checks over the real tree cannot show that a spelling the tree does not use yet is recognised. The live `git fetch` / `log` / `diff` / `rev-list` / `merge-base` calls in `commits.yml` and `prefix.yml` are pinned as legal.
  - Deleting the metallum workflow had also orphaned `tools/ci-graphics-storage-images.py`: nothing named it any more, so its assertions ran nowhere. It is named by `ci.yml` again, and both repositories now fail when a contract script is named by no workflow.
- [x] Run the contract scripts that no workflow named, then keep them wired.
  - Seven `tests/test_*.py` contracts were in no workflow at all: `test_background_pipeline_warmup`, `test_entity_reference_default`, `test_glsl_builtin_shadowing`, `test_native_shared_compute_contract`, `test_preprocessor_macro_redefinition`, `test_setup_compute_contract` and `test_sky_claim_coverage`. All seven passed when run by hand, so none of them was failing; none of them was enforcing anything either. `build.yml` now names them, and `test_workflow_contract.py` fails if a script in `tests/` drops out of every workflow again.
- [x] Keep the port status page inside the house text rule.
  - `docs/metallum-port.md` was pure ASCII before the sync; the rewrite introduced two U+2014 em dashes, which `:checkText` refuses along with en dashes, curly quotes, the ellipsis character and the non-breaking space. Build #291 failed on it. The prose is ASCII again, and the covered set (551 files) scans clean.
- [x] Pin code-bearing heads rather than branch tips in every status surface.
  - `docs/metallum-port.md`, this file's neighbour `STATE.md` and `metallum/README.md` each named a branch tip, so every documentation, CI, test or `.gitignore` commit made them stale while adding nothing: the renderer under test had not changed. They now name the code-bearing head (`faed8e` for Vitrail, `82a0c75e` for Metallum) and say that later commits touch documentation, CI, tests and `.gitignore` only.
  - Both PR bodies are corrected as well. Every code-bearing-head claim on both pages now reads `484fdd2d` for Vitrail and `82a0c75e` for Metallum with the behaviour-neutral successor named beside them, so a documentation-only commit does not date the page again. The status blocks that still called PHASE 17 active and Photon `Broken` are corrected in the same pass, because a PR description is a status surface and a claim there that contradicts the repository is the same defect as a stale `docs/` page.
  - The line naming `faed8e` as Vitrail's code-bearing head is superseded: `484fdd2d` is the current one, and `faed8e` remains the head that closed the Sodium list overflow.
- [x] Close the two PHASE 0 items that were still open by bringing the migration plan into the repository.
  - `.context/architecture/roadmap.md` holds the plan verbatim, at the same sha256 as the supplied source. It cannot live in `docs/`: `:checkText` refuses the 18 U+2014 and 10 U+201C/U+201D it contains, and `docs/` is a published English set.
  - `docs/roadmap.md` is the condensed English form, ASCII-clean, and it ends with the places the implementation deliberately differs from the plan's sketches: the bridge is an optional Mixin/reflection seam rather than the separately-modelled `vitrail-metallum` module, the capability interfaces are named differently, no pack runtime ever needed migrating out of Metallum, CI runs on macOS rather than Linux, the smoke packs are `*-contract` fixtures rather than a numbered ladder, and Photon was used early against the plan's own ordering. Each is an arrival at the same goal by another route, and saying so stops the next reader treating a sketch as a specification.
  - `AGENTS.md` rule 4 and the `docs/README.md` router point at both, and `AGENTS.md` records why the one uncompressed file sits in a directory defined as compressed mental models.
- [ ] Keep Vitrail/Metallum ownership boundaries strict in every follow-up: pack semantics/defaults/diagnostics in Vitrail; generic Metal execution in Metallum.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one runtime session, CI alone, or the absence of log-level errors.
