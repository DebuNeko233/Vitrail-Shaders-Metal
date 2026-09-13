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

A dedicated Metallum branch and draft pull request implement the Minecraft 26.2 multi-render-target contract:

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
- backend-native colour mipmap generation through `MTLBlitCommandEncoder.generateMipmapsForTexture:` for eligible colour textures.

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

The second slice adds `mixin.metallum.MetalBackendMixin`, an optional `@Pseudo` mixin targeting Metallum's `com.metallum.render.MetalBackend` by name. This keeps Metallum off Vitrail's compile classpath and lets Vitrail still run when Metallum is absent.

The provider currently publishes exactly one Metal capability: `BufferBlending.serve(true)` after `MetalBackend#createDevice` returns successfully. This is justified by the MRT implementation itself: Metallum now configures pixel format, write mask, and blend state independently for each indexed color target. The provider deliberately does not infer any other Metal feature.

Publishing on successful return rather than method entry is important. Metallum's default backend order is Metal, then Vulkan, then OpenGL. If Metal device construction fails and Minecraft falls back to Vulkan, no Metal capability is left behind; the existing Vulkan provider then publishes the capabilities of the backend that actually won.

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

This is still only part of the backend boundary. Vulkan-only pipeline-cache handling, synchronization special cases, descriptor-set handling, geometry-stage implementation, and startup/backend selection still need explicit provider or capability boundaries before the port can be called backend-neutral.

## Validation status

The Metallum changes are **not yet considered runtime-complete**.

The fork currently has no confirmed successful build for this feature branch. The local execution environment used during this work also cannot reach GitHub or Maven repositories, so it cannot download the dependencies needed to substitute for CI.

Before the Metallum PR is ready to merge, it still needs:

1. a successful `./gradlew build` against Minecraft 26.2 and Sodium 0.9.2;
2. a macOS/Apple-Silicon MRT smoke test that writes distinct values to at least four targets and reads them back or visualizes them;
3. confirmation that a pass with an unused middle attachment slot preserves fragment-output locations;
4. confirmation that ordinary single-target vanilla/Sodium rendering is unchanged;
5. a colour-mipmap smoke test that samples non-zero LODs after a Metal-generated chain;
6. a regression check that unsupported shadow/depth mip generation cleanly stays on Vitrail's base-level fallback.

The Vitrail backend-neutral Sodium hook, optional Metal capability provider, colour-mipmap adapter, and dead-viewport cleanup are source-checked against Sodium tag `mc26.2-0.9.2`, the current Metallum MRT branch, Minecraft 26.2 `GpuFormat`, Metal's blit mipmap API, the Mixin soft-target contract, and the Vitrail commit history. They still need the repository build/CI path plus Vulkan and Metal runtime smoke coverage before the draft PR is ready.

## Next Vitrail work

Continue Phase 1 without yet treating Metal as a fully supported shader-pack backend. `HostReport.otherBackend()`, `PackScreens`, `GraphicsApiChoice`, `StartupGuard`, and the backend placeholder must continue to prevent the incomplete Metal path from being presented as finished until the remaining required capabilities are bridged and validated.

The next backend slices should isolate pipeline-cache lifecycle, descriptor/binding internals, synchronization special cases, and geometry-stage support, then establish a Metal implementation or conservative fallback for each. The existing Vitrail capability classes should remain the policy boundary: Vulkan and Metal providers publish facts, while shader-pack scheduling and render-target semantics stay in Vitrail.

Any new Minecraft, Sodium, Mixin, or Metallum API used by that bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
