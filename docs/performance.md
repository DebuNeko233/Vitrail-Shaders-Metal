# Performance, Metal 4 and MetalFX: the phase plan

This is the working plan for making the Metal path faster, adopting Metal 4, and deciding what
MetalFX may touch. It is written against the code in the repository today, so every phase names the
classes and methods it changes rather than the idea behind them. Companion pages:
[`metallum-port.md`](metallum-port.md) for validation status, [`roadmap.md`](roadmap.md) for the
migration plan the phase numbers elsewhere refer to, and
[`phase17-compatibility.md`](phase17-compatibility.md) for what a compatibility claim needs.

## How a phase works

Every phase below has the same five parts: what it is for, what it needs first, the work itemised
against current code, the Apple documentation it follows, and its exit criterion. Three rules apply
to all of them.

1. **A phase whose number was never captured does not start.** P0 exists to produce the numbers, and
   P1 to P4 each state which one they move. An optimisation with no measurement is an opinion.
2. **No phase may change what a shader pack observes.** Pass order, `DRAWBUFFERS` and MRT numbering,
   `colortex*`, `depthtex*`, `shadowtex*`, `shadowcolor*`, ping-pong parity, cross-frame history,
   clear behaviour, texture formats, sampling behaviour, depth compare, blend/cull/depth state and
   the intermediate results a pack can read are all pack-visible and all fixed. What changes is the
   execution underneath them.
3. **A phase ends with a picture as well as a counter.** Frame rate cannot see a wrong store action,
   which is why the exit criteria below name the comparison wherever the risk is a wrong image.

`AGENTS.md` no longer requires the Vulkan baseline to be preserved. P7 is where that becomes code,
and no other phase may use it as an excuse for a pack-visible change.

## Where the code stands today

Recorded first, because the plan this began from proposed several things that are already the shape
of the tree, and building them again is the largest available waste of effort here.

| Capability | Current state | Where it is decided |
| --- | --- | --- |
| Render encoder reuse | Already merged. An encoder is not begun per pass; it is kept, and invalidated when the pass configuration changes. The whole repository ends an encoder in five places, four of which are the engine's own internal passes. | `metallum/render/MetalCommandEncoder.java:225-243`, `metallum/render/MetalRenderPass.java:431-435` |
| State shadowing | Already implemented at descriptor granularity: pipeline, scissor and vertex buffers carry dirty flags, and descriptors are tracked as a `BitSet` of binding indices rather than re-pushed wholesale. | `metallum/render/MetalRenderPass.java:58`, `:66-68`, `:334`, `:624`, `:635-645` |
| Argument buffers | Already used for passes that need them, one per encoded layout, kept per layout and re-bound. | `metallum/render/MetalRenderPass.java:688-700`, `metallum/mtl/MTLArgumentEncoder.java` |
| Private storage for GPU-only textures | Already the default. Buffers take Shared only where the CPU writes them, and use untracked hazard tracking because the engine fences explicitly. | `metallum/render/MetalGpuTexture.java`, `metallum/render/MetalGpuBuffer.java` |
| Copies only on a real read and write | Already implemented, and stricter than the usual proposal: a copy is taken only for a target a geometry program both samples and draws into, only for passes after the deferred stage, and only for targets the reference binds to a geometry program at all. The class states its cost and its divergence. | `common/src/main/java/dev/vitrail/render/TargetCopies.java` |
| Attachment feedback classification | Already implemented for the shadow family. | `common/src/main/java/dev/vitrail/render/GeometryProgram.java:2615`, `:2487` |
| Background pipeline precompile | Already an advertised narrow capability with serialized cache paths. | `metallum/api/MetallumApi.java`, `metallum/render/MetalDevice.java:240` |
| Disk cache with an eviction ceiling | Already exists for compiled shader modules, under the game directory, with a tunable ceiling. It is the precedent a pipeline cache should follow. | `common/src/main/java/dev/vitrail/cache/ModuleCache.java:121`, `:155-158`, `:261-263` |
| SPIRV-Cross reflection | Already queried for bindings and active interface variables during MSL generation. The information P4 needs is already being read; it is not being used to plan bindings. | `metallum/render/MetalCrossShaderCompiler.java:350`, `:354`, `:606`, `:698-699` |
| Frame counters behind a marker | Already exists as a pattern: a system property or a marker file in the game directory, off by default. | `common/src/main/java/dev/vitrail/render/timing/ShadowFrameProbe.java:26-37`, `common/src/main/java/dev/vitrail/render/timing/PassTimings.java` |

What that table means: encoder merging, state shadowing, argument buffers, Private storage,
target-copy pruning and a disk cache are **done or already the shape of the code**. The plan below
therefore starts from what is genuinely absent.

## What is genuinely absent

1. **Store actions are always Store for pack passes, and load actions are never `dontCare`.**
   `MetalRenderPass` chooses neither; a pack pass reaches Metal through
   `metallum/mtl/MTLCommandBuffer.java:128-148`, which picks
   `clearColor != null ? MTLLoadActionClear : MTLLoadActionLoad` and passes `MTLStoreActionStore`
   unconditionally. The `DontCare` constants exist and are used, but only by the engine's own
   internal passes (`metallum/mtl/MTLBuiltinPipelines.java:210-224`, `:276`).
2. **Binding dedup is by index, not by value.** `dirtyDescriptors` records *that* a binding index
   changed, and a pipeline change marks every resource dirty
   (`metalRenderPass.java:624`). Re-binding the same texture to the same slot still reaches the
   native call.
3. **Nothing publishes attachment lifetime.** The knowledge is spread across
   `pack/target/ChainPlan.java` (per-pass reads and writes), `pack/target/TargetSchedule.java` and
   `TargetPlan.java`, `render/ColorTargets.java` (clears, flips, keep directives),
   `render/TargetCopies.java` and `render/ShadowAmortisation.java`. No single place states first
   use, last use, full overwrite, or survives-the-frame, and the backend is told none of it.
4. **Compilation is paid twice for the same shaders.** A pack switch costs a measured 2.5 to 3.7
   seconds and one load compiles 155 leftover pipelines (`.context/STATE.md`, the `5ab260ab`
   session). There is no persistent pipeline cache and no binary archive in the tree.
5. **Dead-resource elimination runs on declaration text, not reachability.** The repository has
   already recorded the resulting over-count: on Photon, `deferred` appears to read `colortex6` and
   `colortex7` because those are the pack's own three-dimensional worley overrides, not
   colour-target reads. A sampler whose every use was removed by `#ifdef` still counts as read.

---

# Phase P0 - Instrumentation

**For.** Everything after this phase is a hypothesis until a capture says otherwise. This phase
produces the three numbers the later phases are judged by, and it produces them inside the engine
rather than by hand, so they can be re-taken after every change.

**Needs first.** Nothing. This is the first phase.

**Work.**

1. Add a probe class in the shape `ShadowFrameProbe` already uses: a system property plus a marker
   file in the game directory, off by default. Read `ShadowFrameProbe.java:26-37` and copy the
   property and marker handling rather than inventing a second convention.
2. Extend the existing census machinery rather than adding a parallel one.
   `PassTimings` already has `armCensus()`, `censusArmed()`, `censusSubmit()`, `censusClear()`,
   `finishCensus()`, `resetCensus()` and `censusReopen(Supplier<String>, String)`. The new counters
   are three more things a census reports, not a new census.
3. Counter one: **render encoder boundaries per frame**, split into "the attachment configuration
   changed" and "a real dependency forced it". The cheapest honest form counts calls in
   `MetalCommandEncoder.endEncoder()` (`:225-243`) and records why the caller ended it, which means
   the caller has to say: the two callers are the `currentEncoder` swap and
   `submitRenderPass()` (`:410`).
4. Counter two: **bytes stored and loaded per frame, per attachment.** This is the number P1 is
   judged by. It needs each attachment's format and size at the moment the descriptor is built, so
   the natural place is the same code that currently picks the actions -
   `metallum/mtl/MTLCommandBuffer.java:128-148` - where the texture handle is already in hand.
5. Counter three: **bindings per frame, split by kind**: pipeline, texture, sampler, buffer,
   viewport, scissor. The bind sites are `MetalRenderPass.bindTexture(String, GpuTextureView,
   GpuSampler)` (`:182`) and the push helpers around `:441-590`.
6. Count pipeline compilations per session, split into "background warm-up" and "on the draw path",
   with the wall-clock cost of each. `MetalCompiledRenderPipeline:249` is where a pipeline is
   actually created, so a counter there covers every caller.
7. Record a baseline capture: one heavy pack and one light pack, at the 16-chunk distance the tester
   uses, on the hardware actually available.

**Apple documentation.** Frame capture and counters are documented rather than guessed:

- Capturing Metal commands programmatically:
  https://developer.apple.com/documentation/metal/capturing-metal-commands-programmatically
- GPU counters and counter sample buffers:
  https://developer.apple.com/documentation/metal/gpu-counters-and-counter-sample-buffers
- Metal debugger (the GPU trace UI):
  https://developer.apple.com/documentation/xcode/metal-debugger
- Improving your game's graphics performance and settings:
  https://developer.apple.com/documentation/metal/improving-your-games-graphics-performance-and-settings

The in-engine counters are not a replacement for a GPU trace. They exist so that a change can be
re-measured in a normal session; the trace is what says which pass is expensive and why.

**Exit criterion.** The three numbers exist for both baseline packs, a second run reproduces them
within noise, the probe is off unless asked for, and a GPU trace of one frame has been taken and
kept with the numbers.

---

# Phase P1 - Attachment lifetime, and the load and store actions that follow

**For.** This is the largest avoidable cost in the frame on a tile-based GPU, and it is the phase
the rest of the plan is ordered around. An attachment whose contents are dead when a pass ends is
currently written back to memory anyway, and one whose contents are about to be fully overwritten is
currently loaded first.

**Needs first.** P0's byte counter, and a capture showing where the stores and loads actually are.
Without that number there is no way to tell a real win from a smaller one.

**Background: why the actions matter on this hardware.** Apple GPUs are tile-based deferred
renderers: a render pass begins by loading each attachment into tile memory, the pass writes into
tile memory, and it ends by storing the result back. Every `Load` and every `Store` is therefore
system-memory traffic that exists only because the action said so, and `DontCare` on either side
removes that traffic without changing what any shader reads inside the pass. Apple documents this
model directly:

- Tailor your apps for Apple GPUs and tile-based deferred rendering:
  https://developer.apple.com/documentation/metal/tailor-your-apps-for-apple-gpus-and-tile-based-deferred-rendering
- Render passes (the load and store actions are properties of a render pass attachment):
  https://developer.apple.com/documentation/metal/render-passes
- Resource fundamentals, for the storage modes that interact with this:
  https://developer.apple.com/documentation/metal/resource-fundamentals

**Work.**

1. **Vitrail: state the lifetime, in one place.** The facts are already computed somewhere; the work
   is to collect them behind one description per pass and per attachment:
   - reads and writes: `pack/target/ChainPlan.java` already walks reads and writes per pass, from
     the declarations. Its known weakness is item 5 of "What is genuinely absent": it is
     declaration-based and over-counts. P4 fixes the precision; P1 needs the structure.
   - whether previous contents are needed, whether the pass fully overwrites, whether a clear is
     owed: `render/ColorTargets.java` already carries `clearOwed` and `fullDeferOwed`, and
     `render/ShadowTargets.java` already defers a clear into the first pass's load op
     (`render/TerrainDraw.java:590-640`). That deferral is the mechanism to generalise, not a new
     one to invent.
   - whether the target survives the frame: `render/ShadowAmortisation.java` and
     `ShadowTargets.hasKept()` are the existing proof machinery for exactly this question.
   - which side of a ping-pong pair a target is on: `pack/target/TargetSchedule.java` and
     `TargetPlan.java`.
2. **Cross the seam as semantics, not as names.** This is the part that must be got right, because
   it is also what makes P5 possible. The backend may be told "resource 17: first use, fully
   overwritten, not read again this frame, does not survive the frame". It may not be told that
   resource 17 is `colortex7`. `metallum/tools/ci-contracts.py` exists to refuse the second, and the
   existing rule in `AGENTS.md` says the same thing about native handles: a capability is added only
   where the public API cannot express the operation, and never as a handle.
3. **Metallum: consume the lifetime.** The change is local and mechanical once the description
   exists: `MetalRenderPass` asks for the actions per attachment and passes them to
   `MTLRenderPassDescriptor.colorAttachment(...)` and `depthAttachment(...)`, which already accept
   `loadAction` and `storeAction` (`metallum/mtl/MTLRenderPassDescriptor.java:48-75`). That replaces
   the unconditional choice in `metallum/mtl/MTLCommandBuffer.java:128-148`.
4. **Keep the conservative direction.** Every uncertain answer must fall back to today's behaviour:
   `Load` and `Store`. `DontCare` is only chosen from a positive lifetime fact, never from the
   absence of one. A wrong `DontCare` on a target that is still read later is not a slower frame, it
   is a wrong image, and it will look like a pack bug.
5. **Record the divergence.** Vitrail's existing practice is to write down why it differs from the
   reference and what it costs; `TargetCopies.java` is the model to copy. If the load or store
   action ends up observable to a pack in any corner case, that belongs in `docs/` next to the code.

**Exit criterion.** Stored bytes per frame fall on the P0 capture, the bindings and encoder counts
do not regress, and the regression set in "Regression, not just frame rate" is unchanged, image for
image. The counter alone does not close this phase.

**Risk.** The failure mode is silent and looks like a pack defect, which is why the phase is
entry-gated on P0 and exit-gated on the comparison rather than on the number.

---

# Phase P2 - Bindings and uniforms

**For.** The second and third numbers from P0. Binding is cheap per call and expensive in volume,
and the current dedup cannot see that the same resource is being bound again.

**Needs first.** P0's binding counter, split by kind, so that the work targets the kind that is
actually hot rather than the one that is easiest to change.

**Work.**

1. **Compare values, not just indices.** `MetalRenderPass` tracks a `BitSet` of dirty descriptor
   indices (`:58`), and a pipeline change marks all of them
   (`dirtyDescriptors.or(compiledPipeline.allResources())`, `:624`). Before setting a bit in
   `bindTexture(String, GpuTextureView, GpuSampler)` (`:182`) and in the buffer and sampler paths,
   compare the currently bound handle for that index and skip the mark when it is unchanged. This
   cannot change behaviour: it only removes a redundant call.
2. **Check the uniform path before changing it.** `MetalCommandEncoder` exposes `transientMemory()`,
   and `MetalRenderPass.allocateTransient(long, long, int)` allocates through
   `transientMemory().allocateGpuMapped(...)`. The question to answer first is whether *every*
   uniform upload goes through that allocator or whether some path still creates a buffer per draw.
   Answer it by reading the call sites of `transientMemory()` rather than by assuming; if a path
   allocates per draw, moving it into the ring is the change, and if none does, this item is closed
   with that finding written down.
3. **Do not add a second binding path.** The argument-buffer path already exists
   (`ensureArgumentBuffersBound`, `:688-700`) and P5 will make it the binding surface. Anything done
   here should be the kind of change that survives that, which means it belongs in the shared
   descriptor bookkeeping rather than in one of the two paths.

**Apple documentation.** Binding and resource management:

- Resource fundamentals: https://developer.apple.com/documentation/metal/resource-fundamentals
- Buffers: https://developer.apple.com/documentation/metal/buffers
- Textures: https://developer.apple.com/documentation/metal/textures
- Shader libraries (binary archives live here, which P3 uses):
  https://developer.apple.com/documentation/metal/shader-libraries

**Exit criterion.** Bindings per frame fall on the capture, frame time does not regress, no
pack-visible value changes, and the uniform question is answered in writing either way.

---

# Phase P3 - Compilation: stop paying twice

**For.** This is the phase a player feels most, and it is measured rather than argued: a pack switch
costs 2.5 to 3.7 seconds, of which the background work alone is 2500 to 3731 ms, and a single load
compiles 155 leftover pipelines. It is also the phase that has to be built so P5 can supersede it
rather than compete with it.

**Needs first.** P0's compilation counter, so that "warm" and "cold" are separated and a cache miss
can be told from a slow compile.

**Work.**

1. **Define a stable pipeline key.** It must include everything that can change the compiled
   result: the pack's content hash (not its name or path), the MSL source hash, the attachment
   formats and sample count, blend state, depth and stencil state, the vertex layout, the GPU
   family, and the Metal and OS version. The engine already has a model for a content-addressed
   cache: `common/src/main/java/dev/vitrail/cache/ModuleCache.java`, which lives under the game
   directory, keys on content, and has a ceiling with eviction (`:121`, `:155-158`, `:261-263`).
   Follow it rather than inventing a second disk-cache convention, including its ceiling policy.
2. **Put the cache where the compile is, not above it.** Pipelines are created in
   `metallum/render/MetalCompiledRenderPipeline.java:249` (and `MTLDevice.newRenderPipelineState` at
   `metallum/mtl/MTLDevice.java:159`), and background precompile enters through
   `metallum/render/MetalDevice.java:240`. A cache consulted in one place and not the other produces
   the worst outcome: a warm cache that a warm-up path ignores.
3. **Understand what supersedes it.** Metal 4 lets a render pipeline state be created unspecialised
   and then specialised from shared Metal IR for different colour states. Apple documents both:
   - Using function specialization to build pipeline variants:
     https://developer.apple.com/documentation/metal/using-function-specialization-to-build-pipeline-variants
   - Shader libraries, which is where binary archives are described:
     https://developer.apple.com/documentation/metal/shader-libraries
   The recurring cost in this engine is not "the same pipeline again" but "the same shaders,
   different attachment and colour state", which is what makes a reload recompile a chain that was
   already compiled once. That is the problem specialisation solves. So the cache key and the
   specialization key should be the same key, and P5 should be able to swap the mechanism without
   redefining the identity.
4. **Keep cold start slow on purpose.** The first run of an unseen pack is allowed to cost what it
   costs; what must improve is the second. A cache that changes first-run behaviour is hiding a
   different bug behind an optimisation.

**Exit criterion.** A warm pack switch costs materially less than the recorded 2.5 to 3.7 seconds,
with the same first full frame and the same image, on a cold cache after a version change as well as
a warm one. The counter must show the compiles moving from the draw path to the cache, not just
disappearing.

---

# Phase P4 - Reachability-driven dead resource elimination

**For.** Bindings and copies are currently planned from what a pack *declares*, not from what its
compiled shader *uses*. The repository has already recorded the cost of that: on Photon, `deferred`
appears to read `colortex6` and `colortex7` because those are the pack's own three-dimensional
worley overrides, not colour-target reads. A sampler whose every use was removed by `#ifdef` still
counts.

**Needs first.** P2's binding counter must already be down, otherwise the reachability win is
invisible in the number. And item 1 below must confirm what reflection already returns.

**Work.**

1. **The capability is already in the tree; find out what it already knows.**
   `metallum/render/MetalCrossShaderCompiler.java` already calls
   `spvc_compiler_get_active_interface_variables` (`:606`) and reads resource names and bindings
   (`:350`, `:354`, `:698-699`). The first task is not to add reflection but to establish, by
   reading that code and printing its output for a real pack, which uniforms and samplers the active
   interface variables already exclude. If the answer is already there, this phase is about
   publishing it rather than computing it.
2. **Send the answer back as a shader-level fact, not as a target fact.** Metallum can say "resource
   X is unreachable in program P". Vitrail owns the mapping from resource X to `colortexN`, so
   Vitrail is where the decision "therefore do not copy and do not bind" is made. Doing it the other
   way round would put pack semantics in the backend, which `metallum/tools/ci-contracts.py`
   refuses.
3. **Apply it to copies and bindings only.** The two consumers are
   `common/src/main/java/dev/vitrail/render/TargetCopies.java` (does this target still need a
   snapshot?) and the binding plan. It is not an invitation to touch shader mathematics, and it is
   not a shader optimiser: the Metal compiler already folds constants, eliminates dead code,
   performs common-subexpression elimination and inlines.
4. **Stay conservative.** A dynamic branch that could read a sampler keeps it reachable. The
   question being asked is "can this provably never be read", and anything short of provable keeps
   today's behaviour.

**Apple documentation.** This phase is mostly compiler and reflection work rather than an API
matter; the Metal side of it is the argument-buffer binding model that P5 formalises:

- Resource fundamentals: https://developer.apple.com/documentation/metal/resource-fundamentals
- Metal enumerations (storage modes and related enums):
  https://developer.apple.com/documentation/metal/metal-enumerations

**Exit criterion.** At least one real pack is shown to take fewer copies or fewer bindings because a
sampler is provably unreachable after preprocessing, with no image change on the regression set, and
the finding is written down with the pack and the evidence. If reflection turns out not to prove
anything on real packs, this phase closes with that negative result recorded, which is a legitimate
outcome and cheaper than a speculative implementation.

---

# Phase P5 - Metal 4

**For.** Adopting the Metal 4 core API as a second implementation of the same seam, not as a
rewrite. Metal 4 is a parallel API surface that builds on the existing framework, supported from M1
and A14 onward, so adoption is a runtime choice: detect support, use the new objects, fall back to
the existing path otherwise.

**Needs first.** P1's lifetime description, because Metal 4 makes explicit what is implicit today.

**Why P1 is a prerequisite, in one paragraph.** In Metal 4 all resources are untracked and
synchronisation is explicit: stage-to-stage ordering is expressed with a barrier API rather than
inferred. An engine that cannot say what a pass reads, what it writes, and how long the result
lives cannot produce correct barriers, and cannot declare residency. That is the same description
P1 builds for a different reason, which is why the ordering here is not a preference.

**Apple documentation.**

- Understanding the Metal 4 core API:
  https://developer.apple.com/documentation/metal/understanding-the-metal-4-core-api
- Using the Metal 4 compilation API:
  https://developer.apple.com/documentation/metal/using-the-metal-4-compilation-api
- Resource synchronization (barriers, fences, events):
  https://developer.apple.com/documentation/metal/resource-synchronization
- Memory heaps: https://developer.apple.com/documentation/metal/memory-heaps
- GPU devices and work submission:
  https://developer.apple.com/documentation/metal/gpu-devices-and-work-submission
- Metal 4 overview session: https://developer.apple.com/videos/play/wwdc2025/205/

**Work.**

1. **Opt in, do not migrate.** Detect Metal 4 support and keep the existing `MTLCommandQueue` path
   as the fallback, in the shape of the optional capability providers this project already uses:
   an advertised capability with an API version, a reflective probe that fails closed, and a cached
   negative result. `metallum/api/MetallumApi.java` and `MetallumStatus` are the model.
2. **Keep the seam semantic.** If the seam stays a description of resources and lifetimes, Metal 4
   is a second implementation of the same interface. If a Metal 4 type name reaches `common/`, it is
   a rewrite, and `AGENTS.md` already forbids that for native handles.
3. **Let the attachment map replace encoder switching.** Metal 4 gives a render encoder an
   attachment map that maps logical shader outputs onto physical colour attachments, configurable
   per encoder and swappable on the fly. That is the platform's own answer to the encoder-merging
   question this plan started from, so do not build a bespoke merger first; the work is to make the
   engine able to express "the same attachment set, and then a different one", and to let the
   backend decide whether that is a new encoder.
4. **Let the unified compute encoder absorb the engine's internal blit work.** Metal 4 consolidates
   blit, compute and acceleration-structure encoding into one encoder type, which reduces encoder
   count for the engine's own utility passes. Those live in
   `metallum/mtl/MTLBuiltinPipelines.java` and `metallum/render/MetalDepthMipmapBridge.java`.
5. **Replace the argument-buffer path with argument tables.** `MTL4ArgumentTable` stores the binding
   points an encoder needs and allocates only for what it uses, and it can be shared across stages.
   The existing path (`ensureArgumentBuffersBound`, one buffer per layout, Shared storage with
   tracking) is the thing it replaces; P2's dedup work is the thing that should survive into it.
6. **Replace fence bookkeeping with barriers where the API is used.** The engine already updates a
   fence when it ends an encoder (`MetalCommandEncoder.endEncoder()`, `:225-243`); under Metal 4 the
   same ordering has to be expressed as a barrier, which is where P1's reads and writes become
   load-bearing rather than documentation.
7. **Use residency sets and placement sparse rather than heap placement.** `MTLHeap` is the pre-Metal
   4 answer and it is the wrong thing to design around now. Apple's memory-heaps page still applies
   to the existing path; the new path has residency sets for the same problem.
8. **Take the compilation API with it.** `MTL4Compiler` is separate from the device and gives
   explicit control over CPU compilation, which is a better fit for the background warm-up workers
   than the current capability advertisement, and the unspecialised-to-specialised pipeline model is
   what P3's key should already be shaped for.

**Exit criterion.** Both paths run the same session with the same images on the regression set; the
Metal 4 path shows fewer encoder boundaries or lower store traffic on the P0 numbers; the fallback
is exercised deliberately at least once, not just left in the tree; and no Metal 4 type appears
anywhere under `common/`.

---

# Phase P6 - MetalFX

**For.** Deciding, before writing code, what MetalFX is allowed to touch. Its risk is not the API
but the placement: it operates on an image, and this engine's image is owned by a shader pack's own
chain, which frequently contains its own temporal accumulation.

**Needs first.** Nothing technically, but it needs two decisions recorded in this repository first,
because both are pack-semantics questions and neither is answerable from the Metal documentation.

**Apple documentation.**

- MetalFX framework: https://developer.apple.com/documentation/metalfx
- Machine learning passes, which is how model inference is encoded in Metal 4:
  https://developer.apple.com/documentation/metal/machine-learning-passes
- Metal, What's New (the source for what the upscaler and frame interpolation now do):
  https://developer.apple.com/metal/whats-new/

**The three questions, in the order they have to be answered.**

1. **Where does it sit relative to the pack's chain?** A pack's final pass owns the image, and large
   packs already accumulate temporally: Photon ships its own temporal antialiasing enabled by
   default. An interpolation or temporal upscale placed after that fights the pack's own history; one
   placed before it changes what the pack is handed. The defensible default is engine level, after
   the pack's final pass, off by default, and never inside the pack's chain.
2. **What happens to the engine's existing scale modules?** The engine already has its own upscale
   and sharpen modules, and the tester's session runs at a 65 percent render scale. Two upscalers in
   one frame is a bug with a good frame rate, so the answer has to be "one of them, selected
   deliberately", and the settings screen has to make that unambiguous.
3. **Is the render resolution a pack-visible fact?** If a pack is told a resolution it is not
   rendering at, its own reconstruction assumes the wrong pixel footprint. That is shader-pack
   semantics, so it belongs to Vitrail, and any claim about it travels the evidence path in
   [`phase17-compatibility.md`](phase17-compatibility.md) like any other compatibility statement:
   per pack, with reviewed visual evidence, not a switch that defaults to on.

**Work, once those are answered.**

1. Write the decision down, with the pack-visible quantities it changes, before implementing.
2. Implement it as an engine-level, post-final, opt-in path with an off switch that restores exactly
   today's image.
3. Verify it per pack, under the compatibility evidence policy, rather than per engine.

**Exit criterion.** A recorded decision, and either an implementation that meets the regression set
with the option off and on, or a recorded decision not to implement it. Both are valid endings; a
half-enabled upscaler is not.

---

# Phase P7 - Remove the Vulkan path

**For.** `AGENTS.md` no longer requires the Vulkan baseline to be preserved. This phase turns that
decision into the tree. It is deliberately last: deletion work is cheapest once the surviving paths
are the ones that were measured, and doing it first would have removed the reference the earlier
phases compare against.

**Needs first.** P1 to P6 either done or explicitly abandoned. The engine's own claims about itself
must be true at every step, and a half-removed backend is the worst state for that.

**Scope, measured today.**

- **49 files call Vulkan APIs**: the Vulkan mixin family, the accessor mixins into the game's
  Vulkan types, and `common/src/main/java/dev/vitrail/cache/ModuleCache.java`.
- **58 further files mention Vulkan only in prose**, mostly to explain a divergence or a trap.
- **One CI contract**: `tests/test_vulkan_recording_contract.py`, named at `.github/workflows/build.yml:92`.
- **15 documents** name Vulkan, including `README.md` and `CONTRIBUTING.md`.

**Work, in this order.**

1. **`HostReport` and the status pages in one commit.** `HostReport.otherBackend()`
   (`common/src/main/java/dev/vitrail/HostReport.java:143-154`) still documents Vulkan as the
   production path and accepts Metal only behind the developer smoke switch. Leaving that in place
   while the documentation claims Metal is the maintained path is exactly the defect this repository
   has spent a week removing from its own pages, so these two change together or neither does.
2. **The Vulkan-only mixins and their accessors**, in batches that each keep the build green. The
   mixins that exist only to reach Vulkan types are the easiest to remove first.
3. **The contract test and its `build.yml` line in the same commit.** A contract script that no
   workflow names fails the workflow contract instead, so the two cannot be separated.
4. **The prose last, deliberately.** Ninety-nine comments describing a backend that no longer exists
   are worse than no comments. This is where the remaining knowledge is either kept on purpose - one
   place, stating why a divergence exists - or dropped on purpose. `docs/sky-and-shadows.md`,
   `docs/internals/render-targets.md` and `docs/internals/game-graphics-api.md` carry most of it.
5. **Keep the seam.** The point of deleting Vulkan is to have one backend, not to have no boundary.
   The seam is what made P5 an implementation rather than a rewrite, and
   `metallum/tools/ci-contracts.py` should keep refusing shader-pack vocabulary in the backend after
   the deletion exactly as it does before it.

**Exit criterion.** No Vulkan API call remains in the tree, the build and the contract suite are
green, `HostReport` and every status page agree that Metal is the path, and the seam is intact with
its contract still enforced.

---

# Regression, not just frame rate

Every phase above is accepted with the picture, not instead of it. The set is small enough to
actually be run, and it is the same set the evidence policy already implies:

- a screenshot comparison on a fixed camera, time and dimension;
- one frame-by-frame pass on a moving camera;
- the shadow family, because it is where the engine's own scheduling is most intricate;
- one temporal pack, with its own temporal antialiasing enabled (Photon is the known case);
- a dimension switch, a shader reload, and a window resize at a different render scale.

Frame rate alone cannot see a wrong store action. That is why P1's exit criterion is the comparison
and not the counter, and why the same rule applies to the load and store actions introduced by any
later phase.

# What not to do

- **Do not fuse pack full-screen passes.** A pack's later composite may sample an earlier one at an
  arbitrary offset, so the intermediate must exist as a complete texture. Fusing the engine's own
  internal passes is a different and much smaller question.
- **Do not write a GLSL optimiser.** The Metal compiler already folds constants, eliminates dead
  code, performs common-subexpression elimination and inlines. P4 removes bindings and copies by
  reachability, which is not the same activity.
- **Do not turn on automatic float-to-half.** Depth, world position, normals, shadow coordinates and
  temporal accumulation are the values most likely to be hurt, and their failure modes are exactly
  the ones a screenshot comparison attributes to the wrong change.
- **Do not enable aggressive fast-math by default.** Reassociation changes the arithmetic between
  two pipelines that have to agree, which is how a depth or a temporal mismatch appears.
- **Do not change the pack's mathematics.** Optimise the compatibility glue this engine generates,
  not the lighting, BRDF, temporal antialiasing, screen-space reflections or shadow code a pack
  author wrote.
- **Do not let a Metal 4 type name reach `common/`.** That is what keeps P5 a second implementation
  instead of a rewrite.

# References

Apple documentation, cited above next to the phase that follows it:

- Metal: https://developer.apple.com/documentation/metal
- Understanding the Metal 4 core API:
  https://developer.apple.com/documentation/metal/understanding-the-metal-4-core-api
- Using the Metal 4 compilation API:
  https://developer.apple.com/documentation/metal/using-the-metal-4-compilation-api
- Using function specialization to build pipeline variants:
  https://developer.apple.com/documentation/metal/using-function-specialization-to-build-pipeline-variants
- Render passes: https://developer.apple.com/documentation/metal/render-passes
- Resource fundamentals: https://developer.apple.com/documentation/metal/resource-fundamentals
- Resource synchronization:
  https://developer.apple.com/documentation/metal/resource-synchronization
- Memory heaps: https://developer.apple.com/documentation/metal/memory-heaps
- Shader libraries: https://developer.apple.com/documentation/metal/shader-libraries
- Machine learning passes:
  https://developer.apple.com/documentation/metal/machine-learning-passes
- GPU counters and counter sample buffers:
  https://developer.apple.com/documentation/metal/gpu-counters-and-counter-sample-buffers
- Capturing Metal commands programmatically:
  https://developer.apple.com/documentation/metal/capturing-metal-commands-programmatically
- Tailor your apps for Apple GPUs and tile-based deferred rendering:
  https://developer.apple.com/documentation/metal/tailor-your-apps-for-apple-gpus-and-tile-based-deferred-rendering
- `MTLStorageMode.memoryless`:
  https://developer.apple.com/documentation/metal/mtlstoragemode/memoryless
- MetalFX: https://developer.apple.com/documentation/metalfx
- Metal, What's New: https://developer.apple.com/metal/whats-new/
- Discover Metal 4 (WWDC25): https://developer.apple.com/videos/play/wwdc2025/205/
- Go further with Metal 4 games (WWDC25): https://developer.apple.com/videos/play/wwdc2025/211/
