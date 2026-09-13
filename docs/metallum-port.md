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
- raw SPIR-V storage-buffer and storage-image reflection;
- storage-texture zero clear and exact region-copy primitives;
- a general shader-pack compute bridge that compiles SPIR-V to MSL, owns `MTLComputePipelineState`, binds Minecraft facade resources by reflected name/binding, and dispatches exact workgroup counts.

This remains backend GPU behaviour. Shader-pack target selection, ping-pong/history, custom-image policy, camera reanchor policy, program scheduling and resource naming remain Vitrail responsibilities.

The current Metallum compute-foundation head is `eb0b9e1d52a49158f4f6565d09aed3d5e993ba9e`. GitHub Actions merge workflow `34765312322` completed successfully. This is compile validation only; Apple-Silicon runtime validation is still outstanding.

## Vitrail backend-neutralization

Phase 1 is active on branch `feat/backend-neutral-sodium-terrain-hook` and Draft PR #1, targeting `dev` as required by repository policy.

### Backend-neutral Sodium terrain binding

Sodium 0.9.2 calls `pass.setPipeline(...)` before `DrawContext#setContext(...)`. The former Vitrail Vulkan mixin injected `TerrainDraw.bind(...)` at the head of `VKDrawContext#setContext`; the current common wrapper performs the same bind immediately before delegating to the generic `DrawContext#setContext` invocation. The effective order is therefore unchanged: pipeline set, Vitrail resources bound, draw-context native state captured, then chunk draws.

### Optional Metal capability providers

Optional `@Pseudo` mixins bridge package-private Metallum classes without putting Metallum on Vitrail's common compile classpath. Current providers cover independent blending, mipmap generation, selective pipeline eviction, shader-storage-buffer allocation, writable storage-image allocation, storage-image clear/copy commands, and compute pipeline/dispatch.

Missing capabilities remain explicit narrow interfaces rather than backend-name guesses.

### Shader-storage buffers and images

`StorageBuffers` and `StorageImages` continue to own shader-pack declaration and lifetime policy. Vulkan keeps its direct VMA resource paths. A backend provider instead returns normal Minecraft facade resources.

For backend-neutral compute:

- `StorageBuffers.facadeSlice(name)` exposes a backend-owned SSBO strictly as `GpuBufferSlice`;
- `StorageImages.facadeView(name)` exposes a backend-owned storage image as `GpuTextureView`;
- `CustomImages.storage(name)` decides whether a custom-image name is the writable image uniform rather than a sampled alias. That answer comes from the pack declaration, not from whether a Vulkan-native handle exists.

No `VkBuffer`, `VkImageView`, `MTLBuffer`, `MTLTexture` or backend argument index crosses these facade paths.

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

The caller-side migration is now split into three backend-neutral pieces before `PackCompute` itself is routed:

1. `ComputeResources` reflects only the resource names actually present in an already-compiled SPIR-V module. It inventories uniform buffers, storage buffers, sampled images and storage images without rewriting any binding decoration or creating a native object.
2. `PackComputeBindings` resolves those names to Minecraft facade resources. It preserves the existing Vulkan policy order: custom-image resources and pack texture overrides first, then the selected ping-pong colour target, pass depth/distant/centre depth, engine textures, the stage default target and finally the existing black fallback.
3. `BackendComputePass` owns the non-Vulkan pass state: the uniform ring, opaque backend pipeline token, resource inventory and resolved dispatch. Existing Vulkan layout creation, push descriptors, barriers, `WideSamplerSets`, native pipeline and destruction stay in `PackCompute.Pass` and have not been moved into this class.

This split is deliberate: Vitrail decides what a resource name means; the backend decides how that already-resolved facade object is bound natively.

### Metal local-size and shared-memory refusal rules

Metal dispatch needs both workgroup counts and `threadsPerThreadgroup`, so the backend pass validates the actual shaderc-preprocessed compute text before compiling the native pipeline.

- `local_size_x` must be present and readable.
- `local_size_y` and `local_size_z` use GLSL's default of one only when the axis is genuinely omitted after preprocessing.
- An explicitly written axis whose value is still not a positive integer after preprocessing is refused rather than silently replaced with one.
- Shared/threadgroup declarations are sized from the same preprocessed text.
- A shared declaration that Vitrail cannot size is refused on native Metal.
- A declaration over the currently verified 32768-byte Metal threadgroup-memory limit is refused.
- The MoltenVK-only oversized-shared-memory rewrite is intentionally not reused on native Metal, because its single-workgroup storage-buffer substitution is a different policy with different memory semantics.

### Vulkan-specific code that stays Vulkan-specific

`WideSamplerSets` remains a MoltenVK/Vulkan push-descriptor workaround. Vulkan synchronization barriers and direct descriptor writes remain on the Vulkan branch. They are not translated into Metal operations; Metallum's render/blit/compute encoder transitions and `MTLFence` chain provide Metal ordering.

## Current validation status

Both repositories remain Draft and unmerged.

### Metallum

Head `eb0b9e1d52a49158f4f6565d09aed3d5e993ba9e` passed merge workflow `34765312322`.

### Vitrail

Before the caller-side helpers, compute capability/provider head `9ecf5e3bb020b1a30960e6856a2dee25b8ae4a1c` passed build `34765441999` and commit-policy `34765441998`; later documentation head `7382b5c99766a2b63da4fbc078b75315775b81f8` also passed both gates.

Caller-helper head `31183c792c537f973b4e23392dbd1b51e2207cb3` passed commit policy but build `34766668269` reached Java compilation and exposed one checked-exception mismatch: Minecraft 26.2's `IntermediaryShaderModule.createFromSpirv(...)` declares `ShaderCompileException`, while the new backend pass initially caught only `RuntimeException`. Commit `15a6d7c626a1a2e28508685a8426dd3333edf3b2` now catches the reflection/compile failure under the same `Exception` boundary as the established Vulkan path. The new head is not called compile-validated until its current build completes successfully.

## Runtime validation required before merge

Before either Draft PR becomes ready, the combined path still needs at least:

1. Apple-Silicon MRT output with distinct values in at least four targets;
2. an unused middle MRT slot preserving fragment-output location;
3. ordinary single-target vanilla/Sodium regression coverage;
4. Metal colour mipmap generation sampled at non-zero LOD;
5. safe fallback for unsupported shadow/depth mip chains;
6. entity mesh-layout transition proving stale Metal pipeline stride is rebuilt safely;
7. SSBO zero-at-birth plus shader write/read round trip without buffer-slot aliasing;
8. writable storage-image zero/write/read using a true 3D texture;
9. sampled alias of the same storage-image resource reading the expected contents;
10. scratch-based storage-volume camera reanchor;
11. shader-pack compute writing storage resources and a later render/compute stage consuming them after `PackCompute` is routed through the new backend seam;
12. Vulkan regression coverage for every shared path touched by backend-neutralization.

A green Gradle build is necessary but is not evidence that these rendering semantics are correct.

## Next work

Immediate work is:

- complete the build gate for the compute caller helpers;
- route `PackCompute` to `BackendComputePass` only when both `ComputeDeviceBackend` and `ComputeCommands` are present;
- leave the existing Vulkan branch and its barriers/descriptors unchanged;
- close backend passes through their backend-owned lifetime while retaining the existing Vulkan deferred destruction;
- then run Apple-Silicon compute smoke tests before changing any startup support gate.

Geometry-stage support and the remaining synchronization/startup boundaries follow. `HostReport.otherBackend()`, `PackScreens`, `GraphicsApiChoice`, `StartupGuard`, and the backend placeholder remain conservative until the required capabilities and Apple-Silicon validation are complete.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
