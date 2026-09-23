# Metallum / Metal backend port status

This page tracks the backend work needed to run Vitrail shader packs through Metallum on macOS. The detailed roadmap and repository rules live in [`AGENTS.md`](../AGENTS.md); this page records what has actually been implemented and what is still only planned.

## Version baseline

The active compatibility baseline is:

- Minecraft Java Edition 26.2
- Java 25
- Sodium 0.9.2 stable for Minecraft 26.2
- Vitrail uses `net.caffeinemc:sodium-fabric:0.9.2+mc26.2`
- Metallum uses the Modrinth release artifact form `mc26.2-0.9.2-fabric`

The two Sodium strings are intentionally different because the repositories resolve Sodium from different Maven coordinates.

## Metallum foundation

The companion backend work lives in `DebuNeko233/metallum`; its pull request is merged into `master`, and the branch it came from no longer exists.

The current backend foundation includes:

- up to eight indexed Metal colour attachments, preserving unused/null slots;
- complete indexed `RenderPipeline.getColorTargetStates()` handling;
- per-target Metal format, write mask, blend state and clear value;
- attachment-set identity and depth-only render-pass sizing;
- backend-native colour mipmap generation, and a generic D32 progressive-nearest depth path for the mip chains that command cannot reduce;
- selective compiled-pipeline eviction with deferred native release;
- backend-owned zero-initialized shader-storage buffers;
- backend-owned writable 1D, 2D and true-3D textures;
- an optional bridge that creates an otherwise ordinary Minecraft texture with Metal `ShaderWrite` usage for writable colour targets;
- raw SPIR-V storage-buffer and storage-image reflection;
- storage-texture zero clear and exact region-copy primitives;
- a general shader-pack compute bridge that compiles SPIR-V to MSL, owns `MTLComputePipelineState`, binds Minecraft facade resources by reflected name/binding, and dispatches exact workgroup counts;
- background-safe ordinary render-pipeline precompilation for Vitrail family warm-up.

This remains backend GPU behaviour. Shader-pack target selection, ping-pong/history, custom-image policy, camera reanchor policy, program scheduling and resource naming remain Vitrail responsibilities.

At this status update the two builds are paired by the integration contract rather than by a commit: Vitrail understands `API_VERSION` 1 (`common/src/main/java/dev/vitrail/render/MetallumStatus.java`), a Metallum build answers with `MetallumApi.apiVersion()`, and a mismatch is reported as incompatible rather than used. An exact head is still named wherever a *claim* rests on one, because an evidence bundle records the exact material it ran, but it is no longer how the pair is described: Metallum has not been released yet, so there is no version to name on its side, and a commit hash on a branch that has since been merged names history rather than a head. The Metal foundation and baseline PHASE 5-16 acceptance have Apple-Silicon real-device evidence, and PHASE 17 is closed by the project owner's judgement rather than left open; the next section says what that leaves unverified.

## Vitrail backend-neutralization

The backend-neutralization baseline is merged into `dev` and released: `v0.12.0-metal-beta` was tagged on `main` at `55d6d6a8`, and `dev` has since opened `0.13.0-dev`. The code-bearing head the device evidence names is `484fdd2d4dc2fd80f7cb1254db4869cbcee1d0a2`, whose behaviour-neutral documentation successors carried it to that release. The two defects that used to be outstanding at the head of this page are both closed: the `60ff5610cf3ce60f05fcd0bbe1566b32fa00adb4` Sodium camera-list restoration overflow is repaired at `faed8edadf705440419bdfb7049f10127c3e607b` and confirmed on hardware across the sessions at that repair's successor `16b051c1c767c50269cbd67ff48220f82edfe8f3`, and the shadow matrix publish defect that `cd52b13f` introduced is repaired at `484fdd2d`, with the tester confirming on hardware that the cutout-vegetation ghosting is gone. The sky-ownership path that the earlier `0caa74ba6f91f97584cfad0d8d9172a30181986a` attempt broke was repaired and carries structural Apple-Silicon evidence at `ac33fed3fa3323784c24bc6c96db82d828c51393`.

PHASE 17 real shader-pack compatibility is closed by the project owner's judgement, with no per-row compatibility status recorded; [`phase17-compatibility.md`](phase17-compatibility.md) records what that leaves unverified. The earlier phases remain acceptance baselines rather than a claim that those subsystems can no longer receive fixes.

### Backend-neutral Sodium terrain binding

Sodium 0.9.2 calls `pass.setPipeline(...)` before `DrawContext#setContext(...)`. The former Sodium draw-context mixin injected `TerrainDraw.bind(...)` at the head of `DrawContext#setContext`; the common wrapper performs the same bind immediately before delegating to the generic `DrawContext#setContext` invocation. The effective order is therefore unchanged: pipeline set, Vitrail resources bound, draw-context native state captured, then chunk draws.

### Optional Metal capability providers

Optional `@Pseudo` mixins bridge package-private Metallum classes without putting Metallum on Vitrail's common compile classpath. Current providers cover independent blending, mipmap generation, selective pipeline eviction, shader-storage-buffer allocation, writable storage-image allocation, ordinary shader-writable texture allocation, storage-image clear/copy commands, and compute pipeline/dispatch.

A separate optional public-API probe covers background render-pipeline precompilation. Vitrail calls ordinary `GpuDevice.precompilePipeline` from its family warm-up workers only when Metallum explicitly advertises that narrow operation as background-safe; older/incompatible APIs fail closed and retain first-draw compilation. Command encoding is not included in that guarantee.

`DumpedProgram` also exposes backend-neutral warm-up eligibility. Optional program families that cannot draw in the current runtime configuration are refused before detached precompile rather than counted as failed warm-up compiles. `DistantProgram` uses this only for warm-up; its real first-draw `compile()` path remains available if the runtime later makes Distant Horizons usable.

Missing capabilities remain explicit narrow interfaces rather than backend-name guesses.

### Shader-storage buffers and images

`StorageBuffers` and `StorageImages` continue to own shader-pack declaration and lifetime policy. A backend provider returns normal Minecraft facade resources.

For backend-neutral compute:

- `StorageBuffers.facadeSlice(name)` exposes a backend-owned SSBO strictly as `GpuBufferSlice`;
- `StorageImages.facadeView(name)` exposes a backend-owned storage image as `GpuTextureView`;
- `CustomImages.storage(name)` decides whether a custom-image name is the writable image uniform rather than a sampled alias. That answer comes from the pack declaration, not from whether a backend-native handle exists.

No native buffer, image view, texture or backend argument index crosses these facade paths.

### Shader-writable colour targets

A compute can write a normal pack colour target through `colorimgN`, so the target has to be created with writable-image usage before any dispatch can safely use it. Minecraft 26.2 has no public storage-image usage bit.

`TargetSurface` continues to decide whether a target is compute-writable. It unwraps the `GpuDeviceBackend` through the existing `GpuDeviceAccessor` and, when the backend implements `ShaderWritableTextureBackend`, asks for the same ordinary target texture with the one missing allocation fact added. The result remains a normal `GpuTexture`, including the existing render-attachment, sampled, copy and mip-chain semantics.

Metallum uses its `MetalTextureBridge`, which selects the already-existing `MetalGpuTexture(..., shaderWrite=true)` path and therefore adds `MTLTextureUsageShaderWrite` without teaching Metallum any `colorimgN` naming or pack policy. Where no explicit capability is present, the target is still created through the ordinary `GpuDevice.createTexture(...)` call with its `TextureUsage`.

Graphics-stage storage-image writes use the same ownership model. Metallum marks the written texture contents dirty and ends the native render encoder at the logical pass boundary so its existing fence transition makes untracked shader writes visible to later passes. Shader-pack resource meaning remains Vitrail-owned.

### Backend-neutral compute seam

`ComputeDeviceBackend` owns native compute-pipeline lifetime:

- `vitrail$compileCompute(label, spirv)` receives translated SPIR-V and returns an opaque backend-owned token;
- `vitrail$closeCompute(token)` returns it to the backend for safe native destruction.

`ComputeCommands` owns one resolved dispatch:

- `Map<String, GpuBufferSlice>` for buffers;
- `Map<String, GpuTextureView>` for textures;
- `Map<String, GpuSampler>` for sampled-image sampler states;
- exact workgroup counts and exact local workgroup dimensions.

Metallum remains responsible for SPIR-V-to-MSL translation, resource binding indices, `MTLComputePipelineState`, Metal arguments, encoder transitions, fences and native lifetime. It uses `dispatchThreadgroups:threadsPerThreadgroup:` because the values Vitrail resolves are workgroup counts, not total thread counts.

### Caller-side compute split

The caller-side migration uses three backend-neutral pieces:

1. `ComputeResources` reflects only the resource names actually present in an already-compiled SPIR-V module. It inventories uniform buffers, storage buffers, sampled images and storage images without rewriting any binding decoration or creating a native object.
2. `PackComputeBindings` resolves those names to Minecraft facade resources. It preserves the established policy order: custom-image resources and pack texture overrides first, then the selected ping-pong colour target, pass depth/distant/centre depth, engine textures, the stage default target and finally the existing black fallback.
3. `BackendComputePass` owns the pass state that is not backend-native: the uniform ring, opaque backend pipeline token, resource inventory and resolved dispatch. Native pipeline construction, resource binding, encoder ordering and destruction stay in the backend.

`PackCompute` routes shadow, chained and standalone computes through this path when both device and command capabilities are present. Pass teardown closes the backend-owned pipeline and uniform ring. Backend dispatch diagnostics name each program and its group/local dimensions only after an accepted non-zero dispatch. Compile refusal, binding failure, encoder rejection and zero-group no-ops do not produce a success line; acceptance still does not prove GPU completion or correct output.

The optional Metallum adapter resolves all three bridge methods inside a normal call and caches them only after every lookup succeeds. A missing class or incompatible signature raises a catchable exception without poisoning class initialization; backend runtime exceptions and fatal errors retain their original type.

This split is deliberate: Vitrail decides what a resource name means; the backend decides how that already-resolved facade object is bound natively.

### Metal local-size and shared-memory rules

Metal dispatch needs both workgroup counts and `threadsPerThreadgroup`, so the backend pass validates the actual shaderc-preprocessed compute text before compiling the native pipeline.

- `local_size_x` must be present and readable.
- `local_size_y` and `local_size_z` use GLSL's default of one only when the axis is genuinely omitted after preprocessing.
- An explicitly written axis whose value is still not a positive integer after preprocessing is refused rather than silently replaced with one.
- Shared/threadgroup declarations are sized from the same preprocessed text.
- A shared declaration that Vitrail cannot size is refused on native Metal.
- A declaration above the verified 32768-byte Metal threadgroup-memory limit is served by a transient storage-buffer fallback only when the dispatch is fixed to exactly one work group and the active backend exposes generic storage-buffer allocation.
- Multi-workgroup oversized shared memory, or an oversized single-workgroup program on a backend without that allocation capability, fails closed rather than silently changing GLSL `shared` semantics.

The 2026-09-18 Photon run exercises the oversized-single-workgroup fallback in real execution: `world0/deferred4_a` asks for 36864 bytes, compiles through the active backend and dispatches as groups `(1, 1, 1)` / local `(256, 1, 1)`.

### Pack preprocessor macro redefinition

Pack source expansion preserves the pack's own active-path redefinition semantics before shaderc sees the source. When the same pack macro is defined again on the same active preprocessing path, `IncludeExpander` emits a matching `#undef` immediately before the later `#define`, preserving later-definition-wins behavior without blanket-undefining Vitrail/compiler environment macros.

This is a generic source-preparation rule, not a Bliss or `diagonal3` special case. The 2026-09-18 Bliss run no longer reports the earlier `diagonal3` shaderc redefinition failure.

### Reference-unbacked vertex inputs

The extended entity mesh already carries the four identifier lanes backing Iris's `iris_Entity` attribute. Vitrail answers pack-facing `mc_Entity` from that same backing storage, with explicit conversion to the type declared by the shader, rather than treating it as an unavailable constant. This does not add another vertex element or change the entity stride.

Entity `at_midBlock` is intentionally different. Iris 26.1 defines it on `IrisVertexFormats.TERRAIN`; `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but no `at_midBlock`, and Iris shader keys use `ENTITY` for both ordinary and shadow entities. Vitrail therefore classifies `at_midBlock` as reference-unbacked for ordinary/shadow entity diagnostics instead of growing a Vitrail-only entity ABI. Hardware logs after that change contain no `at_midBlock` warning while shadow entities continue to draw.

The same rule now covers the other source-audited families without changing their mesh layouts:

- `PARTICLES`, `PARTICLES_TRANS` and `WEATHER` use `DefaultVertexFormat.PARTICLE`, which has no backing element for `mc_Entity`, `mc_midTexCoord` or `at_tangent`. Vitrail keeps their real answer set empty and classifies those names only for the missing-input diagnostic. The `37d06c12` hardware run verifies the warnings are gone while particle and weather draws remain active.
- Iris sky keys use `POSITION`, `POSITION_COLOR`, `POSITION_TEX` or `POSITION_TEX_COLOR`. None backs `mc_Entity` or `mc_midTexCoord`. With zero entity components `VanillaCoreTransformer` leaves a pack-declared `mc_Entity` unbacked, and an explicit `mc_midTexCoord` likewise does not become a physical sky field. Vitrail classifies the two observed names as reference-unbacked without changing any sky format.
- `LINES` uses `DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH`, which carries neither UV2 nor an entity id. `VanillaCoreTransformer` renames `vaUV2` to an `iris_UV2` input even when the format has no light element, while zero entity components leave a pack-declared `mc_Entity` unbacked. Vitrail keeps `LinesVertex.ANSWERED` and the line stride unchanged and classifies only those two observed names for diagnostics.

A reference-unbacked classification is narrower than value parity. Iris can read OpenGL generic-attribute state at an unbacked location, whereas Vitrail supplies its existing deterministic synthesized constant. These changes say only that the vertex buffer is not missing a reference-required field; they do not claim that every unbacked value equals Iris draw-for-draw.

### Native ordering stays native

Ordering is not carried across the seam as an API. Metallum's render/blit/compute encoder transitions and its `MTLFence` chain provide Metal ordering, and Vitrail states a dependency rather than naming a barrier.

## Current validation status

Both requests were merged, the companion first, and this repository released **`v0.12.0-metal-beta`** from `main` at `55d6d6a8`: the tag is checked against `gradle.properties`, the release body is this version's `CHANGELOG.md` entry, and the merged jar `vitrail-0.12.0-metal-beta+mc26.2.jar` is attached. `dev` has since opened `0.13.0-dev`, which is the shape nothing will tag. **The release promotes no pack.** A version shipping is a statement about the engine, not about compatibility: the matrix still carries no status for any row, exactly as recorded below. The Metal route is the maintained path from here, and the companion is what it runs on; the companion has no released version yet, so obtaining it is still a build-from-source matter and its first release is open work.

The reviewed hardware sessions that precede the newest ones pair Vitrail `60ff5610cf3ce60f05fcd0bbe1566b32fa00adb4` with Metallum `82a0c75e53e28390472b3c26b569cdc2335d90b4`, and earlier Vitrail `ac33fed3fa3323784c24bc6c96db82d828c51393` with the same Metallum head. The `0caa74ba6f91f97584cfad0d8d9172a30181986a` attempt that first reached the claimed-sky path was not an acceptance pass: Photon exposed a generated-GLSL ordering-helper omission for coverage-only fragments, and Solas an attachment-format mismatch when the ownership sibling was bound into the already-open sky render pass. Both defects are repaired. The `60ff5610` session then exposed a separate Vitrail scheduling defect: after sustained Photon rendering, Sodium `ChunkRenderList.add` throws `Render list is full` through `RenderSectionManager.readRenderListFromTree` and `ShadowTerrain.restoreCameraWalk`, and Vitrail stops drawing the shadow map. Code-bearing `faed8e` repairs it, and that repair has since run on hardware: every session at its behaviour-neutral successor `16b051c1c767c50269cbd67ff48220f82edfe8f3` reaches the 600-frame shadow census with the map drawn 598 to 600 times and no failure of any kind.

The newest sessions pair Vitrail `484fdd2d4dc2fd80f7cb1254db4869cbcee1d0a2` (behaviour-neutral documentation successor `5ab260ab1c007cec53f22eb925954e93cb7dc951`) with the same Metallum code-bearing head `82a0c75e53e28390472b3c26b569cdc2335d90b4`. They close a defect no run had named. `cd52b13f` moved the shadow draw into the frame, because a pack that voxelises into its shadow pass has to share one frame with the compute reading the volume, and the four shadow matrix uniforms a pack reads were still published from the pair the map was *not* drawn with, whose premise was the older arrangement. Every pack shadow lookup therefore landed where the caster stood one draw earlier: invisible while the camera was still and a displaced shadow the moment it moved, clearest on the fine shadow content a pack reads for leaf and grass self-shadowing, and read by the pack's `SHADOW`, `SSS` and `AO_IN_SUNLIGHT` terms alike. `484fdd2d` publishes the pair the shadow stage actually draws with, and the tester confirms the ghosting gone on that head. It was found by auditing the publish path against Iris, not by a device session.

At these baselines:

- PHASE 2 foundation: **CLOSED / Real-device Verified**;
- PHASE 5-15 baseline acceptance: **CLOSED / Real-device Verified**;
- PHASE 16 Advanced Features baseline acceptance: **CLOSED / Real-device Verified** on Apple M5 Pro / macOS 27 / Metal;
- PHASE 17 Real Shader Pack Compatibility: **CLOSED by project-owner judgement, with no per-row status recorded**.

"Closed" here means the phase's acceptance baseline has real-device evidence. It does not mean that a later real pack cannot expose a generic defect in that subsystem; PHASE 17 findings continue to be fixed at the owning shader-pack contract, Minecraft contract or Metal capability. PHASE 17 is the one exception to the first sentence, and it is worth stating plainly rather than leaving to be inferred: its owner closed it on the device sessions for a single pack, knowing that the phase's own policy asks for a reviewed per-row record that was never written. No row in the matrix carries a status, the four rows other than Photon have never run on device, and [`phase17-compatibility.md`](phase17-compatibility.md) records that outcome alongside the findings that were still open when it closed.

### 2026-09-18 runtime evidence

The Apple M5 Pro / macOS 27.0 / Metal sessions exercise Bliss v2.1.2, Complementary Reimagined r5.9.1, MakeUp Ultra Fast 9.5e, Photon v1.3b, Solas Shader V3.7b and Sundial Lite v1.1.0. The launcher builds come from the two project feature branches named above.

They read as a progression rather than one pass, and this page records the newest evidence for each subsystem rather than the oldest:

- every tested pack reaches a first full frame and the client later reaches clean `Stopping!`;
- the `bc5e180a8580757bf8863abb2d7c130ca1476913` log carries no Vitrail or Metallum log-level `ERROR` or `FATAL`, and its sky/line diagnostic classification is hardware-verified for every path that run exercised: no sky `mc_Entity` / `mc_midTexCoord` and no line `vaUV2` missing-real-attribute warning, while Photon sky/line and Solas sky still record their draws. That run did not trigger the Solas line draw, so the one observed `mc_Entity` line path stays source-classified rather than re-exercised;
- Photon, MakeUp and Complementary no longer attempt unusable no-DH `distant*` programs during detached warm-up;
- Bliss no longer hits the `diagonal3` macro-redefinition failure;
- Photon compiles past the previously recorded `world0/prepare/vertex` blocker, reaches its chain, compiles and dispatches `world0/deferred4_a` on Metal;
- Solas compiles and dispatches `shadowcomp` on Metal as groups `(24, 12, 24)` / local `(8, 8, 8)`;
- entity `at_midBlock` is absent after its diagnostic correction while the shadow-entity path remains active, and the exercised particle and weather draw paths remain active with no `at_tangent` regression;
- `97dcf76d` closes Sundial Lite's first deterministic blocker: its pack-local `min3` / `max3` helpers are no longer read as an overload against compiler built-ins, the leftover pipelines compile and the chain reaches a first full frame;
- `c296caec3aca9d64b2030c424a33c898a827ba24` closes the observed Photon defect. With `Voxel Volume Center = Ahead`, a stationary Nether-portal emissive light no longer shifts or flickers under view-only rotation, because the voxel writer and `shadowcomp` now share the current camera and view uniforms in the same frame; the tester's A/B against `Player` isolated that as a view-centre term rather than a camera-position one;
- `ac33fed3` structurally validates the repaired sky-ownership replay. Solas records its disc first draw and horizon cone with no Minecraft 26.2 attachment-format rejection, and Photon's outputless `world0/gbuffers_skybasic` reports coverage-only at colour rank zero, records its first draw and continues through compute and entity work to clean world exit, with no missing `ofOrderOutputs()` helper and no sky sibling build or compile warning;
- `60ff5610` exposes the scheduling defect described under current validation status after sustained Photon rendering; it is repaired at code-bearing `faed8e` and confirmed on hardware by the `16b051c1` sessions, which cross the former failure point and reach the 600-frame shadow census intact;
- `484fdd2d` closes the reported positional ghosting on cutout vegetation. The four shadow matrix uniforms a pack reads had been published from the pair the shadow map was not drawn with since `cd52b13f` moved that draw into the frame, so every pack shadow lookup landed one draw behind the caster: invisible while the camera was still, a displaced shadow as soon as it moved, and clearest on the leaf and grass self-shadowing a pack reads through `SHADOW`, `SSS` and `AO_IN_SUNLIGHT`. The tester confirms it gone, on the newest session, which opens the pack eight times and reaches eight first full frames.

Two of those results are narrower than they look. The `ac33fed3` session does not load Bliss, so it does not close the primary visual acceptance question, which is whether a Bliss `gbuffers_skybasic` discard still lets the vanilla scene seed repaint a claimed sky; nor does it exercise the End sky, the other `covers=true` branch. The Sundial Lite result closes that pack's first fatal only and assigns no compatibility status.

None of this promotes Photon or any other pack. A clean runtime log, CI, a plausible frame and a warning count are each insufficient under the PHASE 17 evidence policy, which still requires reviewed screenshot/reference material.

Comparison-vs-ordinary shadow sampler declarations and first-frame `nothing fills them yet` resource diagnostics remain separate. They should stay explicit until reference behavior or persistent visual evidence identifies a concrete contract to change.

## Acceptance required before merge

The list below is what was still outstanding when PHASE 17 closed, not a gate that still blocks. The owner closed the phase without the reviewed per-row record its policy asks for, so the compatibility matrix carries no status for any row and the evidence path stays unused rather than half-filled. Each item is marked with where it stands now. Both requests have since been merged and `v0.12.0-metal-beta` released from them. Nothing in this list was closed by that, and every open item below is now on `dev` and on `main` as a recorded gap rather than a resolved one.

1. **Done.** Re-running the current code-bearing head past the former Sodium list-overflow point was the item the `16b051c1` sessions satisfied: each reaches the 600-frame shadow census with no `Render list is full`, no shadow-stage shutdown and no stage-failure marker. Keep reading a log for that marker family rather than only for its frame count, because `Vitrail stopped drawing this pack after an error` is what `tests/phase17_collect_session.py` records as a high-confidence fatal and what makes `tests/phase17_compatibility.py` return `Broken`; a session whose shadow stage dies without it would be filed as a fallback rather than a failure, which is the one reading those runs must not be given;
2. **Partly done.** The stationary Nether-portal emissive light was reconfirmed stable at `c296caec`, and nothing since has re-opened that specific observation. What is still open is the later view-only re-observation of the same light: Photon centres its LPV voxel grid from the inverse model view, and the identity volume is physically reanchored by whole-block camera translation only, so a pure rotation can still leave writer and compute one integer voxel apart. Ordinary shadow terrain and a non-view-centred voxel pack are likewise unreconfirmed after the scheduling move;
3. **Not done.** No PHASE 17 screenshot/reference material was collected. The tester's Bliss scene-seed check - "no obvious problem" in the `3d951772` multi-pack run, a simple visual pass - is a useful regression observation and is not the reviewed evidence the policy requires, and the End sky branch, the other `covers=true` branch, has still never been exercised;
4. **Not done.** The real Photon compute-dispatch evidence and its paired-head metadata exist in device logs but were never carried into the formal evidence path;
5. **Maintained.** Comparison, sampler and first-frame resource diagnostics stay separate from vertex-input classification and are investigated only against reference semantics or persistent visual evidence;
6. **Not done.** The matrix's BSL-family and Sildur-family rows have never run under Vitrail on Metal. Neither has Complementary, and MakeUp Ultra Fast reached a first full frame and clean shutdown without a compatibility record;
7. **Maintained.** Backend-neutral and shared-path regression coverage is kept for every contract changed while fixing these findings.

A green Gradle build, successful client launch, full warm-up count or successful compute dispatch is useful evidence, but none alone proves rendering correctness or shader-pack compatibility.

## Next work

The hardware rerun that used to head this section has happened, and its finding is the shadow-matrix publish defect closed at `484fdd2d`; the shadow stage stays alive across the 600-frame census and the tester confirms the ghosting gone. What remains is the open list above rather than a rerun: the view-centre gap behind the Nether-portal emissive light, ordinary shadow terrain and a non-view-centred voxel pack after the scheduling move, and the reviewed Bliss/End-sky material that was never collected.

The distant abrupt transition the tester reports is no longer treated as a cloud problem. The reversed-`smoothstep` candidate produced no visual improvement on hardware and was reverted in `c3a619e0429d683a19d5d80e1398ed899a158e22`, so no speculative cloud rewrite remains in production translation. Photon enables `BORDER_FOG` by default to hide the render-distance boundary, the failing run uses a 16-chunk view distance, and Vitrail's Iris-shaped horizon cone is also drawn at 256 blocks. The owning area to inspect is therefore terrain depth, reconstructed scene position, border fog and the terrain-to-sky/scene-seed handoff at that shared distance; `BORDER_FOG` is A/B'd only as a diagnostic, and cloud math is not patched from that symptom. This one is still open, and it is the finding most likely to decide whether Photon would be `Supported` or `Partially Supported` if its record were ever written.

The production Metal shader-pack gate remains conservative: PHASE 17 closed without compatibility statuses, so no pack is promoted by that closure. `phase17-compatibility.md` defines compatibility evidence; `VITRAIL_SMOKE.md` in the companion Metallum repository documents deterministic developer smoke launchers. Neither CI nor a smoke launcher alone changes the support claim.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
