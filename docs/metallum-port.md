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
- exact storage-texture region copies through the Metal blit encoder;
- a general shader-pack compute bridge that compiles SPIR-V to MSL, owns `MTLComputePipelineState`, binds Minecraft facade resources by reflected name/binding, and dispatches exact workgroup counts.

This remains backend GPU behavior. Shader-pack concepts such as `colortex*`, draw-buffer routing, ping-pong/history, custom-image clear policy, camera reanchor policy, program scheduling and pack semantics remain Vitrail responsibilities.

The current Metallum compute-foundation head is `eb0b9e1d52a49158f4f6565d09aed3d5e993ba9e`. GitHub Actions merge workflow `34765312322` completed successfully, so the current MRT/storage/compute backend slice is compile-validated on the repository CI. Runtime validation is still outstanding.

## Vitrail backend-neutralization

Phase 1 is active on branch `feat/backend-neutral-sodium-terrain-hook` and draft PR `DebuNeko233/Vitrail-Shaders-Metal#1`, targeting `dev` as required by the repository branch policy.

### Backend-neutral Sodium terrain binding

Sodium 0.9.2 calls `DrawContext#setContext(RenderPass, RenderPipeline)` from `DefaultChunkRenderer` after choosing its concrete draw context. Metallum supplies a `MetalDrawContext` from the same common abstraction. Vitrail therefore hooks the common `DefaultChunkRenderer` invocation rather than the Vulkan-only `VKDrawContext` implementation.

The replacement must preserve the original Vulkan ordering: Sodium sets the pipeline first, then Vitrail binds pack resources, then chunk draws begin. The hook imports neither Vulkan nor Metallum classes. This ordering remains a required regression check before the port is called ready.

### Optional Metal capability providers

Optional `@Pseudo` mixins bridge package-private Metallum classes without putting Metallum on Vitrail's common compile classpath. The current providers cover independent blending, native mipmap generation, selective pipeline eviction, shader-storage-buffer allocation, writable storage-image allocation, storage-image clear/copy commands, and the new compute pipeline/dispatch capability.

Minecraft 26.2's public `DeviceInfo` and `DeviceFeatures` are still used where they describe real generic capabilities, but they do not expose every shader-pack fact Vitrail needs. Missing facts remain explicit narrow capabilities rather than backend-name guesses.

### Backend-native colour mipmaps

`MipmapCommands` is backend-neutral. Vulkan retains its explicit blit/barrier implementation; Metallum forwards to `MetalCommandEncoder.generateMipmaps(GpuTexture)` and uses the backend's existing encoder/fence transitions.

Metal currently refuses depth/stencil mip generation. Vitrail must keep the safe base-level fallback for shadow-depth chains until a correct depth-reduction path exists and has been runtime validated.

The old unused `vitrail$viewport(width, height)` command was removed after history inspection confirmed its only consumer disappeared with the old render-pass-per-mip fallback.

### Backend-neutral selective pipeline-cache eviction

`StalePipelines` separates Vitrail policy from backend action:

- Vitrail chooses which `RenderPipeline` keys are stale;
- Vulkan and Metal receive only a predicate;
- Vulkan keeps its existing worker adoption optimization;
- Metal evicts the matching compiled entries and lets the next compile rebuild them;
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

The storage-image resource path uses the same narrow-capability model instead of duplicating shader-pack policy in Metallum.

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
- `RenderPassMixin` substitutes a backend-owned storage-image view only when `StorageImages` has one for that name and still calls the original public bind operation.
- Metallum reflects the real storage-image resource kind from SPIR-V. The storage binding receives only a Metal texture argument; an optional sampled alias remains a sampled texture plus sampler.
- `StorageImageCommands` exposes zero clear and exact region copy. Vitrail decides when to invoke them; Metallum encodes the operation with its compute/blit encoders and existing `MTLFence` ordering.
- Camera reanchor uses two copies through a distinct scratch texture on both backends, so no backend is asked to define overlapping in-place texture-copy semantics.
- Birth preparation is transactional at the Vitrail level: `laidOut` is committed only after backend preparation succeeds. If preparation throws, Vitrail destroys the complete allocated set through the active backend lifetime path and forces a later attempt to allocate fresh resources.

### Backend-neutral compute seam

The first compute boundary is now present without changing the established Vulkan execution path yet.

`ComputeDeviceBackend` owns only native pipeline lifetime:

- `vitrail$compileCompute(label, spirv)` receives Vitrail's already-translated SPIR-V and returns an opaque backend-owned token;
- `vitrail$closeCompute(token)` returns that token to the backend for deferred native destruction.

`ComputeCommands` owns only one resolved dispatch:

- buffer bindings are supplied as `Map<String, GpuBufferSlice>`;
- texture bindings are supplied as `Map<String, GpuTextureView>`;
- sampler bindings are supplied as `Map<String, GpuSampler>`;
- the dispatch carries exact workgroup counts plus the shader's local workgroup size.

The optional Metallum provider is late-bound through `com.metallum.render.MetalComputeBridge`, so Vitrail still has no compile-time Metallum dependency. The opaque token is not a Metal pointer and Vitrail never observes `MTLComputePipelineState`, MSL source, native argument indices, `MTLBuffer`, or `MTLTexture` handles.

Metallum's implementation keeps native responsibilities on the backend side:

- SPIR-V to MSL conversion and compute entry-point resolution;
- reflection of uniform buffers, storage buffers, combined sampled images and storage images;
- `MTLComputePipelineState` creation and deferred release;
- compute buffer/texture/sampler argument binding;
- `dispatchThreadgroups:threadsPerThreadgroup:` for Vulkan-equivalent workgroup-count semantics;
- render/blit/compute ordering through the existing encoder-ending `MTLFence` chain rather than Vulkan barriers.

The distinction between `dispatchThreadgroups` and `dispatchThreads` is deliberate. Vitrail's current Vulkan `vkCmdDispatch(groupsX, groupsY, groupsZ)` passes workgroup counts, so feeding those values to Metal's arbitrary-thread-grid `dispatchThreads` would execute the wrong grid. The Metal bridge therefore takes both the workgroup counts and local size explicitly.

This seam is not yet the end-to-end switch. `PackCompute` still contains the current Vulkan layout, push-descriptor, barrier, dispatch and destruction implementation. The next code step is to keep that Vulkan branch unchanged while adding a Metal branch that resolves the same names and target halves into the new facade maps and then invokes the capability above.

`WideSamplerSets` remains a Vulkan/MoltenVK push-descriptor workaround and is not a Metal feature to port.

### Compute limitations still explicit

The current Metal bridge intentionally claims only the resource classes already reflected and bound correctly: uniform buffers, storage buffers, combined sampled images and storage images. It does not silently claim support for every possible SPIR-V resource class.

The old MoltenVK-specific oversized `shared`-memory rewrite also has not been generalized to native Metal. If a pack's compute kernel exceeds native Metal threadgroup-memory limits, that must be handled by a separately verified policy or refused explicitly; the backend must not guess a semantically different substitute.

## Current validation status

The two repositories are still Draft and unmerged.

### Metallum

Current head `eb0b9e1d52a49158f4f6565d09aed3d5e993ba9e` passed GitHub Actions merge workflow `34765312322`. This includes the general compute bridge, Metal compute buffer/sampler binding, and workgroup-count dispatch additions.

This is compile validation only. Apple-Silicon runtime validation of MRT, mipmaps, SSBO/storage-image behavior and general compute remains required.

### Vitrail

Before the new compute seam, head `1ce612755028a470aa5a1625c35d36c444fb0eeb` had a green build and commit-policy gate. The compute capability/provider head `9ecf5e3bb020b1a30960e6856a2dee25b8ae4a1c` has a successful commit-policy workflow; its build workflow `34765441999` is still running at this documentation checkpoint and must not be called compile-validated until it completes successfully.

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
11. custom shader-pack compute writing storage resources and a later render/compute stage consuming the result after `PackCompute` is routed through the new backend seam;
12. Vulkan regression coverage for every shared path touched by the backend-neutralization.

A green Gradle build is necessary but is not evidence that these rendering semantics are correct.

## Next work

Immediate work is now:

- finish the current Vitrail compute-seam build gate;
- refactor `PackCompute` so Vitrail continues to own pack resource-name resolution, ping-pong target selection, dispatch sizing and scheduling while Vulkan retains its existing native branch and Metal receives facade resources through `ComputeDeviceBackend` / `ComputeCommands`;
- keep `WideSamplerSets` and Vulkan barrier code on the Vulkan branch only;
- explicitly handle or reject native-Metal shared/threadgroup-memory cases that exceed verified limits;
- then run Apple-Silicon compute smoke tests before changing any startup support gate.

Geometry-stage support and the remaining synchronization/startup boundaries follow. `HostReport.otherBackend()`, `PackScreens`, `GraphicsApiChoice`, `StartupGuard`, and the backend placeholder must remain conservative until the required capabilities and Apple-Silicon validation are complete.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by the next bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
