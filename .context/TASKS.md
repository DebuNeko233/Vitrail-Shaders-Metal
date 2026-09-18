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
