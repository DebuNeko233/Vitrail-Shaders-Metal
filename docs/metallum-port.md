# Metallum / Metal backend port status

This page tracks the backend work needed to run Vitrail shader packs through Metallum on macOS. The detailed roadmap and repository rules live in [`AGENTS.md`](../AGENTS.md); this page records what has actually been implemented and what is still only planned.

## Version baseline

The active compatibility baseline is:

- Minecraft Java Edition 26.2
- Java 25
- Sodium 0.9.2 stable for Minecraft 26.2
- Vitrail uses the CaffeineMC Maven artifact `net.caffeinemc:sodium-fabric:0.9.2+mc26.2`
- Metallum uses the Modrinth release artifact form `mc26.2-0.9.2-fabric`

The two Sodium strings are intentionally different because the two repositories resolve Sodium from different Maven coordinates.

## Metallum foundation

A dedicated Metallum branch and draft pull request implement the Minecraft 26.2 multi-render-target contract and the backend primitives Vitrail needs:

- repository: `DebuNeko233/metallum`
- branch: `feat/mc26.2-mrt-foundation`
- draft PR: `DebuNeko233/metallum#1`

The implementation preserves the indexed nullable color-attachment model exposed by Minecraft 26.2. A render pass such as `[RT0, unused, RT2]` therefore keeps RT2 at attachment index 2 instead of compacting it to index 1.

The draft currently covers:

- up to eight indexed Metal color attachments;
- `RenderPassDescriptor.colorAttachments()` including unused/null slots;
- `RenderPipeline.getColorTargetStates()` including unused/null slots;
- per-target Metal pixel format, write mask, and blend state;
- per-target clear values;
- full attachment-set identity when deciding whether a Metal render encoder can be reused;
- common attachment extent validation;
- depth-only render-pass sizing;
- Sodium 0.9.2 stable alignment in Metallum;
- backend-native colour mipmap generation through `MTLBlitCommandEncoder.generateMipmapsForTexture:` for eligible colour textures;
- selective pipeline-cache eviction with deferred native-pipeline release at the next safe full cache clear;
- backend-owned, zero-initialized shader-storage buffers and raw SPIR-V storage-buffer reflection.

The mipmap path deliberately refuses depth/stencil textures. Vitrail's shadow-depth mip chain therefore still falls back to level zero on Metal until a correct depth reduction path is implemented and validated.

This is deliberately backend-only work. `colortex*` naming, `DRAWBUFFERS`, ping-pong/history, depth-copy meaning, shader-pack program routing, and frame scheduling remain Vitrail responsibilities.

## Vitrail backend-neutralization

Phase 1 is now active on branch `feat/backend-neutral-sodium-terrain-hook` and draft PR `DebuNeko233/Vitrail-Shaders-Metal#1`, targeting the repository's `dev` integration branch as required by its branch policy.

### Backend-neutral Sodium terrain binding

The first completed slice removes a concrete Vulkan dependency from the Sodium terrain integration. Sodium 0.9.2 calls `DrawContext#setContext(RenderPass, RenderPipeline)` from `DefaultChunkRenderer` after selecting a concrete draw context. Metallum hooks `DrawContext.create()` and supplies `MetalDrawContext` when the active Minecraft device reports the Metal backend. Vitrail therefore now binds `TerrainDraw` resources at the common `DefaultChunkRenderer` call site instead of mixing into `VKDrawContext`.

Concretely:

- `sodium.VKDrawContextMixin` is no longer registered and its source file has been removed;
- `sodium.MixinDefaultChunkRenderer` wraps the common `DrawContext#setContext` invocation;
- the existing Vulkan ordering is preserved: the pack resources are bound after Sodium has set the render pipeline and before chunk draws begin;
- the hook imports neither Metallum classes nor Vulkan classes and therefore also reaches Metallum's Metal draw context;
- MRT descriptor ownership remains in Vitrail's existing terrain pass wrapper.

### Optional Metal capability provider

The second slice adds optional `@Pseudo` providers for the package-private Metallum backend classes. This keeps Metallum off Vitrail's compile classpath and lets Vitrail still run when Metallum is absent.

`mixin.metallum.MetalBackendMixin` publishes `BufferBlending.serve(true)` only after `MetalBackend#createDevice` returns successfully. This is justified by the MRT implementation itself: Metallum configures pixel format, write mask, and blend state independently for each indexed color target. If Metal device construction fails and Minecraft falls back to Vulkan, no Metal capability is left behind and the existing Vulkan provider publishes the capabilities of the backend that actually won.

`mixin.metallum.MetalDeviceMixin` now carries two backend commands without exposing a Metal native type: selective pipeline eviction and shader-storage buffer allocation. The latter crosses the seam as Minecraft's own `GpuBuffer`, not an `MTLBuffer` handle.

Minecraft 26.2's public `DeviceInfo`/`DeviceFeatures` API remains useful for backend identity and generic draw capabilities, but it does not expose several shader-pack facts Vitrail needs, including independent per-target blending and geometry-shader availability. Those facts therefore remain explicit Vitrail-owned capabilities supplied by thin backend providers instead of being guessed from backend names.

### Backend-native colour mipmaps

The next completed slice bridges Vitrail's `MipmapCommands` capability to Metallum without importing Metallum classes into common rendering code.

- Metallum exposes `MetalCommandEncoder.generateMipmaps(GpuTexture)` as backend behavior.
- Its implementation materializes pending clears, ends the current encoder as needed, opens a Metal blit encoder, waits on the existing `MTLFence`, records `generateMipmapsForTexture:`, updates the fence when the blit encoder ends, and then returns to the normal command stream.
- `mixin.metallum.MetalCommandEncoderMixin` is an optional `@Pseudo` soft target for the package-private Metal command encoder and forwards `vitrail$generateMipmaps` to that backend method.
- Metallum currently accepts only textures whose Minecraft 26.2 `GpuFormat` reports a colour aspect. Depth/stencil mip chains remain unsupported on Metal.
- Vulkan keeps its existing explicit blit/barrier implementation unchanged.

The Mixin usage follows the upstream soft-target contract: `@Pseudo` permits targets that are absent at compile time/runtime, and `@Mixin(targets = "...")` is the supported form for package-private or unavailable targets. This keeps the Vitrail/Metallum dependency direction one-way at runtime instead of turning Vitrail common into a Metallum-linked module.

### Removed dead viewport capability

`MipmapCommands` briefly also carried a backend-private `vitrail$viewport(width, height)` hook. History inspection traced that method to commit `9716523`, which simultaneously removed the old render-pass-per-mip fallback that had needed corrected per-level viewport extents. The same commit added only the interface declaration and Vulkan implementation; no surviving caller uses the hook after the fallback removal.

The dead method has therefore been removed from `MipmapCommands`, `VulkanCommandEncoderMixin`, and the Metal adapter instead of inventing a Metal implementation for an operation Vitrail no longer requests. The PR build is the compile-time guard against a missed Java reference; any surviving caller would fail compilation immediately.

### Backend-neutral selective pipeline-cache eviction

The entity-mesh transition exposed another Vulkan leak in what looked like a capability interface. `StalePipelines` previously imported `VulkanRenderPipeline`, chose the game's entity pipelines inside `VulkanDeviceMixin`, and mixed selective invalidation with the Vulkan-only background warm-up adoption path.

The boundary is now split by responsibility without changing the existing `EntityMesh` call site:

- `StalePipelines.vitrail$dropPipelines(Predicate<RenderPipeline>)` is the backend command. A backend receives only a predicate and returns the keys it actually removed.
- `StalePipelines.vitrail$dropEntityPipelines()` is Vitrail policy. It builds that predicate from each pipeline's declared vertex bindings and `DefaultVertexFormat.ENTITY`, deliberately reading the declaration rather than Vitrail's rewritten getter.
- The optional adoption method now accepts Mojang's backend-neutral `CompiledRenderPipeline` and defaults to `false`. The Vulkan provider recognizes `VulkanRenderPipeline` and keeps the existing worker optimization; Metal does not imitate that optimization and safely falls back to its normal first-draw compile.
- `VulkanDeviceMixin` keeps the old compiled Vulkan pipelines alive in its set-aside list until the next full cache purge, exactly as before.
- `mixin.metallum.MetalDeviceMixin` forwards only the predicate to Metallum's `MetalDevice.evictCachedPipelines(...)`.
- Metallum removes matching keys immediately but retains their `MetalCompiledRenderPipeline` values until `clearPipelineCache()`. That method already waits for submitted GPU work before releasing native pipelines, so an already-recorded frame cannot lose a pipeline underneath it.

This Metal invalidation is required by source, not by analogy alone. `MetalCompiledRenderPipeline.buildVertexDescriptor(...)` copies `VertexFormat.getVertexSize()` into the Metal vertex-buffer layout stride when the native pipeline is compiled. Because `MetalDevice` caches compiled pipelines by `RenderPipeline` identity, a live mesh-layout change must invalidate the matching compiled entries or the old stride can survive under the same Java pipeline key.

Minecraft 26.2's public `GpuDeviceBackend` exposes `precompilePipeline(...)` and full `clearPipelineCache()`, but no selective cache invalidation operation. The selective operation therefore remains a thin backend extension while the decision about which keys are stale remains in Vitrail.

### Shader-storage buffers

The next slice removes the first large Vulkan-native resource allocation from shader-pack semantics without pretending Minecraft 26.2 has a storage-buffer API that it does not have.

Minecraft 26.2's `GpuBuffer` usage flags stop at mapped, copy, vertex, index, uniform and texel-buffer usage; there is no storage-buffer flag. Vitrail therefore defines one narrow capability, `StorageBufferBackend.vitrail$createStorageBuffer(long)`, for the missing allocation operation only.

The bind path remains public Minecraft API:

- Vitrail still places each shader-storage name in the bind-group layout as a placeholder uniform because the 26.2 layout API has no storage-buffer entry type.
- `StorageBuffers.ensure(...)` uses `StorageBufferBackend` when the active backend provides it. Metallum returns a zero-initialized backend-owned `GpuBuffer`; no Metal native handle enters Vitrail.
- `StorageBuffers.bind(...)` hands that whole buffer to `RenderPass.setUniform(name, buffer)`. Minecraft 26.2 checks slice alignment on the slice overload but does not require a uniform usage bit before forwarding a buffer to the backend.
- Metallum reflects `SPVC_RESOURCE_TYPE_STORAGE_BUFFER` from the raw SPIR-V, reuses the same binding index as the placeholder entry, and classifies that resource as `STORAGE_BUFFER` rather than `UNIFORM_BUFFER` when it builds its Metal resource table.
- Metal therefore binds the resource through its ordinary vertex/fragment buffer argument path. There is no Metal descriptor-set emulation.
- Vitrail's existing Vulkan/VMA path remains unchanged: it allocates `VK_BUFFER_USAGE_STORAGE_BUFFER_BIT`, binds the 16-byte dummy uniform, and the Vulkan descriptor mixins replace the native handle, range and descriptor type.

The shared binding index is important because `IntermediaryShaderModuleMixin` already appends SPIR-V storage buffers to the vanilla module's uniform-buffer reflection so Minecraft's Vulkan rebind can see them. Metallum's additional raw-SPIR-V reflection identifies the real resource kind but does not create a duplicate argument slot. Its storage-buffer and uniform-buffer indices are both included when choosing the first free Metal vertex-buffer index because all three use Metal's `buffer(index)` namespace.

Storage images are intentionally still unsupported by this slice. They require `MTLTextureUsageShaderWrite`, correct 3D texture construction, storage-image reflection/binding and a backend-native clear implementation that preserves Vitrail's existing clear-at-birth, per-shadow-stage clear and camera-following volume semantics. `WideSamplerSets` remains a Vulkan/MoltenVK push-descriptor workaround and is not a Metal requirement.

This is still only part of the backend boundary. Vulkan-only synchronization special cases, storage-image handling, geometry-stage implementation, and startup/backend selection still need explicit provider or capability boundaries before the port can be called backend-neutral.

## Validation status

Both sides of the current SSBO slice are compile-validated.

- Vitrail draft PR #1 head `85cc7199deb6433e892bef9889b72f5ba670e1db` completed both the `commits` and full Gradle `build` workflows successfully. That includes `StorageBufferBackend`, the Metal soft bridge, the backend-owned allocation path, the preserved Vulkan fallback, text checks, javac warnings-as-errors and doclint.
- Metallum draft PR #1 head `eafb8c5df105a15b749a165cb8044367e28097f3` completed GitHub Actions run `34757903523` successfully with Java 25 and `./gradlew build`. The preceding run exposed an invalid JSpecify type-use on the nested MRT attachment type; that was corrected before this successful run.

This is compile validation, not runtime validation. No Apple-Silicon SSBO/MRT/mipmap smoke result has been recorded yet, and the Metal backend must not be advertised as complete on the strength of a green build alone.

Before the Metallum PR is ready to merge, it still needs:

1. a macOS/Apple-Silicon MRT smoke test that writes distinct values to at least four targets and reads them back or visualizes them;
2. confirmation that a pass with an unused middle attachment slot preserves fragment-output locations;
3. confirmation that ordinary single-target vanilla/Sodium rendering is unchanged;
4. a colour-mipmap smoke test that samples non-zero LODs after a Metal-generated chain;
5. a regression check that unsupported shadow/depth mip generation cleanly stays on Vitrail's base-level fallback;
6. an entity-mesh transition smoke test proving that the Metal pipeline cache recompiles the changed stride and does not release the evicted native pipeline before the safe full-cache purge;
7. an SSBO smoke test that starts from known zero contents, writes through a shader, and reads a nontrivial range back through a later shader stage without aliasing a vertex/uniform argument slot.

Storage-image support needs its own compile/runtime validation after it is implemented and is not implied by the SSBO work.

## Next Vitrail work

Continue Phase 1 without yet treating Metal as a fully supported shader-pack backend. `HostReport.otherBackend()`, `PackScreens`, `GraphicsApiChoice`, `StartupGuard`, and the backend placeholder must continue to prevent the incomplete Metal path from being presented as finished until the remaining required capabilities are bridged and validated.

The next backend slice should be storage images: Metal 3D texture construction, shader-read/write usage, storage-image reflection and a correct native clear path. Geometry-stage handling and the remaining synchronization/startup boundaries follow after that. The existing Vitrail capability classes should remain the policy boundary: Vulkan and Metal providers publish facts, while shader-pack scheduling and render-target semantics stay in Vitrail.

Any new Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross or Metal API used by that bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
