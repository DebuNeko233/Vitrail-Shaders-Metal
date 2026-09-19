# The migration roadmap

This page is the condensed English form of the plan this work is tracked against. The plan itself
is a long Chinese document, kept verbatim beside the repository's other architecture memory at
[`.context/architecture/roadmap.md`](../.context/architecture/roadmap.md); that file is the source
of record and preserves its own typography, which the house text rule would refuse in `docs/`. This
page is what the documentation set routes a reader to.

Where the plan sketches a mechanism and the implementation arrived at a different one, this page
says so instead of repeating the sketch. Those places are collected under
[Where this differs from the sketch](#where-this-differs-from-the-sketch), because a reader who
takes the sketch for a specification will design the wrong seam.

## The responsibility split

```text
Shader pack
    |
    v
Vitrail                 what the pack means: parsing, program routing, GLSL translation,
                        uniforms and samplers, colortex/depthtex/shadowtex, shadow,
                        deferred, composite, final, dimension routing, pack settings,
                        compatibility
    |
    v
Minecraft 26.2 graphics API    GpuDevice, CommandEncoder, RenderPass, RenderPipeline,
                               GpuTexture, GpuTextureView, GpuBuffer, GpuSampler
    |
    v
Metallum                how the GPU does it: device, command encoder, render pass,
                        pipelines, textures, buffers, samplers, SPIR-V to MSL,
                        synchronization, presentation
    |
    v
Apple Metal
```

Vitrail answers what a shader pack wants to say. Metallum answers how those operations are carried
out on Apple Metal. The boundary is meant to hold for the life of the project, and every change that
crosses it is supposed to be re-examined rather than waved through. A mechanism belongs on the
Vitrail side when it is a statement about a pack, and on the Metallum side when it is a statement
about the GPU.

## A plausible image is not a correct image

Nothing counts as finished because it does not crash, and nothing counts as finished because there
is an image on screen. It counts as finished when the semantics the pack asked for and the
behaviour the GPU performed agree. Graphics defects mostly do not announce themselves: they produce
an image that looks reasonable and is wrong. The recurring shapes are a ping-pong target read from
the wrong half, MRT attachments in the wrong order, `depthtex` sampled at the wrong moment,
reversed-Z left unconverted, an alpha cutout lost, wrong blending, a wrong dimension fallback, a
wrong shadow depth convention, the previous frame read as this one, a sampler in the wrong slot, and
a vertex stride that no longer matches.

## Boundary rules

- Vitrail does not grow a Metal renderer. The pack runtime stays backend-neutral, and names like
  `MetalColorTargets` or `MetalCompositeRenderer` are the shape being avoided.
- Minecraft's public graphics types are preferred across the seam. Only what that API cannot express
  becomes a backend extension.
- Extensions are small capability interfaces that describe a GPU effect, not a large `RenderBackend`
  facade, and never Vulkan vocabulary. `MipmapCommands.generateMipmaps(GpuTexture)` is the intended
  shape; pipeline stage bits, access masks and image layouts are not.
- Metallum does not learn shader-pack policy. It implements textures, views, attachments, clears,
  sampling, blits and rendering; it does not schedule pack targets, and it does not know a
  `colortex` name.

## The compilation path

```text
OptiFine/Iris GLSL
    -> Vitrail translation (built-ins, uniforms, samplers, varyings, attributes,
       alpha test, coverage, draw buffers, pack semantics)
    -> SPIR-V
    -> Vitrail SPIR-V patching, reflection and resource mapping
    -> Metallum
    -> SPIRV-Cross
    -> MSL
    -> a Metal function
```

Legacy terrain, entity and Sodium translators belong to the pack runtime and are not meant to live
in the backend for the long term.

## Synchronization and encoder lifetime

Vulkan barriers are not translated one by one into Metal barriers. What is abstracted is the
dependency, not the barrier API: this pass wrote a texture, the next pass reads it, and Metal
expresses that with encoder lifetime, command-buffer ordering and `MTLFence`. Encoder state is meant
to be unambiguous at every moment, so a render encoder is never left open while a blit encoder
begins, every command is begun, encoded and ended, and an exception path closes its encoder too.

## Render targets

MRT is the foundation everything else rests on, not shadow and not composite: one draw writes
`colortex0`, `colortex1`, `colortex2` and the rest at once, so the single-attachment model has to go
first. Per-attachment pixel format and per-attachment blend are part of that, which is why the
backend keeps attachment state attachment-local rather than holding one blend state for the whole
pass.

A pipeline key that contains only the shaders is not enough. It carries at least the vertex and
fragment stages, the vertex format, topology, depth and stencil formats, sample count, the
attachment count, and each attachment's colour format and blend state; otherwise the same shaders
against a different framebuffer can reuse a pipeline that no longer describes it.

Vitrail is the single source for target meaning: `colortex`, `depthtex`, `shadowtex`, ping-pong,
format, size, clear colour, mipmaps, flip and history. Metallum implements texture, view, attachment,
clear, sample, blit and render, and does not carry a second target scheduler.

## Ping-pong

The invariant is that every read sees the surface produced by the write that preceded it. Frame
parity is not a way to guess at that; `TargetPlan` and `TargetSchedule` decide it. Metallum does not
compute a pack flip.

## Depth

Scene depth, `depthtex0`, `depthtex1`, `depthtex2`, shadow depth, hand depth and pre-translucent
depth are distinct and are not aliased into one texture. In particular Minecraft's reversed-Z is not
the legacy pack depth convention, and the conversion happens at one declared boundary rather than
wherever it is convenient.

## The scene seed is a transition, not an implementation

While some geometry still is not drawn through a pack program, the game's own rendered scene can be
carried into a pack colour target. That path is a compatibility bridge and is not the correct
implementation: it already carries Minecraft's lighting, fog, tone mapping and gamma, and the
normal, material and raw albedo behind it cannot be recovered. Every family that starts drawing
through the pack should shrink what the seed has to cover.

## Vertex format is an ABI

Location, offset, format, stride and buffer slot in the shader must agree with the Metal descriptor.
A shader asking for an attribute the descriptor does not declare is a defect, and so is a descriptor
whose stride no longer matches. Every extended vertex format is meant to have a test.

## Pass semantics

Pass behaviour does not follow from a program name. One `gbuffers_terrain` can serve several terrain
passes, so the pass carries its own semantics: alpha test, blend, depth write, depth compare and
coverage. That is what keeps a fire, a flower, leaves and a grass side overlay from being treated as
one case, and what keeps a discarded fragment from being drawn anyway.

## Fail-safe and logging

A pack error must not take down Minecraft's own renderer. A failing program is disabled, its family
falls back, and the native pipeline draws; a crash is not the outcome. Metal backend state that has
genuinely gone bad is a different thing and may terminate.

A log line is meant to answer what happened, for which pack, program, stage, backend and attachment,
why it fell back and where to. Lines that say only that a shader failed are not useful.

## Testing

Four levels, from cheapest to most expensive:

1. plain Java, no GPU: pack parsing, dimension routing, option parsing, target scheduling, program
   fallback, flip parity, shader transformation;
2. shader compilation: GLSL to SPIR-V to MSL across terrain, entity, hand, MRT, cutout, samplers and
   uniforms;
3. Metal contract smoke tests: a deliberately tiny shader whose result is then checked, for MRT,
   depth, cutout, blend, samplers, mipmaps, ping-pong and the vertex ABI;
4. real Minecraft, on a fixed Apple Silicon, Minecraft, loaders, Sodium, Metallum and Vitrail set,
   with reproducible scenes.

Large packs are not the instrument for locating a foundation defect; the layered single-capability
packs are. Photon is the last rung rather than the first.

CI proves compilation, Mixin closure, API compatibility and the contract scripts. It cannot prove a
Metal ABI, a Metal pipeline, Metal synchronization or Apple GPU behaviour. The two labels are
therefore kept apart: `CI Verified` and `Real-device Verified`, and neither substitutes for the
other.

## The phases

```text
0   architecture freeze
1   Vitrail backend neutralization, Vulkan behaviour unchanged
2   Metallum MRT foundation
3   Vitrail to Metallum bridge
4   first fullscreen pack pass
5   terrain and Sodium integration
6   gbuffer MRT
7   entities, block entities, hand
8   depth system
9   shadow
10  deferred
11  composite
12  final
13  dimension routing
14  wide resources and argument buffers
15  compute and storage
16  advanced features: PBR, history, temporal
17  real pack compatibility
```

PHASE 0 fixes the boundary before more features land and stops new high-level pack semantics entering
Metallum. PHASE 1 is not "start writing Metal Vitrail": it extracts the backend extension out of the
existing Vulkan implementation and requires Vulkan behaviour to be unchanged before any Metal
backend begins. PHASE 2 is the hard dependency for everything after it. PHASE 17 is where a pack's
compatibility is finally decided, under the five statuses `Supported`, `Partially Supported`,
`Fallback`, `Unsupported` and `Broken`; "compatible" on its own is not an answer. See
[PHASE 17 compatibility](phase17-compatibility.md) for the evidence rules.

**Two numbering systems are in use and they are not the same one.** This page numbers the *migration*,
PHASE 0 to PHASE 17 above. The performance work that follows it - attachment lifetime, bindings,
compilation, dead resources, Metal 4, MetalFX and the removal of the Vulkan path - is numbered `P0` to
`P7` in [Performance, Metal 4 and MetalFX](performance.md), which carries each phase's Apple
documentation, its measurements and its exit criterion, and which is where a phase's status is
recorded. A bare "P1" is ambiguous between the two: PHASE 1 here is backend neutralization, and
performance P1 is attachment lifetime, whose measured verdict is that removing a sixth of a frame's
attachment traffic returns no frame time on the hardware it was measured on.

## Definition of done

A phase is done when the implementation is complete, its tests are complete, CI is green, there is
no known startup regression, the native fallback works, the debug logs are sufficient, the smoke
test passes, a real Apple Silicon test passes, the documentation and `AGENTS.md` are updated, the
known limitations are written down, and no unrelated feature came along with it.

Nothing merges with a startup crash, a world-load crash, a broken native fallback, a changed image
with no pack selected, a serious GPU validation error, a resource lifetime error, a pipeline cache
reused across a state it does not describe, a misplaced MRT attachment, a vertex ABI mismatch, or an
obvious synchronization error.

## Where this differs from the sketch

These are the places where reading the plan as a specification would send a reader the wrong way.
Each is a deliberate arrival at the same goal by another route, not an omission.

- **The bridge is not a third module.** The plan sketches `vitrail-metallum` as a separate module
  depending on both Vitrail and Metallum, so that Vitrail never depends on Metallum. What exists
  instead is an optional seam: `common/src/main/java/dev/vitrail/compat/metallum/` and
  `mixin/metallum/` reach Metallum through optional `@Pseudo` Mixin targets and reflective lookups,
  and Metallum is never on Vitrail's common compile classpath. The goal holds; the consequence to
  know is that a signature mismatch surfaces as a runtime capability failure rather than a compile
  error, which is why `MetallumApi` carries an API version and why a failed lookup is cached as a
  negative result rather than retried per frame.
- **The capability list is a sketch, not a set of interface names.** Only `MipmapCommands` exists
  under the name the plan gives. The rest of the seam is `ComputeDeviceBackend`, `ComputeCommands`,
  `StorageBufferBackend`, `StorageImageBackend`, `StorageImageCommands`,
  `ShaderWritableTextureBackend` and `StalePipelines`, alongside `GpuFormats`, `PackPass`,
  `PackChain`, `ColorTargets` and `TargetSurface`. The principle (small interfaces, no Vulkan
  vocabulary) is what to hold onto.
- **Nothing needed migrating out of Metallum.** The plan's migration table assumes a backend that
  already carries a pack runtime. This Metallum never did: there is no `LegacyTerrainTranslator`, no
  `ShaderPackManager` and no dimension routing in it, and `tools/ci-contracts.py` fails CI if
  shader-pack vocabulary appears in the backend source. "Metallum keeps no pack policy" is the
  present state rather than a later stage.
- **CI runs on macOS, not Linux.** The plan has Linux CI covering compilation and unit tests on the
  grounds that Linux cannot prove Metal. Both repositories run their CI on an Apple Silicon macOS
  runner, which is a superset of what the plan asks for; the `CI Verified` and
  `Real-device Verified` distinction it requires is kept regardless.
- **The smoke packs are contract fixtures rather than a numbered ladder.** The plan lists
  `00-basic-color` through `15-compute`, one capability each. The repository has the same idea under
  different names: `tests/fixtures/shaderpacks/*-contract`, one launcher per capability, covering
  MRT, terrain, depthtex0, the shadow family, deferred, composite, final, dimension routing, wide
  resources, compute and storage, particles, weather, sky, clouds and the hand.
- **The development order did not follow the plan's ladder.** The plan says Photon should not be the
  first pack used to locate foundation defects. Photon was in fact used early and often, which is
  why so much of the recorded evidence in `.context/STATE.md` is Photon-shaped. That is history and
  is not rolled back; what it means for the reader is that the plan's ordering is the intended one
  for new capability work, not a description of how the existing work was sequenced.
