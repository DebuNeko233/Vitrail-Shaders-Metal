# Performance, Metal 4 and MetalFX

This page is the roadmap for making the Metal path faster, for adopting Metal 4, and for deciding
what MetalFX is allowed to do here. It is written against the code as it stands rather than against
a wish list, so it opens by recording what is **already implemented** and then lists only what is
left. Read it with [`metallum-port.md`](metallum-port.md) for validation status and
[`roadmap.md`](roadmap.md) for the migration plan the phase numbers elsewhere refer to.

Two things it is not. It is not a compatibility document: nothing here may change what a shader
pack observes, and every claim about a pack still travels the evidence path in
[`phase17-compatibility.md`](phase17-compatibility.md). And it is not a promise: every phase below
has an exit criterion, and a phase that cannot show its number does not proceed to the next one.

## The rule that does not move

```text
changing how something is executed  !=  changing what a shader pack observes
```

Pass order, `DRAWBUFFERS` and MRT numbering, `colortex*`, `depthtex*`, `shadowtex*`,
`shadowcolor*`, ping-pong parity, cross-frame history, clear behaviour, texture formats, sampling
behaviour, depth compare, blend/cull/depth state and the intermediate results a pack can read are
all pack-visible. They are fixed. What is optimised is the execution underneath them: how many
render encoders, which load and store actions, which storage mode, how many bindings reach Metal,
and when a pipeline is compiled.

`AGENTS.md` no longer requires the Vulkan baseline to be preserved. That rule was lifted
deliberately, and section "Removing the Vulkan path" below records what that means in practice:
the Vulkan code is still in the tree, its removal is scheduled work, and no phase on this page is
allowed to become an excuse for a pack-visible change.

## What is already implemented

Recorded first so that it is not built twice. Each row cites the code that decides it.

| Item | Where it stands now | Evidence |
| --- | --- | --- |
| Render encoder reuse | Already merged. Encoders are not begun per pass; the encoder is kept and invalidated when the pass configuration changes, and the whole repository ends an encoder in five places. | `render/MetalCommandEncoder.java:225-243`, `render/MetalRenderPass.java:431-435` |
| State shadowing | Already implemented, at named-binding granularity: pipeline, scissor and vertex buffers carry dirty flags, and each named descriptor marks itself dirty rather than every binding being re-pushed per draw. | `render/MetalRenderPass.java:66-68`, `:186`, `:203`, `:334`, `:605-631` |
| Argument buffers | Already used for the passes that need them, one buffer per encoded layout, kept per layout and re-bound. | `render/MetalRenderPass.java:688-700`, `mtl/MTLArgumentEncoder.java` |
| Private storage for GPU-only textures | Already the default for textures; buffers take Shared only when the CPU must write them, and use untracked hazard tracking because the engine fences explicitly. | `render/MetalGpuTexture.java`, `render/MetalGpuBuffer.java` |
| Target copies on real read-and-write | Already implemented, and stricter than the usual proposal: a copy is taken only for a target a geometry program both samples and draws into, only for passes after the deferred stage, and only for the targets the reference binds to a geometry program at all. The page states its cost and its divergence. | `render/TargetCopies.java` |
| Attachment feedback classification | Already implemented for the shadow family: sampling an attachment being written by the same pass is classified as feedback rather than as unfilled. | `render/GeometryProgram.java:2615`, `:2487` |
| Background pipeline precompile | Already a narrow advertised capability with serialized cache paths, so warm-up workers may compile off the render thread. | `MetallumApi.supportsBackgroundPipelinePrecompile`, `render/MetalDevice.java:240` |

What that table means for the optimisation list this work started from: attachment lifetime
analysis, encoder merging, state shadowing, Private storage interning and target-copy pruning are
**already done or already the shape of the code**. Building them again is the largest available
waste of effort on this page.

## What is actually left

Ordered by expected value per unit of risk. Every one of these is a real absence in the current
tree, not a refinement of something present.

### 1. Store actions are always Store, and load actions are always load-or-clear

`MetalRenderPass` chooses no load or store action at all. A pack pass reaches Metal through
`MTLCommandBuffer`, which picks the load action with `clearColor != null ? CLEAR : LOAD` and passes
`STORE_ACTION_STORE` unconditionally (`mtl/MTLCommandBuffer.java:128-148`). The `DONT_CARE`
constants exist and are used, but only by the engine's own internal passes
(`mtl/MTLBuiltinPipelines.java:210-224`, `:276`).

On a tile-based GPU this is the single largest avoidable cost in the frame: an attachment whose
contents are dead when the pass ends is still written back to memory. The fix is not "use
`dontCare` more": it is to publish, from Vitrail, whether this is the attachment's last use, and let
the backend choose. The machinery for the load half already exists in a narrow form - the shadow map
defers its clear into the first pass's load op rather than encoding it separately
(`render/TerrainDraw.java:590-640`, `render/ShadowTargets.java`) - so this is a generalisation of a
mechanism the engine already trusts, not a new one.

### 2. Binding dedup is by name, not by value

`markDescriptorDirty(name)` marks a binding dirty unconditionally, so re-binding the same texture
or sampler to the same slot still reaches the native call. Comparing the handle before marking it
costs one field read and cannot change behaviour.

### 3. Load-time and first-use compile stalls

This is measured rather than argued: a pack switch costs **2.5 to 3.7 seconds**, of which the
background work alone is 2500 to 3731 ms, and a single load compiles **155 leftover pipelines**
(`.context/STATE.md`, the `5ab260ab` session). Today the only acceleration is parallelism across
warm-up workers and an optional background precompile guarantee. There is no persistent pipeline
cache and no binary archive anywhere in the tree.

This is a player-visible cost in every session, which is why it is here at position three and not
in a later phase.

### 4. Dead-resource elimination is not reachability-based

`TargetCopies` and the binding plan are driven by the pack's **declaration text**. That
over-counts, and the repository has already recorded the case: on Photon, `deferred` appears to
read `colortex6` and `colortex7` because those are the pack's own three-dimensional worley
overrides, not colour-target reads. A sampler whose every use was removed by `#ifdef` still counts.
Reflection over the SPIR-V this engine already produces is the way to find out, and it is a
different thing from writing a shader optimiser (see "What not to do").

### 5. Nothing publishes attachment lifetime

The knowledge exists, spread across `pack/target/ChainPlan.java` (per-pass reads and writes),
`pack/target/TargetSchedule.java` and `TargetPlan.java`, `render/ColorTargets.java` (clears, flips,
keep directives), `render/TargetCopies.java` and `render/ShadowAmortisation.java`. No single place
states "first use, last use, fully overwritten, survives the frame", and the backend is told none of
it. Item 1 needs exactly that, and so does Metal 4 (see below), which is why this is foundation work
rather than an optimisation.

The shape to aim for is a per-pass description crossing the seam as **semantics**, never as handles:
reads, writes, whether previous contents are needed, whether the pass fully overwrites, whether a
clear is owed, whether the target survives the frame, and which side of a ping-pong pair it is.
Metallum turns that into load action, store action, storage mode and encoder boundaries. It must
never learn that resource 17 is `colortex7`: that boundary is what `metallum/tools/ci-contracts.py`
enforces, and it is also what makes Metal 4 a backend-internal change rather than a rewrite.

## Measure before changing anything

Every item above is a hypothesis until a capture says otherwise, and the engine already has places
to put the numbers (`render/timing/PassTimings`, `render/timing/ShadowFrameProbe`). One Metal GPU
Capture, on Photon at the 16-chunk distance the tester uses, should yield three numbers:

1. **Render encoder count per frame**, and how many boundaries are pass configuration changes rather
   than real dependencies.
2. **Bytes stored and loaded per frame, per attachment.** This is what item 1 is worth.
3. **Bindings per frame**, split into pipeline, texture, sampler, buffer, viewport and scissor. This
   is what item 2 is worth.

Item 1 does not proceed until the second number exists, and item 2 does not proceed until the third
does. A phase whose number was never captured is not a phase; it is an opinion.

## Phases

Each phase ends with an exit criterion, and the next one does not start until it is met.

### Phase P0 - Instrument

Add the three counters above behind a marker or system property, in the style
`-Dvitrail.probeShadowFrames=true` already uses, and record a baseline capture for one heavy pack and
one light pack. Exit: the three numbers exist for a real session, and a second capture reproduces
them within noise.

### Phase P1 - Last use, and the store action that follows

Publish attachment lifetime from Vitrail (item 5), consume it in Metallum for load and store actions
(item 1). Exit: stored bytes per frame fall on the measured capture, and a screenshot comparison
across the regression set in "Regression, not just frame rate" is unchanged. `dontCare` on a target
whose contents are still read later is a wrong image, not a slow one, so this phase's evidence is
the comparison rather than the counter.

### Phase P2 - Bindings and uniforms

Value-based dedup (item 2), and confirm every uniform path writes into the transient allocator
rather than allocating per draw (`render/MetalCommandEncoder.java` exposes `transientMemory()`, and
it has few call sites - the check is whether the uniform upload is among them). Exit: bindings per
frame fall, frame time does not regress, and no pack-visible value changes.

### Phase P3 - Stop paying for compilation twice

Persistent pipeline cache keyed on pack content hash, the MSL, the attachment formats, and
depth/blend/sample-count state (item 3). Exit: a warm pack switch costs materially less than the
2.5 to 3.7 seconds recorded today, with the same first full frame. Cold-start behaviour must not
change: the first run of an unseen pack is allowed to be slow.

### Phase P4 - Reachability

Reflection-driven dead resource elimination (item 4), applied to copy generation and binding, not
to shader mathematics. Exit: at least one real pack is shown to take fewer copies or fewer bindings
because a sampler is provably unreachable after preprocessing, with no image change.

### Phase P5 - Vulkan removal

See the next section but one. Deliberately last: it is deletion work, and doing it after the
lifetime work means the surviving paths are the ones that were measured.

## Metal 4

Metal 4 is a parallel API surface that builds on the existing Metal framework, supported from M1
and A14 onward, and adopted by detecting support and creating the new objects while the old ones
remain available. The authoritative descriptions are Apple's session and the what's-new page
(listed under References).

What it changes for this engine, in order of importance to this page:

- **Argument tables replace per-encoder binding calls, and every resource is untracked, with an
  explicit barrier API for stage-to-stage synchronisation.** Binding therefore stops being a
  per-draw cost that can be shadowed and becomes a table that is written once and updated in place.
  This subsumes the current argument-buffer path and makes it the only path, and it is why item 5 on
  this page is a prerequisite rather than a nicety: explicit barriers need to know what a pass reads
  and writes, which is the same knowledge a lifetime model carries.
- **A render encoder carries an attachment map, so logical shader outputs map to physical colour
  attachments and one encoder can swap between them.** This is the platform's own answer to the
  encoder-merging idea this work started from. Do not build a bespoke encoder merger first; make the
  engine able to express "the same attachment set, and then a different one" and let the backend
  decide whether that is a new encoder.
- **A unified compute encoder absorbs blit and acceleration-structure work**, which reduces encoder
  count for the engine's own internal passes (`mtl/MTLBuiltinPipelines.java` is where those live).
- **`MTL4Compiler` is separate from the device, and render pipeline states can be created
  unspecialised and then specialised from shared Metal IR for different colour states.** For this
  engine that is a better answer than a binary archive, because the recurring cost here is not
  "the same pipeline again" but "the same shaders, different attachment and colour state" - which is
  what makes a reload recompile a chain that was already compiled once. Phase P3 should therefore
  build the cache in a way that can be superseded by specialisation rather than in competition with
  it.
- **Placement sparse resources and residency sets replace the heap-placement idea.** Do not design
  around `MTLHeap` placement as the long-term answer.

What to do now, so that Metal 4 is an internal change: finish item 5 (lifetime semantics across the
seam), keep every backend call behind the semantic seam, and do not let a Metal 4 type name reach
`common/`. If the seam stays semantic, Metal 4 is a second implementation of the same interface; if
it becomes handle-shaped, Metal 4 is a rewrite.

## MetalFX

MetalFX is treated here as a decision that has to be made before code, not as an optimisation to
schedule, because its risk is not in the API but in where it sits. Frame interpolation and
denoising reached Apple Silicon with macOS Tahoe and Metal 4, and the upscaler now reconstructs
detail from lower render resolutions.

Three questions, in order, and none of them is answerable from this repository alone:

1. **Where does it sit relative to the pack's own chain?** A pack's `final` pass owns the image, and
   most large packs already implement their own temporal accumulation - Photon ships `#define TAA`
   on by default. A frame interpolation or temporal upscale placed after that fights the pack's own
   history, and one placed before it changes what the pack is handed. The defensible default is
   engine-level, after the pack's `final`, opt-in, and never inside the pack's chain.
2. **What happens to the existing scale modules?** The engine already has its own
   `vitrail_scale_upscale_*` and `sharpen_*` modules, and the tester runs at a 65 percent render
   scale. Two upscalers in one frame is a bug with a good frame rate.
3. **Is the resolution a pack-visible fact?** If the pack is told a resolution it is not actually
   rendering at, its own reconstruction assumes the wrong pixel footprint. That is a shader-pack
   semantics question, so it belongs to Vitrail, and any claim about it travels the PHASE 17
   evidence path like any other compatibility statement - not a switch in the settings screen that
   is flipped by default.

An honest first step is therefore a decision record, not an implementation: state where MetalFX may
sit, state that it is off by default, and state which pack-visible quantities change when it is on.

## Removing the Vulkan path

`AGENTS.md` no longer requires the Vulkan baseline to be preserved. The measured scope today, so
that the work can be reviewed as a removal rather than as a surprise:

- **49 files call Vulkan APIs** - the `Vulkan*Mixin` family, the accessor mixins into the game's
  Vulkan types, and `cache/ModuleCache`.
- **58 further files mention Vulkan only in prose**, mostly to explain a divergence or a trap. Those
  are the ones to audit rather than delete: a comment that says "this is why we differ from Vulkan"
  becomes either history worth keeping in one place or a stale claim, and the two must not be
  confused. `docs/sky-and-shadows.md`, `docs/internals/render-targets.md` and
  `docs/internals/game-graphics-api.md` carry most of them.
- **One CI contract**, `tests/test_vulkan_recording_contract.py`, which is named by `build.yml` and
  therefore cannot be deleted without editing the workflow in the same commit.
- **15 documents** name Vulkan, including `README.md` and `CONTRIBUTING.md`.

Order matters. The engine's own claims about itself should be true at every step, so:

1. Change `HostReport.otherBackend` and the status pages together. Today that method calls Vulkan
   the production path and accepts Metal only behind the developer smoke switch
   (`HostReport.java:143-154`); leaving it while claiming Metal is the maintained path is the exact
   defect this repository has spent a week removing from its documentation.
2. Delete the Vulkan-only mixins and their accessors, and the accessors' consumers, in batches that
   each keep the build green. Mixins that exist only to reach Vulkan types are the easiest to remove
   first.
3. Retire `test_vulkan_recording_contract.py` and its `build.yml` line in the same commit, because a
   contract script named by no workflow fails the workflow contract instead.
4. Audit the prose last. Ninety-nine comments describing a backend that no longer exists is worse
   than no comments, and the audit is where the remaining knowledge is either kept deliberately or
   dropped deliberately.

What must not happen along the way: removing the shared seam. The point of deleting Vulkan is to
have one backend, not to have no boundary. The seam is what makes Metal 4 a second implementation
rather than a rewrite, and `metallum/tools/ci-contracts.py` should keep refusing shader-pack
vocabulary in the backend after the deletion exactly as it does before it.

## What not to do

- **Do not fuse pack full-screen passes.** A pack's `composite1` may sample `composite0`'s output at
  an arbitrary offset, so the intermediate must exist as a complete texture. Fusing engine-generated
  internal passes is a different question, and it is Phase P1's work rather than a shader rewrite.
- **Do not write a GLSL optimiser.** The Metal compiler already folds constants, eliminates dead
  code, performs common-subexpression elimination and inlines. Phase P4 removes bindings and copies
  by reachability, which is not the same thing.
- **Do not turn on automatic float-to-half.** Depth, world position, normals, shadow coordinates and
  temporal accumulation are the values most likely to be hurt, and the failure modes (banding,
  shadow acne, ghosting) are exactly the ones a screenshot comparison is least likely to attribute
  correctly. It is defensible only where a pack asked for it.
- **Do not enable aggressive fast-math by default.** Reassociation changes the arithmetic between
  two pipelines that must agree, which is how a depth or temporal mismatch appears.
- **Do not let the pack's mathematics change.** Optimise the compatibility glue this engine
  generates, not the lighting, BRDF, TAA, SSR or shadow code a pack author wrote.

## Regression, not just frame rate

A performance change is accepted with the picture, not instead of it. The set to run is the same one
the evidence policy already implies, and it is small enough to actually be run: screenshot
comparison on a fixed camera and time of day, one frame-by-frame pass on a moving camera, the shadow
family, a temporal pack (Photon with its own TAA), a dimension switch, a shader reload, and a
window resize at a different render scale. Frame rate alone cannot see a wrong store action, which
is why Phase P1's exit criterion is the comparison and not the counter.

## References

- Apple, Discover Metal 4 (WWDC25): https://developer.apple.com/videos/play/wwdc2025/205/
- Apple, Go further with Metal 4 games (WWDC25): https://developer.apple.com/videos/play/wwdc2025/211/
- Apple, What's New in Metal: https://developer.apple.com/metal/whats-new/
- Apple, `MTLStorageMode.memoryless`: https://developer.apple.com/documentation/metal/mtlstoragemode/memoryless
