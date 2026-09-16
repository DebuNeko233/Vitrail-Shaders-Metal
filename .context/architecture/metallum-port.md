# Metallum Port Mental Model

## Responsibility split

The durable boundary is:

```text
Shader pack
  -> Vitrail: what the pack means
  -> Minecraft graphics API + narrow backend capabilities
  -> backend: how the GPU operation is executed
  -> Metal/Vulkan
```

Vitrail owns shader-pack semantics and scheduling. Metallum owns native Metal execution. The bridge exists only where Minecraft's public graphics API cannot express a required operation.

## What stays in Vitrail

- Pack parsing, program routing, GLSL translation, uniforms, samplers, and shader-pack compatibility semantics.
- `DRAWBUFFERS`/MRT meaning, render-target identity, ping-pong/history, depth/shadow timing, and frame scheduling.
- Policy decisions such as which storage images clear, which persist, which follow the camera, and when scratch storage/reanchor is required.
- Decisions about when a capability is required, optional, refused, or allowed to fall back.

## What stays in a backend

- Native texture/buffer allocation and destruction.
- Native pipeline state and resource argument binding.
- SPIR-V/backend shader translation details.
- Encoder lifetime, command ordering, fences, and other native synchronization mechanisms.
- Native presentation and backend-specific execution details.

## Boundary invariants

- Prefer Minecraft `GpuDevice`, `CommandEncoder`, `RenderPass`, `RenderPipeline`, `GpuTexture`, `GpuTextureView`, `GpuBuffer`, and `GpuSampler` across the seam.
- A missing public operation should normally become one narrow semantic capability, not a general-purpose backend facade.
- Backend-neutral interfaces describe the needed GPU effect, not Vulkan/Metal vocabulary. Native handles, descriptor indices, image layouts, access masks, and encoder objects stay backend-side.
- Optional Metallum integration should remain a soft dependency where practical; current bridges use optional Mixin targets rather than placing Metallum on Vitrail's common compile classpath.
- Indexed nullable MRT slots are semantic. Never compact an attachment array: an unused middle slot must keep later fragment outputs at their original indices.
- Do not translate Vulkan barriers literally into Metal. Preserve the dependency using Metal encoder lifetime/ordering/fences.
- Vulkan behavior is the migration baseline. A Metal implementation must not force a Vulkan semantic rewrite unless the shared semantic model itself was wrong.
- Unsupported or unvalidated Metal behavior must remain explicit. Do not advertise completeness because the screen contains a plausible image.

## Current storage-resource pattern

Shader-storage buffers show the intended shape: Vitrail owns the name/policy and carries a Minecraft `GpuBuffer`; the backend allocates the real resource and classifies the raw compiled shader resource as storage when binding it.

Storage images should follow the same ownership rule. Vitrail decides allocation lifetime, clears, resize, persistence, and reanchor behavior; the backend provides shader-writable texture allocation plus exact clear/copy commands and native binding.

## Evidence

Verify this model against current repository evidence before extending it:

- `docs/metallum-port.md`
- `docs/internals/game-graphics-api.md`
- `common/src/main/java/dev/vitrail/render/MipmapCommands.java`
- `common/src/main/java/dev/vitrail/render/StalePipelines.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageBufferBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageCommands.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/`
- companion repository `DebuNeko233/metallum`, draft PR #1
