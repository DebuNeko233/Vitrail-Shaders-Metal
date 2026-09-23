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
- Optional Metallum integration should remain a soft dependency where practical: today that is two optional Mixin targets (`metallum.MetalBackendMixin`, `metallum.MetalDeviceMixin`) plus the capability resolvers, and Metallum is still not on Vitrail's common compile classpath. The encoder Mixin was deleted once the adapter answered the same question, which is the direction this invariant points.
- Indexed nullable MRT slots are semantic. Never compact an attachment array: an unused middle slot must keep later fragment outputs at their original indices.
- Do not translate Vulkan barriers literally into Metal. Preserve the dependency using Metal encoder lifetime/ordering/fences.
- **Vulkan is not a preservation target.** Metal is the maintained path and the Vulkan path's removal is scheduled work (`docs/performance.md`, phase P7), so a Vulkan-only behaviour is not kept merely because it exists. What does survive is the semantic model Vitrail owns: a Metal implementation may not give a pack a different meaning, and an unsupported or unvalidated behaviour stays explicit rather than being advertised as complete because the screen contains a plausible image.

## Reference vertex-ABI discipline

Vertex formats are compatibility ABI, not a convenient place to silence shader warnings.

- Match the exact Iris format used by the corresponding render family before adding an attribute or changing stride.
- A pack declaring an attribute that the reference render family does not physically carry is not, by itself, evidence that Vitrail should add that element. First determine the reference translator/default/constant behavior.
- Iris 26.1 `IrisVertexFormats.TERRAIN` carries `mc_Entity`, `mc_midTexCoord`, `at_tangent` and `at_midBlock`.
- Iris 26.1 `IrisVertexFormats.ENTITY` carries `iris_Entity`, `mc_midTexCoord` and `at_tangent`, but **not** `at_midBlock`; ordinary entities and shadow entities use that `ENTITY` format.
- Therefore an entity/shadow shader asking for `at_midBlock` must not cause Vitrail to grow a Vitrail-only entity vertex element. Classify/implement the same default behavior as the reference instead.
- Iris 26.1 particle and weather keys use `DefaultVertexFormat.PARTICLE`. That format has no backing elements for `mc_Entity`, `mc_midTexCoord` or `at_tangent`; those observed names belong in reference-unbacked diagnostic handling, not a widened particle/weather ABI.
- Iris 26.1 sky keys use the vanilla `POSITION`, `POSITION_COLOR`, `POSITION_TEX` and `POSITION_TEX_COLOR` formats. None backs `mc_Entity` or `mc_midTexCoord`; the core transformer does not turn either observed name into a physical sky field.
- Iris 26.1 line rendering uses `DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH`. It carries neither UV2 nor an entity id; `VanillaCoreTransformer` can still declare `iris_UV2` after renaming `vaUV2`, leaving it without a backing array on that format.
- Keep three concepts separate: a mesh-backed answer, a reference-unbacked input, and the deterministic constant Vitrail chooses for that unbacked input. Classifying the second does not prove value-for-value parity with OpenGL generic-attribute state.
- Apply the same source-first audit to any future family/input warning before changing its mesh format.

This rule is deliberately stricter than warning elimination: a quiet log with a divergent vertex ABI is a compatibility regression.

## Current storage-resource pattern

Shader-storage buffers show the intended shape: Vitrail owns the name/policy and carries a Minecraft `GpuBuffer`; the backend allocates the real resource and classifies the raw compiled shader resource as storage when binding it.

Storage images should follow the same ownership rule. Vitrail decides allocation lifetime, clears, resize, persistence, and reanchor behavior; the backend provides shader-writable texture allocation plus exact clear/copy commands and native binding.

## Instrumentation the engine carries

The long-term performance programme left counting in the engine, each census owning one log line a second, so a
question about the frame is answered by reading a line rather than by attaching a profiler: `render.ShadowCensus`
("Shadow walk:", "Shadow casters:", "Shadow map: ... drawn into it N times in the last 600 frames",
"Feedback copies:"), `render.MipmapCensus` ("Mip chains:", counted per target so a chain names the image it was
filled for), `render.TargetCopyCensus` ("N targets are copied back from their far half" - computed from the pack's
**declaration text**, so it over-counts; see `docs/phase17-compatibility.md`), `render.ComputeDispatchCensus`
("Compute dispatches:") and `compat.metallum.BridgeCensus` ("Vitrail bridge:"). The removal and interval probes that
priced them are default-off switches rather than shipped behaviour, and `docs/performance-report.md` is where their
results are read together.

## Evidence

Verify this model against current repository evidence before extending it:

- `docs/metallum-port.md`
- `docs/internals/game-graphics-api.md`
- `common/src/main/java/dev/vitrail/render/MipmapCommands.java`
- `common/src/main/java/dev/vitrail/render/StalePipelines.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageBufferBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageCommands.java`
- `common/src/main/java/dev/vitrail/render/VertexInputDiagnostics.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/`
- Iris 26.1 `common/src/main/java/net/irisshaders/iris/vertices/IrisVertexFormats.java`
- Iris 26.1 `common/src/main/java/net/irisshaders/iris/pipeline/programs/ShaderKey.java`
- Iris 26.1 `common/src/main/java/net/irisshaders/iris/gl/state/ShaderAttributeInputs.java`
- Iris 26.1 `common/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/VanillaCoreTransformer.java`
- companion repository `DebuNeko233/metallum` - its `master` carries the backend half of the long-term performance programme, and `docs/long-term-performance-summary.md` there is that programme's result
