# Metallum / Metal backend port status

This page tracks the backend work needed to run Vitrail shader packs through Metallum on macOS. The detailed roadmap and repository rules live in [`AGENTS.md`](../AGENTS.md); this page records what has actually been implemented and what is still only planned.

## Version baseline

The active compatibility baseline is:

- Minecraft Java Edition 26.2
- Java 25
- Sodium 0.9.2 stable for Minecraft 26.2
- Vitrail uses the CaffeineMC Maven artifact `net.caffeinemc:sodium-fabric:0.9.2+mc26.2`
- Metallum uses the Modrinth release artifact form `mc26.2-0.9.2-fabric`

The two Sodium strings are intentionally different because the repositories resolve Sodium from different Maven coordinates.

## Metallum foundation

The companion backend work lives in:

- repository: `DebuNeko233/metallum`
- branch: `feat/mc26.2-mrt-foundation`
- draft PR: `DebuNeko233/metallum#1`

The current backend foundation includes:

- up to eight indexed Metal colour attachments, preserving unused/null slots;
- complete indexed `RenderPipeline.getColorTargetStates()` handling;
- per-target Metal format, write mask, blend state and clear value;
- attachment-set identity when deciding whether a render encoder can be reused;
- common attachment extent validation and depth-only pass sizing;
- backend-native colour mipmap generation with Metal blit commands;
- selective compiled-pipeline eviction with deferred native-pipeline release;
- backend-owned zero-initialized shader-storage buffers and raw SPIR-V storage-buffer reflection;
- backend-owned writable 1D, 2D and true-3D textures;
- raw SPIR-V storage-image reflection and texture-only storage binding;
- typed storage-texture zero clears through Metal compute kernels;
- exact storage-texture region copies through the Metal blit encoder.

This remains backend GPU behavior. Shader-pack concepts such as `colortex*`, draw-buffer routing, ping-pong/history, custom-image clear policy, camera reanchor policy, program scheduling and pack semantics remain Vitrail responsibilities.

The latest compile-validated Metallum code head for storage-image render binding is `881c4337426fe2dd88b08bcad2d029a00bfa3d71`; GitHub Actions run `34761479404` completed `./gradlew build` successfully with Java 25. Documentation commits follow that code head.

## Vitrail backend-neutralization

Phase 1 is active on branch `feat/backend-neutral-sodium-terrain-hook` and draft PR `DebuNeko233/Vitrail-Shaders-Metal#1`, targeting `dev` as required by the repository branch policy.

### Backend-neutral Sodium terrain binding

Sodium 0.9.2 calls `DrawContext#setContext(RenderPass, RenderPipeline)` from `DefaultChunkRenderer` after choosing its concrete draw context. Metallum supplies a `MetalDrawContext` from the same common abstraction. Vitrail therefore hooks the common `DefaultChunkRenderer` invocation rather than the Vulkan-only `VKDrawContext` implementation.

The replacement preserves the original Vulkan ordering: Sodium sets the pipeline first, then Vitrail binds pack resources, then chunk draws begin. The hook imports neither Vulkan nor Metallum classes.

### Optional Metal capability providers

Optional `@Pseudo` mixins bridge package-private Metallum classes without putting Metallum on Vitrail's compile classpath. The current providers cover independent blending, native mipmap generation, selective pipeline eviction, shader-storage-buffer allocation, writable storage-image allocation, and storage-image clear/copy commands.

Minecraft 26.2's public `DeviceInfo` and `DeviceFeatures` are still used where they describe real generic capabilities, but they do not expose every shader-pack fact Vitrail needs. Missing facts remain explicit narrow capabilities rather than backend-name guesses.

### Backend-native colour mipmaps

`MipmapCommands` is backend-neutral. Vulkan retains its explicit blit/barrier implementation; Metallum forwards to `MetalCommandEncoder.generateMipmaps(GpuTexture)` and uses the backend's existing encoder/fence transitions.

Metal currently refuses depth/stencil mip generation. Vitrail must keep the safe base-level fallback for shadow-depth chains until a correct depth-reduction path exists and has been runtime validated.

The old unused `vitrail$viewport(width, height)` command was removed after history inspection confirmed its only consumer disappeared with the old render-pass-per-mip fallback.

### Backend-neutral selective pipeline-cache eviction

`StalePipelines` now separates Vitrail policy from backend action:

- Vitrail chooses which `RenderPipeline` keys are stale;
- Vulkan and Metal receive only a predicate;
- Vulkan keeps its existing worker adoption optimization;
- Metal simply evicts the matching compiled entries and lets the next compile rebuild them;
- old native pipelines remain alive until a safe full cache purge.

The Metal invalidation is required because `MetalCompiledRenderPipeline` bakes each vertex binding's stride into the compiled `MTLVertexDescriptor`; a live mesh-layout change cannot safely retain the old compiled pipeline under the same Java key.

### Shader-storage buffers

Minecraft 26.2 has no storage-buffer usage flag or public storage-buffer bind-group entry type. Vitrail therefore exposes only the missing allocation capability.

On Metal:

- `StorageBuffers` asks the backend for a zero-initialized backend-owned `GpuBuffer`;
- the resource is passed to public `RenderPass.setUniform(...)` under the existing placeholder name;
- Metallum reflects `SPVC_RESOURCE_TYPE_STORAGE_BUFFER` from raw SPIR-V and classifies that shared binding index as `STORAGE_BUFFER`;
- Metal binds it through the ordinary buffer argument namespace.

On Vulkan the existing direct VMA allocation, dummy facade binding and native descriptor replacement remain unchanged.

### Shader-storage images

The storage-image resource path is now wired through the same narrow-capability model instead of duplicating shader-pack policy in Metallum.

`StorageImages` remains the one owner of pack semantics:

- absolute versus screen-relative sizing;
- clear-at-birth selection and its allocation-pass budget;
- images marked for a clear at the head of the shadow stage;
- movable-volume detection;
- whether a scratch image is required;
- the exact source/destination region used when a camera-following volume is reanchored.

The backend boundary contains only resource and command facts:

- Vulkan keeps the existing direct VMA images, native image views, `GENERAL` layout handling, transfer barriers and descriptor replacement.
- A backend implementing `StorageImageBackend` may instead return a real Minecraft `GpuTexture`. Vitrail creates a normal `GpuTextureView` through `GpuDevice.createTextureView(...)` and never carries an `MTLTexture` handle or Metal argument index.
- `StorageImages` stores the backend-owned view under both the image uniform name and the optional sampler alias while retaining the existing Vulkan-native `Bound` map separately.
- `RenderPassMixin` wraps the public `RenderPassBackend.bindTexture(...)` call. It substitutes a backend-owned storage-image view only when `StorageImages` has one for that name, and always calls the original operation so existing wrappers and the particle tail hook retain their ordering.
- Metallum reflects the real storage-image resource kind from SPIR-V. The storage binding receives only a Metal texture argument; it does not receive sampler state. An optional sampled alias remains an ordinary sampled texture plus sampler.
- `StorageImageCommands` exposes zero clear and exact region copy. Vitrail decides when to invoke them; Metallum encodes the operation with its compute/blit encoders and existing `MTLFence` ordering.
- Camera reanchor uses two copies through a distinct scratch texture on both backends, so no backend is asked to define overlapping in-place texture-copy semantics.

This is a resource/lifecycle bridge, not proof that custom shader-pack compute programs run on Metal.

### Remaining compute boundary

Vitrail's custom `ComputeShader` path is still Vulkan-specific. It currently accepts `VulkanDevice`, constructs `VulkanBindGroupLayout.Entry` objects, and produces a Vulkan shader module. Therefore a pack that depends on `shadowcomp` or another custom compute stage cannot yet exercise the new Metal storage resources end-to-end merely because allocation, render binding, clear and copy now exist.

The next hard Phase 1 boundary is a narrow backend-neutral compute compile/dispatch capability. Vitrail must retain program scheduling, SPIR-V resource names and shader-pack semantics; Metallum should own SPIR-V-to-MSL translation, `MTLComputePipelineState`, Metal argument binding, encoder transitions and native object lifetime. The Vulkan provider must keep the current behavior unchanged.

`WideSamplerSets` remains a Vulkan/MoltenVK push-descriptor workaround and is not a Metal feature to port.

## Current validation status

The two repositories are still Draft and unmerged.

### Metallum

Storage-image reflection and render binding are compile-validated at code head `881c4337426fe2dd88b08bcad2d029a00bfa3d71` by GitHub Actions run `34761479404` on Java 25.

The storage-texture primitives still require Apple-Silicon runtime validation. In addition, the cached storage-zero compute pipelines have an explicit `MTLStorageTexturePipelines.close()` and that teardown still needs to be connected to `MetalDevice.close()` before the lifecycle slice is considered complete.

### Vitrail

Build run #33 reached Java compilation and exposed two incorrect LWJGL single-structure allocations in `GpuRecording`: `VkDependencyInfo.calloc(1, stack)` returns a buffer, not a single `VkDependencyInfo`. Code head `0592b2b0097f5deeeca1250de4cbbc37701dc579` corrected both sites to the single-structure allocator. Later documentation and repository-memory commits follow that fix.

At this documentation checkpoint, the latest full Gradle build for the current branch head has not yet completed successfully, so the storage-image Vitrail slice must not be described as compile-validated yet.

A code review also identified one state-machine cleanup still to make: `StorageImages.newlyBorn()` currently marks an allocation prepared before backend birth preparation has succeeded. The state should be committed only after the backend preparation path completes so an explicit provider refusal cannot leave a failed allocation looking initialized.

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
11. custom shader-pack compute writing storage resources and a later render/compute stage consuming the result once the compute bridge exists;
12. Vulkan regression coverage for every shared path touched by the backend-neutralization.

A green Gradle build is necessary but is not evidence that these rendering semantics are correct.

## Next work

The immediate closure tasks for the storage-image slice are:

- complete a green Vitrail build on the final code head;
- connect `MTLStorageTexturePipelines.close()` to Metal device teardown;
- commit `laidOut` only after storage-image birth preparation succeeds;
- refresh the repository-memory checkpoint with the final build/run identifiers.

After those are closed, continue with the backend-neutral shader-pack compute boundary. Geometry-stage support and the remaining synchronization/startup boundaries follow. `HostReport.otherBackend()`, `PackScreens`, `GraphicsApiChoice`, `StartupGuard`, and the backend placeholder must remain conservative until the required capabilities and Apple-Silicon validation are complete.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
