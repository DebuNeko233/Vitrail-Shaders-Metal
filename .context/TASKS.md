# Active Tasks

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## P0 - Complete PHASE 17 real shader-pack compatibility

The synthetic/runtime capability phases are closed; current work is evidence-backed real-pack compatibility on Apple Silicon.

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
- [ ] Continue the matrix through the remaining required pack families, including BSL-family and Sildur-family coverage, with the same evidence discipline.

The latest hardware baseline pairs Vitrail `c296caec` with Metallum `82a0c75e` on Apple M5 Pro / macOS 27 / native Metal. Photon v1.3b no longer exhibits the previously reproduced view-dependent coloured-light displacement/flicker in Ahead mode, hardware-validating the same-frame writer/compute direction at that ancestor. The same run exposes a separate missing far-cloud gradual fade; current Vitrail code is newer than the hardware baseline.

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
- [ ] Re-run the current code-bearing Vitrail head after the later `11e2569f` Sodium-list isolation refinements; ancestor hardware evidence must not be silently promoted to the newer code.
- [ ] Reconfirm ordinary shadow terrain and one non-view-centred voxel pack after the scheduling move.

### Native-Metal reversed smoothstep / cloud cutoff fade

- [x] Source-classify the newly observed Photon far-cloud fade gap without using `clouds_offset` as a false lead.
  - Photon disables vanilla clouds and renders its own volumetric chain; the log's unanswered `clouds_offset` is not used by the far-distance density cutoff in the audited cloud shaders.
  - Main cumulus hides its 20 km raymarch cutoff with literal descending `smoothstep(1.0, 0.95, ...)`; altocumulus uses the defined ascending form and subtracts it from one.
  - GLSL leaves `smoothstep` undefined when the first edge is not less than the second, so the OpenGL result cannot be assumed across SPIR-V/native Metal.
- [x] Implement a generic native-Metal compatibility normalization for statically reversed scalar literal edges only.
  - The translator leaves normal-order calls, dynamic-edge calls and pack-defined `smoothstep` functions untouched.
  - The helper writes the Hermite expression explicitly and the active-backend fact is part of the translation-cache key.
  - Vulkan explicitly publishes native-Metal=false, preserving its established translation and cache-key spelling.
- [x] CI-verify the reversed-smoothstep translation change: test-only head `07d1ba33` (code-bearing parent `bcf96268`) passes smoke/contracts and the full `./gradlew build` in macOS arm64 build #285.
- [ ] Hardware-rerun Photon and verify the far main-cumulus layer fades gradually before its cutoff.

## P2 - Keep acceptance and documentation synchronized

- [ ] Keep both PRs Draft/open/unmerged while PHASE 17 real-pack acceptance is incomplete.
- [x] Keep `.context/STATE.md` and `.context/TASKS.md` synchronized with the `c296caec + 82a0c75e` hardware baseline, the CI-green current same-frame path, and the CI-green native-Metal reversed-smoothstep cloud candidate awaiting hardware validation.
- [ ] Bring `docs/metallum-port.md` forward from its older hardware-head wording in a dedicated documentation synchronization pass; do not silently treat its stale SHA as current evidence.
- [ ] Keep Vitrail/Metallum ownership boundaries strict in every follow-up: pack semantics/defaults/diagnostics in Vitrail; generic Metal execution in Metallum.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one runtime session, CI alone, or the absence of log-level errors.
