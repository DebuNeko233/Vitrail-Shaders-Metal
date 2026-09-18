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

The companion backend work lives in `DebuNeko233/metallum`, branch `feat/mc26.2-mrt-foundation`, Draft PR #1.

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

At this status update the companion Metallum branch head is `283bf389dda9f7c84b92f57ab06b99395a0daf92`, whose code-bearing Metal head is `82a0c75e53e28390472b3c26b569cdc2335d90b4`; the commit after it changes CI only and no backend behaviour. The Metal foundation and baseline PHASE 5-16 acceptance already have Apple-Silicon real-device evidence; PHASE 17 real-pack compatibility remains active and may still expose generic defects that must be fixed in their owning subsystem.

## Vitrail backend-neutralization

The backend-neutralization baseline is implemented on branch `feat/backend-neutral-sodium-terrain-hook` and Draft PR #1, targeting `dev` as required by repository policy. Its committed tip is `840dc322e7cf994f337afe1fce34c76e1315a4b9`, of which the code-bearing head is `faed8edadf705440419bdfb7049f10127c3e607b`; the two commits after it change documentation and CI only. `faed8e` is green in build #290 and still needs a hardware rerun before the current scheduling is closed. The sky-ownership path that the earlier `0caa74ba6f91f97584cfad0d8d9172a30181986a` attempt broke was repaired and carries structural Apple-Silicon evidence at `ac33fed3fa3323784c24bc6c96db82d828c51393`, and the later `60ff5610cf3ce60f05fcd0bbe1566b32fa00adb4` run exposed the Sodium camera-list restoration overflow that `faed8e` repairs.

PHASE 17 real shader-pack compatibility is active. The earlier phases remain acceptance baselines rather than a claim that those subsystems can no longer receive fixes.

### Backend-neutral Sodium terrain binding

Sodium 0.9.2 calls `pass.setPipeline(...)` before `DrawContext#setContext(...)`. The former Vitrail Vulkan mixin injected `TerrainDraw.bind(...)` at the head of `VKDrawContext#setContext`; the current common wrapper performs the same bind immediately before delegating to the generic `DrawContext#setContext` invocation. The effective order is therefore unchanged: pipeline set, Vitrail resources bound, draw-context native state captured, then chunk draws.

### Optional Metal capability providers

Optional `@Pseudo` mixins bridge package-private Metallum classes without putting Metallum on Vitrail's common compile classpath. Current providers cover independent blending, mipmap generation, selective pipeline eviction, shader-storage-buffer allocation, writable storage-image allocation, ordinary shader-writable texture allocation, storage-image clear/copy commands, and compute pipeline/dispatch.

A separate optional public-API probe covers background render-pipeline precompilation. Vitrail calls ordinary `GpuDevice.precompilePipeline` from its family warm-up workers only when Metallum explicitly advertises that narrow operation as background-safe; older/incompatible APIs fail closed and retain first-draw compilation. Command encoding is not included in that guarantee.

`DumpedProgram` also exposes backend-neutral warm-up eligibility. Optional program families that cannot draw in the current runtime configuration are refused before detached precompile rather than counted as failed warm-up compiles. `DistantProgram` uses this only for warm-up; its real first-draw `compile()` path remains available if the runtime later makes Distant Horizons usable.

Missing capabilities remain explicit narrow interfaces rather than backend-name guesses.

### Shader-storage buffers and images

`StorageBuffers` and `StorageImages` continue to own shader-pack declaration and lifetime policy. Vulkan keeps its direct VMA resource paths. A backend provider instead returns normal Minecraft facade resources.

For backend-neutral compute:

- `StorageBuffers.facadeSlice(name)` exposes a backend-owned SSBO strictly as `GpuBufferSlice`;
- `StorageImages.facadeView(name)` exposes a backend-owned storage image as `GpuTextureView`;
- `CustomImages.storage(name)` decides whether a custom-image name is the writable image uniform rather than a sampled alias. That answer comes from the pack declaration, not from whether a Vulkan-native handle exists.

No `VkBuffer`, `VkImageView`, `MTLBuffer`, `MTLTexture` or backend argument index crosses these facade paths.

### Shader-writable colour targets

A compute can write a normal pack colour target through `colorimgN`, so the target has to be created with writable-image usage before any dispatch can safely use it. Minecraft 26.2 has no public storage-image usage bit.

`TargetSurface` continues to decide whether a target is compute-writable. It unwraps the `GpuDeviceBackend` through the existing `GpuDeviceAccessor` and, when the backend implements `ShaderWritableTextureBackend`, asks for the same ordinary target texture with the one missing allocation fact added. The result remains a normal `GpuTexture`, including the existing render-attachment, sampled, copy and mip-chain semantics.

Vulkan remains unchanged: when no explicit capability is present, `TargetSurface` still raises `TextureUsage` around the ordinary `GpuDevice.createTexture(...)` call and `VulkanConstMixin` adds `VK_IMAGE_USAGE_STORAGE_BIT`. Metallum instead uses its optional `MetalTextureBridge`, which selects the already-existing `MetalGpuTexture(..., shaderWrite=true)` path and therefore adds `MTLTextureUsageShaderWrite` without teaching Metallum any `colorimgN` naming or pack policy.

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

Metallum remains responsible for SPIR-V-to-MSL translation, resource binding indices, `MTLComputePipelineState`, Metal arguments, encoder transitions, fences and native lifetime. It uses `dispatchThreadgroups:threadsPerThreadgroup:` because Vitrail's established Vulkan `vkCmdDispatch(x,y,z)` values are workgroup counts, not total thread counts.

### Caller-side compute split

The caller-side migration uses three backend-neutral pieces:

1. `ComputeResources` reflects only the resource names actually present in an already-compiled SPIR-V module. It inventories uniform buffers, storage buffers, sampled images and storage images without rewriting any binding decoration or creating a native object.
2. `PackComputeBindings` resolves those names to Minecraft facade resources. It preserves the existing Vulkan policy order: custom-image resources and pack texture overrides first, then the selected ping-pong colour target, pass depth/distant/centre depth, engine textures, the stage default target and finally the existing black fallback.
3. `BackendComputePass` owns the non-Vulkan pass state: the uniform ring, opaque backend pipeline token, resource inventory and resolved dispatch. Existing Vulkan layout creation, push descriptors, barriers, `WideSamplerSets`, native pipeline and destruction stay in `PackCompute.Pass` and have not been moved into this class.

`PackCompute` routes shadow, chained and standalone computes through this path when both device and command capabilities are present. Otherwise it retains the existing Vulkan route. Pass teardown closes the backend-owned pipeline and uniform ring. Backend dispatch diagnostics name each program and its group/local dimensions only after an accepted non-zero dispatch. Compile refusal, binding failure, encoder rejection and zero-group no-ops do not produce a success line; acceptance still does not prove GPU completion or correct output.

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

### Vulkan-specific code that stays Vulkan-specific

`WideSamplerSets` remains a MoltenVK/Vulkan push-descriptor workaround. Vulkan synchronization barriers and direct descriptor writes remain on the Vulkan branch. They are not translated into Metal operations; Metallum's render/blit/compute encoder transitions and `MTLFence` chain provide Metal ordering.

## Current validation status

Both repositories remain Draft, open and unmerged. The Metal shader-pack route remains developer-validation-only.

The most recent reviewed hardware sessions pair Vitrail `60ff5610cf3ce60f05fcd0bbe1566b32fa00adb4` with Metallum `82a0c75e53e28390472b3c26b569cdc2335d90b4`, and earlier Vitrail `ac33fed3fa3323784c24bc6c96db82d828c51393` with the same Metallum head. The `0caa74ba6f91f97584cfad0d8d9172a30181986a` attempt that first reached the claimed-sky path was not an acceptance pass: Photon exposed a generated-GLSL ordering-helper omission for coverage-only fragments, and Solas an attachment-format mismatch when the ownership sibling was bound into the already-open sky render pass. Both defects are repaired. The `60ff5610` session then exposed a separate Vitrail scheduling defect: after sustained Photon rendering, Sodium `ChunkRenderList.add` throws `Render list is full` through `RenderSectionManager.readRenderListFromTree` and `ShadowTerrain.restoreCameraWalk`, and Vitrail stops drawing the shadow map. Code-bearing `faed8e` repairs it and is CI-green, but no hardware run has confirmed that yet.

At these baselines:

- PHASE 2 foundation: **CLOSED / Real-device Verified**;
- PHASE 5-15 baseline acceptance: **CLOSED / Real-device Verified**;
- PHASE 16 Advanced Features baseline acceptance: **CLOSED / Real-device Verified** on Apple M5 Pro / macOS 27 / Metal;
- PHASE 17 Real Shader Pack Compatibility: **active**.

"Closed" here means the phase's acceptance baseline has real-device evidence. It does not mean that a later real pack cannot expose a generic defect in that subsystem; PHASE 17 findings continue to be fixed at the owning shader-pack contract, Minecraft contract or Metal capability.

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
- `60ff5610` exposes the scheduling defect described under current validation status after sustained Photon rendering; it is repaired at code-bearing `faed8e`, which is CI-green and awaiting a hardware rerun.

Two of those results are narrower than they look. The `ac33fed3` session does not load Bliss, so it does not close the primary visual acceptance question, which is whether a Bliss `gbuffers_skybasic` discard still lets the vanilla scene seed repaint a claimed sky; nor does it exercise the End sky, the other `covers=true` branch. The Sundial Lite result closes that pack's first fatal only and assigns no compatibility status.

None of this promotes Photon or any other pack. A clean runtime log, CI, a plausible frame and a warning count are each insufficient under the PHASE 17 evidence policy, which still requires reviewed screenshot/reference material.

Comparison-vs-ordinary shadow sampler declarations and first-frame `nothing fills them yet` resource diagnostics remain separate. They should stay explicit until reference behavior or persistent visual evidence identifies a concrete contract to change.

## Acceptance required before merge

PHASE 17 remains the merge gate. Before either Draft PR becomes ready, real-pack evidence must be collected and reviewed under [`phase17-compatibility.md`](phase17-compatibility.md). In particular:

1. re-run the current code-bearing head with Photon `Voxel Volume Center = Ahead`, for long enough and with enough movement to cross the former Sodium list-overflow point, and require the shader chain and shadow map to stay active with no `Render list is full` and no shadow-stage shutdown;
2. at that same head, reconfirm the stationary Nether-portal emissive light still does not shift or flicker under view-only rotation, and reconfirm ordinary shadow terrain plus one non-view-centred voxel pack after the same-frame scheduling move;
3. turn runtime progress into reviewed pack evidence only where the required screenshot/reference material exists, beginning with the tester's simple Bliss visual check, and exercise the End sky, the other `covers=true` branch;
4. preserve the real Photon compute-dispatch evidence and exact paired-head metadata in the formal evidence path;
5. keep comparison/sampler/resource diagnostics separate from vertex-input classification, and investigate them only against reference semantics or persistent visual evidence;
6. continue the required real-pack matrix, including the BSL-family and Sildur-family rows not covered by the current sessions;
7. keep Vulkan/shared-path regression coverage for every backend-neutral contract changed while fixing PHASE 17 findings.

A green Gradle build, successful client launch, full warm-up count or successful compute dispatch is useful evidence, but none alone proves rendering correctness or shader-pack compatibility.

## Next work

Immediate work is a hardware rerun of code-bearing `faed8e` with Photon `Voxel Volume Center = Ahead`, long enough and with enough movement to cross the former Sodium list-overflow point. The shadow stage must stay alive, and the stationary Nether-portal emissive light must remain stable under view-only rotation. Only after that should the visual Bliss scene-seed case be judged from reviewed screenshots.

The distant abrupt transition the tester reports is no longer treated as a cloud problem. The reversed-`smoothstep` candidate produced no visual improvement on hardware and was reverted in `c3a619e0429d683a19d5d80e1398ed899a158e22`, so no speculative cloud rewrite remains in production translation. Photon enables `BORDER_FOG` by default to hide the render-distance boundary, the failing run uses a 16-chunk view distance, and Vitrail's Iris-shaped horizon cone is also drawn at 256 blocks. The owning area to inspect is therefore terrain depth, reconstructed scene position, border fog and the terrain-to-sky/scene-seed handoff at that shared distance; `BORDER_FOG` is A/B'd only as a diagnostic, and cloud math is not patched from that symptom.

After that, collect the PHASE 17 screenshot and reference evidence needed to assign compatibility statuses to packs that reach full frames, and do not promote support from runtime logs alone.

The production Metal shader-pack gate remains conservative while PHASE 17 is incomplete. `phase17-compatibility.md` defines compatibility evidence; `VITRAIL_SMOKE.md` in the companion Metallum repository documents deterministic developer smoke launchers. Neither CI nor a smoke launcher alone changes the support claim.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
