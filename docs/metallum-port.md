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
- backend-native colour mipmap generation;
- selective compiled-pipeline eviction with deferred native release;
- backend-owned zero-initialized shader-storage buffers;
- backend-owned writable 1D, 2D and true-3D textures;
- an optional bridge that creates an otherwise ordinary Minecraft texture with Metal `ShaderWrite` usage for writable colour targets;
- raw SPIR-V storage-buffer and storage-image reflection;
- storage-texture zero clear and exact region-copy primitives;
- a general shader-pack compute bridge that compiles SPIR-V to MSL, owns `MTLComputePipelineState`, binds Minecraft facade resources by reflected name/binding, and dispatches exact workgroup counts;
- background-safe ordinary render-pipeline precompilation for Vitrail family warm-up.

This remains backend GPU behaviour. Shader-pack target selection, ping-pong/history, custom-image policy, camera reanchor policy, program scheduling and resource naming remain Vitrail responsibilities.

At this status update the companion Metallum branch head is `54ff6f0e22b153ea206cc726f68bccaee6ce4e70`. The Metal foundation and baseline PHASE 5-16 acceptance already have Apple-Silicon real-device evidence; PHASE 17 real-pack compatibility remains active and may still expose generic defects that must be fixed in their owning subsystem.

## Vitrail backend-neutralization

The backend-neutralization baseline is implemented on branch `feat/backend-neutral-sodium-terrain-hook` and Draft PR #1, targeting `dev` as required by repository policy. Current branch head is `54f454bc2e4d45c9c8d72faad4a9cc15ef8bed31`.

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

### Entity attribute compatibility

The extended entity mesh already carries the four identifier lanes backing Iris's `iris_Entity` attribute. Vitrail answers pack-facing `mc_Entity` from that same backing storage, with explicit conversion to the type declared by the shader, rather than treating it as an unavailable constant. This does not add another vertex element or change the entity stride.

The remaining entity-shadow `at_midBlock` warning must **not** be fixed by extending that stride. Iris 26.1 defines `at_midBlock` on `IrisVertexFormats.TERRAIN`; `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but no `at_midBlock`, and Iris shader keys use `ENTITY` for both ordinary and shadow entities. Vitrail therefore needs reference-parity default/diagnostic handling for an entity shader that asks for `at_midBlock`, not a Vitrail-only entity ABI.

The same source-first rule applies to the remaining sky and particle/weather attribute diagnostics: establish the exact Iris format and translation/default behavior before changing a mesh format or suppressing a warning.

### Vulkan-specific code that stays Vulkan-specific

`WideSamplerSets` remains a MoltenVK/Vulkan push-descriptor workaround. Vulkan synchronization barriers and direct descriptor writes remain on the Vulkan branch. They are not translated into Metal operations; Metallum's render/blit/compute encoder transitions and `MTLFence` chain provide Metal ordering.

## Current validation status

Both repositories remain Draft, open and unmerged. The Metal shader-pack route remains developer-validation-only.

Current branch heads at this documentation update are:

- Vitrail `54f454bc2e4d45c9c8d72faad4a9cc15ef8bed31`;
- Metallum `54ff6f0e22b153ea206cc726f68bccaee6ce4e70`.

At those branch baselines:

- PHASE 2 foundation: **CLOSED / Real-device Verified**;
- PHASE 5-15 baseline acceptance: **CLOSED / Real-device Verified**;
- PHASE 16 Advanced Features baseline acceptance: **CLOSED / Real-device Verified** on Apple M5 Pro / macOS 27 / Metal;
- PHASE 17 Real Shader Pack Compatibility: **active**.

"Closed" here means the phase's acceptance baseline has real-device evidence. It does not mean that a later real pack cannot expose a generic defect in that subsystem; PHASE 17 findings continue to be fixed at the owning shader-pack contract, Minecraft contract or Metal capability.

### 2026-09-18 five-pack runtime evidence

A single Apple M5 Pro / macOS 27.0 / Metal client log runs Bliss v2.1.2, Complementary Reimagined r5.9.1, MakeUp Ultra Fast 9.5e, Photon v1.3b and Solas Shader V3.7b. The Vitrail module-cache path records build `54f454bc`; the mod list records Metallum 0.0.24. The log itself does not print a Metallum commit SHA, so a formal PHASE 17 evidence bundle should continue to carry the exact companion head separately.

For the observed session:

- every tested pack reaches a first full frame and the client later reaches clean `Stopping!`;
- the initial Bliss warm-up reports 62/62 leftover pipelines, and the later pack loads report 155/155;
- no Vitrail/Metallum shader compile `ERROR` occurs;
- the only log-level `ERROR` is an invalid/missing PNG header from the enabled Prime's HD resource pack;
- Photon, MakeUp and Complementary no longer attempt unusable no-DH `distant*` programs during detached warm-up;
- Bliss no longer hits the `diagonal3` macro-redefinition failure;
- Photon compiles past the previously recorded `world0/prepare/vertex` blocker, reaches its chain, compiles and dispatches `world0/deferred4_a` on Metal;
- Solas compiles and dispatches `shadowcomp` on Metal as groups `(24, 12, 24)` / local `(8, 8, 8)`.

This materially advances the old Photon blocker and closes the previously unverified compute-dispatch half for that observed path. It does **not** by itself promote Photon or any other pack to a new PHASE 17 compatibility status: the status policy still requires reviewed screenshot/reference evidence, not only a clean runtime log.

The session still contains warnings that need source/reference classification rather than blanket suppression. Important groups include entity `at_midBlock`, sky `mc_midTexCoord` / `mc_Entity`, particle/weather extended inputs, comparison-vs-ordinary shadow sampler declarations, and first-frame `nothing fills them yet` resource diagnostics. The entity `at_midBlock` case is already source-classified as a reference-format/default-input issue rather than a missing entity vertex element; the other groups remain separate audits.

## Acceptance required before merge

PHASE 17 remains the merge gate. Before either Draft PR becomes ready, real-pack evidence must be collected and reviewed under [`phase17-compatibility.md`](phase17-compatibility.md). In particular:

1. turn the current five-pack runtime progress into reviewed pack evidence only where the required screenshot/reference material exists;
2. preserve the real Photon compute-dispatch evidence and exact paired-head metadata in the formal evidence path;
3. audit the remaining vertex/sampler/resource diagnostics against Iris before changing contracts or treating warning counts as compatibility scores;
4. continue the required real-pack matrix, including pack families not covered by the current five-pack session;
5. keep Vulkan/shared-path regression coverage for every backend-neutral contract changed while fixing PHASE 17 findings.

A green Gradle build, successful client launch, full warm-up count or successful compute dispatch is useful evidence, but none alone proves rendering correctness or shader-pack compatibility.

## Next work

Immediate work is to correct the entity `at_midBlock` diagnostic/reference-default classification without changing entity ABI, then source-audit the remaining sky and particle/weather attribute warnings. Comparison/ordinary shadow sampler conflicts and first-frame empty-resource diagnostics should remain explicit until reference behavior or visual evidence identifies a concrete contract to change.

In parallel, collect the PHASE 17 screenshot/reference evidence needed to assign compatibility statuses to the packs that now reach full frames. Do not promote support from this runtime log alone.

The production Metal shader-pack gate remains conservative while PHASE 17 is incomplete. `phase17-compatibility.md` defines compatibility evidence; `VITRAIL_SMOKE.md` in the companion Metallum repository documents deterministic developer smoke launchers. Neither CI nor a smoke launcher alone changes the support claim.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
