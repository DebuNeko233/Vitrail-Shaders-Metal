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

## The first measured baseline

Phase P0 produced a probe; this is the first run that used it. It is one session, on one pack, on
one machine, so it is a starting point rather than a general law, and every phase below that names a
number is judged against a re-run of it rather than against this paragraph.

- **Where.** Photon v1.3b at the tester's settings, on Vitrail `aef3db09` (the merge of the launch
  argument removal, an ancestor of the current `dev`) with the companion Metallum on Apple Silicon.
  The pack's window is 3600x2038, so one RGBA16F target of that shape is 56 MiB.
- **The window.** 600 frames, about seventeen seconds, sixty-two per cent of them drawn at roughly
  35 frames per second.
- **Attachment traffic, per frame: 199 MiB loaded and 313 MiB stored.** In target terms that is
  about three and a half loads and five and a half stores of a 56 MiB target every frame. The store
  figure is the one P1 exists to move, and it is large because the store action is `Store` for every
  attachment of every pack pass.
- **Encoder boundaries, per frame: 4.9**, of which 3.9 are a render-pass configuration change and
  1.0 is the frame's submission.
- **Bindings, per frame: 222** - 71 textures, 71 samplers, 49 buffers, 13 pipelines, 11 scissors and
  7 viewports. The texture and sampler counts are equal because a texture bound with a sampler
  counts as one of each.
- **Pipeline state creation: 440 over the session, 32 milliseconds in total.** This one changed the
  plan, and the phase it changes is P3.

**What that window was, and what it was not.** Not one of its 600 frames is a frame of a drawn pack.
The session's only `first full frame` arrives at `01:03:51`, eleven seconds after the window closed,
because two loads in a row were replaced before either of them finished. So every figure above
describes **warming** frames - the ones this engine spends with the level skipped - and not the frames
P1 is about. The first window that does contain a drawn pack was taken later, on the same machine, the
same pack and the same 3600x2038 window: **1689 MiB loaded and 2066 MiB stored per frame, 34 encoder
boundaries of which 33 are a pass configuration change**, over 600 frames of Photon at 35 frames a
second. Eight and a half times the load traffic and seven times the boundaries, which is the
difference between a frame with no level in it and a frame with one. That is the number P1's target
should have been, and the correction moves the phase's prize up rather than down.

**What a byte figure here is and is not.** It is what the engine asked Metal to load and store, from
the actions it chose and the sizes of the attachments it chose them for. It is not a measurement of
what the hardware moved: a driver is free to elide a store whose contents nothing reads, and part of
what P1 is for is to stop asking. So the number is the ceiling on the traffic, and the win is the
gap between it and the wall time.

## The run that is not a baseline, and the load it did measure

A second log arrived with the probe armed and reads, at first glance, as the same frame measured
cheaper:

| | window covers a pack | window covers no pack |
|---|---|---|
| encoder boundaries, 600 frames | 2957 (2358 of them a pass change) | 1335 (736) |
| attachment loaded, MiB | 119595.9 | 56611.1 |
| attachment stored, MiB | 187947.4 | 113925.7 |

Same pack, same 3600x2038 window, same 21 programs, and the two rows are not the same 600 frames.
The second session opened with no pack applied: the probe spent its whole budget on the frames
before one, and the pack was read twenty-two seconds after the window had closed. Nothing was
halved and no light pack has been measured.

The instrument could not have been aimed at anything else. `MetalFrameProbe` asked its marker once,
before the first frame, so a window could only cover frames a launch reached by itself - which is
why the first log is usable: it opened with a pack already selected. The companion backend now asks
the marker again while the probe is off, at most once a second, and opens a window on the marker's
return rather than on its presence, so a marker left in place still arms the window that read it and
no other (`metallum#5`, with `tools/ci-frame-probe.py` pinning the shape on that side).

**A third window landed on a third kind of frame, for the reason that shape does not fix.** The next
log was armed by a marker already in place before the launch, so its budget went on the frames before
there was a world to draw: the pack is applied at `02:15:28`, the integrated server only starts at
`02:17:33`, and the 600 frames counted between `02:15:27` and `02:15:39` are the main menu. They read
829 encoders, 16366 MiB loaded and 23978 MiB stored - lower than either the pack window or the
no-pack window, and measuring a menu. No instrument can tell a frame worth counting from one that is
not, so the marker has to be absent before the launch and created once the world is up; that is the
only way to arm this probe at something real.

What the run does measure is the load, because it is the one it starts cold. Four loads across the
two logs, and what each of them paid:

| load | archive | translating | flattening | making modules | leftover pipelines |
|---|---|---|---|---|---|
| Complementary, at launch | 793 ms | 299 ms / 56 calls, 48 served | 311 ms / 90 units | 39 ms / 124 modules | 62 pipelines, 707 ms |
| Photon, switched to | 1100 ms | 1591 ms / 71 calls, 0 served | 330 ms / 86 units | 1951 ms / 124 modules | 62 pipelines, 3146 ms |
| Photon, reopened in the same session | not read | 521 ms / 80 calls, 63 served | 353 ms / 77 units | 1553 ms / 324 modules | 155 pipelines, 2845 ms |
| Photon, cold on a new build | 1488 ms | 2256 ms / 80 calls, 0 served | 713 ms / 163 units | 3524 ms / 328 modules | 155 pipelines, 4677 ms |

**Only the modules the store missed cost anything.** The module cache lines in the same two logs
say how many were served and how many were built, and they line up with the times above at twenty
to twenty-eight milliseconds a built module:

- Complementary, at launch: 229 served, 0 built - and making modules cost 39 ms.
- Photon, reopened: 304 served, 73 built - and 73 at roughly 20 ms is the 1553 ms recorded.
- Photon, cold: 250 served, 124 built - and 124 at roughly 28 ms is the 3524 ms recorded.

So `making modules` is shaderc and SPIRV-Cross compiling the ones the module store did not answer,
and it is the largest single item in every cold load here. It also sharpens the question rather than
answering it: the reopen served 304 modules and still built 73, from one pack, in one session, on one
settings set, with the translation store answering 30 of 30 at its own door. What those 73 differ by
in the module key is worth an answer before any cache is widened.

**A third log answers that question, and the answer withdraws it.** It reaches the same pack with
both stores already holding the edition, and builds nothing at all: four loads, **1317 modules served
and 0 built**, at 32 to 100 milliseconds of `making modules` for 124 to 377 modules each time, with
the translation store answering 30, 30, 15 and 58 programs and translating none of them. So a load at
the edition the store already holds compiles no module, and the 73 above cannot be the reopen's: the
module-cache line that reports them spans the tail of the preceding switch as well. What is left is
narrower and cleaner than the question it replaces - **the whole multi-second cost is the modules
built when the edition changes**, which only a rebuild does, and nothing else in either store misses.

**How long the wait is.** Every load that is not interrupted prints one line for its first full
frame, so the wait is measured rather than inferred:

| load | pack opened | first full frame | waited | what it was doing |
|---|---|---|---|---|
| Photon to Complementary, both stores warm | 02:17:43 | 02:17:44 | 1 s | 155 pipelines, 1218 ms of background work |
| Complementary back to Photon, both stores warm | 02:17:53 | 02:17:54 | 1 s | 155 pipelines, 1385 ms |
| Photon, reopened in the same session | 01:03:45 | 01:03:51 | 6 s | 155 pipelines, 2845 ms, and 73 modules built |
| Photon, cold on a new build | 01:38:31 | 01:38:40 | 9 s | 155 pipelines, 4677 ms, and 124 modules built |

The first two rows are the same packs on the same machine and window as the last two, and the
difference between them is the caches: **one second against six**, with the leftover-pipeline work
still 1.2 to 1.4 seconds of it. So the wait the loading page covers is about a second when the stores
hold the edition, and the seconds it was written for are the rebuild case.

The rest of a cold wait is visible in the same seconds: the world's chunk sections are all built
again because the mesh has to start carrying what the pack reads, 826 MiB of colour targets are
allocated at 3600x2260, and the chain's leftover pipelines are compiled ahead of their first draw.
Two of the first log's three opens never reach a line at all, because the next switch arrived first.

**The cold row is what a development build always sees, not a worst case.** Both disk stores name
their directory after `Vitrail.cacheEdition()`, which is the version and the game for a release and
adds the commit for a development build (`Vitrail.java:99-112`). A tester who rebuilds reads an
edition of its own and starts from nothing, while the previous build's units sit unharmed under
their own directory. That is intended - a developer's caches are worth nothing across two builds -
and it has one consequence for this phase: a run-for-run comparison across a rebuild measures the
store, not the engine. A warm number has to be taken twice on one build, or across two loads inside
one session.

## The GPU trace

P0 asked for one capture of a frame, kept beside the numbers. Three `Metal System Trace` recordings
have now been taken on the tester's Apple M5 Pro - 28 s over two pack switches, 14 s of steady play,
and 14 s over a third switch - and the counts they yield are in `MetalFrameProbe`'s terms because the
trace is read through `xctrace export` rather than by eye.

**Steady play is GPU-bound, without a gap.** Over the 14 s window that contains no load and no
switch, the GPU's shader cores are busy for 13.65 s of 14.04 - **97.3 per cent**, with every single
second of the window between 0.99 and 1.03 s of activity. The split is `Fragment 12.63 s`,
`Vertex 2.02 s` and `Compute 2.03 s`, so roughly three quarters of the work is fragment work. That
settles P1's premise on hardware rather than on reasoning: the frame is limited by GPU work, so GPU
work removed is frame time returned, and the attachment loads and stores P1 is aimed at are fragment
work.

**A pack switch is four seconds in which no frame is submitted at all.** In the switch recording the
client submits nothing - not one command buffer - for three whole seconds, and the GPU sits at six to
seven per cent, with six Metal allocations across those seconds. Around it, one second of half rate
before and a burst of 75 submissions after. So the wait is not a slow frame; it is no frame.

**What fills it is the world being rebuilt, not the shaders being compiled.** The log for the same
session says `The block ids moved, from 23877 states to 32194. The sections carry them, so they are
all built again` in the same second the switch starts, and the next thing the log says is a chunk
pass. Three independent readings agree that compilation is not the cost: the switch's own report
gives 672 ms of archive, 178 ms of translation over 66 calls with 58 programs served from the store,
246 ms of flattening and 222 ms over 332 modules with **none built**; the driver's shader compiler is
**idle** for the whole stall, having done its 3.69 s of work in the four seconds *before* the switch
while the game was still playing; and the client is otherwise silent.

**The same switch took one second in another session.** `Photon` to `ComplementaryReimagined` on the
same machine, the same 3600x2038 window, with the same block-table move and the same "sections are
all built again" announcement, reached its first full frame **one second** after the pack opened in
the 02:23 session and **five seconds** after it in the 02:41 one, losing one frame in the first and
three seconds of frames in the second. The compile figures are the same to within noise in both. So
the variable is the rebuild, and the number of sections it has to rebuild, and not the engine's own
translation or module work.

**The loading page covers it, and only because nothing is drawn.** The tester reports the page
standing through the stall. The code does not hold it there: `warming()` is `!drawable()`
(`PackChain.java:816`, `:2934`) and `drawable()` becomes true at the moment the log says the chain
can draw (`:2771-2775`), which is roughly four seconds before the first full frame. What keeps the
page on screen is that the client presents no frame in between, so the page is the last thing
presented and the screen holds it. That is worth knowing before anyone changes the predicate: the
page is not standing where it was designed to stand, and a frame that arrives a second earlier would
replace it with the empty world.

**What a Metal trace cannot say, and what that costs.** There is no per-pass attribution in it. The
GPU activity intervals are keyed by driver objects (`0xcb0a...`, 63 of them in one recording) that
intersect none of the 80986 labelled objects and none of the encoders; the encoder table carries only
`Encoding` events, which are CPU-side; there is one command buffer a frame, so per-command-buffer
granularity is per-frame; and joining GPU intervals to encoders by time overlap leaves 100983 of
117988 unattributed, because GPU execution trails encoding. Instruments' own Encoder Hierarchy shows
the passes - `MetalCommandEncoder.java:435` already pushes a debug group named by
`descriptor.label()`, the same label `PassTimings` reads - but `xctrace export` does not carry debug
groups. So the pass names are in the trace and the per-pass GPU times are not reachable from it.
P1 does not need them: `MetalFrameProbe`'s `loadedMiB` and `storedMiB` are exactly the quantity P1
moves, and with the GPU saturated a fall in them is a rise in frame rate.

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
4. **Compilation is paid twice for the same shaders.** One load compiles 155 leftover pipelines, and
   there is no persistent pipeline cache and no binary archive in the tree (`.context/STATE.md`, the
   `5ab260ab` session). What this is *not* is the pack switch's stall: the trace above puts four
   seconds of that on the world's rebuild, with both stores answering, the module store building
   nothing, and the driver's own shader compiler idle for the whole of it.
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
kept with the numbers. The trace is taken - three recordings, in the section above - and so is the
reproduction: two sessions of one build report **the same 829 encoders, 230 pass changes and 599
submissions** over their 600-frame windows, attachment bytes within one per cent of each other, and
the same 1317 modules served with none built. What is still owed is the light pack's numbers.
"Within noise" is a claim about one build: a development build reads a cache edition of its own, so
two runs either side of a rebuild are two cold starts and comparing them compares that, not the
engine.

**Where it stands.** The probe is built and in the companion backend: `MetalFrameProbe` counts all
four, off unless armed by `-Dmetallum.probeFrames=true` or a `metallum/probe-frames` marker in the
game directory, and `tools/ci-frame-probe.py` pins it. Its first shape asked the marker once, before
the first frame, so a window could only cover frames a launch reached by itself; the backend now
asks it again while the probe is off, at most once a second, and opens a window on the marker's
return rather than on its presence (`metallum#5`), so a session can be told to count once the pack it
is meant to measure is the one in force. The marker has to be **absent before a launch** and created
once the world is up, which is the one part of arming it that no contract can enforce and the part
three supplied logs got wrong in turn. **A pack window has been counted**, and no longer by hand:
`metallum/tools/run-vitrail-performance.sh` stages a pack and the options file beside it, writes the
pack selection and the Metal preference the settings UI would have written, launches the dev client
straight into a world with `--quickPlaySingleplayer`, waits for the pack's own first full frame, arms
the probe only then, waits out its window, collects the log and a picture, and stops the client. Two
runs under two sets of switches are the comparison it exists for, and
`metallum/tools/vitrail-performance-compare.py` prints them side by side out of the probe's own line;
`metallum/tools/ci-vitrail-performance.py` pins the order that makes a window worth counting.

The harness's own first launch is also what found the reason the earlier windows could not land on a
pack: the pack was coming up on its **own defaults**, because the options its owner had chosen live
in a file beside the archive and only the archive was being staged. At those defaults one of the
pack's programs needs a sampler slot Metal does not have, which is a fault in the backend's
direct-resource decision rather than in the pack, and it is fixed in `metallum` by counting the slot
the last sampled image lands in instead of counting the sampled images.

What is still owed is the light pack's numbers, a picture on both sides of a comparison - macOS
refuses screen capture to the process the harness runs under until it is granted in System Settings -
and a **frame time**: the probe counts bytes and bindings, and the only rate in the log belongs to the
first full frame, which is a warming window and not a measurement.

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

**Where it stands.** Both halves are written and off behind one switch,
`-Dvitrail.elideTargetTraffic`. The load half is the pass's own fact - a draw over the whole screen
that writes every pixel of its targets has no use for what stood there - and it needs nothing across
the seam, because the descriptor can already say clear-or-load. The store half is the frame's fact,
whether anything reads what a pass leaves, and the descriptor cannot say it at all, so it crosses as
a narrow capability: `AttachmentContents` carries two booleans per attachment slot and nothing else,
the answer nobody gives is the one that changes nothing, and both directions are pinned by
`tools/ci-frame-probe.py` on that side and `tests/test_pack_pass_writes_every_pixel.py` on this one.
The two doors differ on purpose - a load is only elidable for a draw that covers the whole target,
while a store needs only the absence of a reader - and the chain publishes both per pass.

**Measured.** One build, one scene, two runs: photon v1.3b with the options its owner chose, the same
world, an 1800x1019 window (a 3600x2038 drawable), 600 frames a run, the first run without the switch
and the second with it. The scenes are the same to within a per cent on every binding count.

| counter | off | on | change |
| --- | --- | --- | --- |
| loadedMiB | 938906 | 633286 | -32.6% |
| storedMiB | 1126032 | 1122679 | -0.3% |
| encoders | 19691 | 19752 | +0.3% |
| passChanged | 19091 | 19152 | +0.3% |

The load half is the win it was supposed to be: 509 MiB a frame less read, 1565 to 1056, and about a
sixth of the frame's attachment traffic across the two counters together (3441 MiB a frame to 2927).

**The store half measured nothing, and the chain says why.** It fires only where nothing reads what a
pass leaves, and in this pack almost everything a pass leaves is read: the frame is a sequence of
full-screen programs handing one target to the next, and twelve of the pack's fifteen colour targets
are kept between frames, so the last write to each of them is read by the next frame before it is
written again. The one unread target the chain did find - one of `composite4`'s two - is one target
of one pass in a frame of thirty-three. The mechanism is correct, off by default, and free when
nothing is unread, which is why it stays: a pack whose last write to a target is genuinely dead is
what P4's reachability work is for, and this is the door it will come through.

`-Dvitrail.narrowStorageBoundary=true` was on in the same run and moved no encoder boundary either
(19691 to 19752 is noise, in the other direction). That is the expected reading rather than a fault:
the frame already opens about one encoder per pass, so a rule that would force an extra boundary has
none left to force. The pack does read storage images - it declares four and reads two of them as
samplers - so the mechanism has something to narrow in principle and nothing to narrow here.

What is still owed is the picture on both sides, and a frame time to go with the bytes.

**Exit criterion.** Attachment bytes per frame fall on the P0 capture, the bindings and encoder counts
do not regress, and the regression set in "Regression, not just frame rate" is unchanged, image for
image. The counter alone does not close this phase. On the pack measured above the whole of that fall
is the load half, and the store half is worth what the chain is asked for: a phase that only ever
claims the load half has to say so, and this one does.

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

**For.** This is the phase a player feels most, and what a player feels has now been measured rather
than inferred. The stall is real: a pack switch is **four seconds in which the client submits no
frame at all**, and the GPU sits idle through it. But the trace above takes that stall away from this
phase. It is **not** pipeline state creation, which one run measured at 440 creations costing **32
milliseconds in total**; it is **not** translation or module building, which the switch's own report
puts at 178 ms and 222 ms with the module store building nothing; and it is **not** the driver's
compiler, which is idle for the whole of it. It is the world: the block-id table moved from 23877
states to 32194, every section had to be rebuilt, and the seconds are that rebuild. So the case for
a compilation cache is now the *load*, where the numbers below stand, and not the switch, where the
same switch took one second in one session and five in another with identical compile figures.

What the run shows instead is where the seconds are: **translation** at 1591 ms over 71 translator
calls with **nothing served from the translation cache**, chain unit flattening at 620 ms over 160
units, and 46 modules built by the compiler in a load the module cache otherwise served 78 of. The
translation cache is not broken - other moments in the same session report 48 and 63 programs served
from it - so the question this phase now asks is why a cold-ish load translates everything again
while a warm one does not, and what in the two caches misses.

A second log, taken cold and decomposed in the baseline section above, makes the module store the
largest item of the load rather than one of three: **making modules** cost 3524 ms over 328 modules
with 124 of them built, and 1553 ms over 324 with 73 built on a reopen that the translation store
answered completely. A module the store answers is free - 229 served for 39 ms - and one it does not
is 20 to 28 ms. So the size of this phase is a count: how many of a load's 324 to 377 modules the
store misses, and what those differ by in the key. That question comes before any widening of a
cache, because the reopen already says the answer is not that the store is too small.

That also demotes one argument for P5: the "same shaders, different colour state" pattern is real
(440 pipeline states over 46 modules, so roughly ten states per module) but it is worth thirty
milliseconds, not seconds. Specialisation remains the right shape for that work; it is no longer a
compilation-time lever.

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
   interface variables already exclude. **The baseline answers part of this already**: the same
   session logs `Samplers bound from what a module reaches: 194 dropped across 33 of the 46 pack
   modules walked`, so sampler reachability is not a proposal here, it is something the engine
   already does at bind time. What is left for this phase is narrower and should be checked before
   any of it is built: which parts of the plan still run on declaration text rather than on
   reachability - `TargetCopies` is the one the repository has already recorded as over-counting -
   and whether those parts are worth the reflection plumbing at all.
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

1. **`HostReport` and the status pages changed together, and half of that is already done.**
   `HostReport.otherBackend()` no longer documents Vulkan as the production path, and the developer
   switch that used to gate Metal (`-Dvitrail.experimentalMetal=true`) has been removed, so a session
   on Metal is now accepted on its own answers alone: a compatible Metallum, that build's Prefer
   Metal, and a device that came up. What remains under this step is the rest of the page's Vulkan
   guidance, which still sends a non-Metal session to a graphics API this phase is meant to delete.
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
