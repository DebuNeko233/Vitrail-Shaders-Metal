# Vitrail performance optimisation  -  status report

Measured on the reference configuration unless a line says otherwise: Photon v1.3b, render scale 55, shadow map
scale 100, fullscreen at the display's 1920x1200 mode with the world drawn at 1056x660, camera pinned at
`548.5,63,-248.5 yaw 0 pitch 7.8`, 600 frames, Unlimited FPS, vsync off, MAILBOX with `displaySyncEnabled=false`.
Every number here comes from an arm in `metallum/run/`; anything not run is `NOT MEASURED`, never estimated.

**The render target is named by the run's own log, the harness does not pin it, and it has two states.** The
display has a 1920x1200 mode and a 3840x2400 one, and `--fullscreen` - written into `options.txt` before every
run - lands on either: every anchor arm (`run/p0-base`, `run/p0-repeat`, `run/p6-pixels`, `run/p6-mips`,
`run/p7-reuse`) reports `The world renders at 1056x660 for a 1920x1200 window`, while one arm of a later session
reported `2112x1320 for a 3840x2400 window` - four times the pixels, 14.4 ms a frame against 7.3. So the anchor
was fullscreen at the display's 1920x1200 mode, which is not the same thing as the windowed 1920x1200 an earlier
round compared it against: `run/p0-windowed` drew the same structure at `loadedMiB` 252660.8 and
`run/shadow-walk` at 312294.5, against the anchor's 93922.0. **The check before reading any time column is the
structural counters and not the window size or the screenshot's dimensions**: `loadedMiB` 93922.0 with `encoders`
21435 is the anchor's target. A session whose arms disagree about it is refused by
`tools/vitrail-performance-compare.py` ("the two arms did not render the same window"), and three sessions taken
for this pass were mixed and are not used here. The one kept arm set is `run/shadow-decomp2`, whose six arms all
reproduce the anchor's target.

## Starting SHAs

Vitrail: `01d0b1c0`   Metallum: `33866a7`

## Benchmark protocol

pack Photon v1.3b · preset 55 % · 1920x1200 window, world drawn at 1056x660 · frames 600 · warm-up 25 s
settle · FPS limiter Unlimited · present mode unchanged within a pair · the pinned camera above · one display
mode across every arm of a session, checked rather than assumed.

## Initial baseline (`run/p0-base`, `run/p0-repeat`)

wallP50 7.29 / 7.27 ms · wallP95 8.42 / 8.61 · wallP99 9.00 / 9.41 · wallMax 9.93 / 9.87
GPU 7.34 / 7.29 ms p50 (gpuMs 4395.30 / 4366.07 over 600)
passes 20928 / 20922 a window (34.9 a frame) · encoders 21440 / 21435 · passChanged 20840 / 20835 · submit 600
copies (blits) 6600 · blittedMiB 22159.3 · clears (clearEncoders) 600 · depthAttachments 4800
loadedMiB 93943.3 / 93922.0 · storedMiB 132773.6 / 132752.3 · compiles 0 · pipelineIdentities == pipelineKeys 345

**The scene's own spread**: 0.03 % on the structural counters, 0.3-0.7 % on the time columns. **Picture
comparison across launches is worthless on it**: the same two arms differ by a mean channel difference of 30.68
with the counters agreeing to 0.02 %, because the sun, the weather phase and the pack's temporal history differ
per launch.

## §55 Optimisation table

| Optimisation | Baseline ms | New ms | Gain | Copies Δ | Passes Δ | Submits Δ | Default? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Shadow reuse | 7.30 | - | - | - | - | - | no (pack refuses reuse: it voxelises); what the stage costs is decomposed under Shadow decomposition |
| Target copy elision | 7.31 | 7.25 | -0.8 % (inside the floor) | blits 6600 → 4800, blittedMiB 22159.3 → 21260.2 | 0 | 0 | no  -  rejected, measured |
| Feedback copies | NOT MEASURED | - | - | - | - | - | no  -  zero copies taken on this pack |
| Attachment traffic | 7.22 | 7.30 | +1.1 % (inside the floor) | loadedMiB 93943.3 → 65229.4 (-30.6 %), storedMiB -1.2 % | 0 | 0 | no  -  rejected, measured |
| Storage boundary | 7.31 | 7.31 | 0 | 0 | 0 | 0 | no  -  rejected, measured (zero boundaries merged) |
| Mipmap planning | 7.30 | 7.30 | 0 (telemetry only) | 0 | 0 | 0 | no change made; 2 chains a frame measured and the reduction priced by removal at about 0.045 ms a frame |
| Compute bindings | 7.27 | 7.26 | 0 (CPU change) | 0 | 0 | 0 | **YES** - the 3 reusable mutable binding maps are no longer allocated per dispatch; the 3 immutable `Map.copyOf` snapshots remain |
| Capability lookup | NOT MEASURED | - | - | - | - | - | no change made |

## Definition of Done (§57)

| item | state | evidence |
| --- | --- | --- |
| 57.1 measurement complete | partial | anchor + repeat taken; final baseline after the one kept change taken (`run/p7-reuse`) |
| 57.2 major GPU work understood | **yes for this pack** | passes, copies, clears and submits known; the shadow stage decomposed by removal arms (terrain raster 0.04 ms, translucent 0.25 ms, entities 0 on an entity-free fixture, voxelisation UNKNOWN); mipmap volume counted (2 chains a frame, 11 levels, 464402 pixels) and priced (0.045 ms, 1.0 per cent) |
| 57.3 target copy policy | **answered** | rejected, measured: 3 blits a frame removed, no time; refusal half proved on the history fixture |
| 57.4 feedback copies | **answered** | zero copies on this pack; `PackChain:2066` is live, so it is a fact about the pack |
| 57.5 attachment traffic | **answered** | rejected, measured: -30.6 % loaded bytes, no time |
| 57.6 storage boundary | **answered** | rejected, measured: zero boundaries merged, because every pass answers "may read" |
| 57.7 compute hot allocations | **answered** | kept: 3 reusable mutable binding maps a dispatch no longer allocated, the 3 immutable `Map.copyOf` snapshots remain (downstream ownership isolation preserved), same 6.5 bindings, same scene, time unchanged |
| 57.8 no unmeasured high-value switch | **yes** | all three named switches have conclusions |
| 57.9 correctness corpus | **not met** | fixtures cover the elision cases; no cross-pack corpus run this pass |
| 57.10 reload/lifecycle | **not met** | NOT MEASURED |
| 57.11 architecture clean | **yes** | Vitrail gained no Metal type, no generation path, no command type |


## Mipmaps (phase 6, `run/p6-pixels`, 09:23 and `run/shadow-decomp2`, 09:40)

Load time: **2 targets carry a chain** (colortex5, colortex11) because one program reads them at a lod, 7 MiB
more. Per frame, from the census this pass added:

```
Mip chains: 275 reduced over 1000 ms (275.0 a second), 3025 levels (11.0 a chain),
            63855275 pixels (232201 a chain)
```

- **2 chains a frame**, one per chain-carrying target;
- **11 levels a chain**, which is the whole chain from level nought;
- **pixels reduced: 464402 a frame, 63855275 a second**, counted from each target's own width and height at
  every level the chain holds rather than estimated; the arithmetic is under Mipmap cost;
- **about 0.045 ms a frame, 1.0 per cent**, priced by removing the chains from a frame otherwise identical.

The arm is the reference scene exactly (`encoders` 21435, `loadedMiB` 93922.0, `blits` 6600, `wallP50` 7.27), so
counting costs nothing measurable. Nothing was optimised here: the code already skips a valid chain, the only
candidate left is invalidating less often, and the measured cost is below the gate that would justify it.


## The two open gates, and exactly what each needs

### 57.9 Cross-pack corpus

The harness can reach the *load* and *one pinned window* for any pack handed to it:
`tools/run-vitrail-performance.sh --pack <zip> --fixture|--fullscreen ...`, with the two scene guards added this
pass refusing a window that did not draw the pack. That covers "load" for Photon (done), Complementary
(staged at `.perfstage/ComplementaryReimagined_r5.9.1.zip`, loaded and run once during the ACL work) and
MakeUp-UltraFast (staged). NOT MEASURED in this pass for BSL-family, a compute/storage-heavy pack, a
shadow-mipmap pack and a translucent feedback pack, because none of them is in this machine's staging folder.

What the harness cannot do, for any of them: **walk around, look at water or translucency, change dimension,
reload with F3+T, or resize the window.** Those are section 51's other four checks and section 52's visual
checklist, and they need either a session with a person at the keyboard or a new harness capability that drives
them. This report does not claim them.

### 57.10 Reload and lifecycle

NOT MEASURED, and it is not reachable from the harness as it stands: one arm is one launch, one world, one pack
and one window. The checks the plan asks for - F3+T, pack switch, world leave and join, dimension change, resize,
shutdown, each watched for a stale texture, an old ping-pong half, a use-after-close, a lost target or an old
shadow map - are hand-run session work.

**What to look for, so the check is worth something when it is run.** After each of those six events, the frame
must still be the pack's picture and the log must not carry a refusal, a lost target or a texture error; the
steady-state counters should return to the reference band (`renderPasses` about 20900, `blits` 6600,
`loadedMiB` about 93900) rather than drifting across the event. A pack switch and a world join are the two where
a lost target would show; F3+T and a resize are the two where a stale ping-pong half would.


## Compute allocation correction

The phase 7 change is often summarised as "maps a dispatch: 3 to 0", which reads as though a compute dispatch
allocates no map at all. It does not, and the difference matters to anyone reading the code next:

```
before:  3 x new LinkedHashMap      + 3 x Map.copyOf snapshots
after:   0 x new LinkedHashMap      + 3 x Map.copyOf snapshots
```

What changed is the three **reusable mutable binding maps** (`PackComputeBindings.Scratch`, owned by the program
and cleared per dispatch). What remains is the three **immutable `Map.copyOf` snapshots** inside `Resolved`, and
they are deliberate: they are what stops a backend holding a map that a later dispatch then clears and refills
underneath it. Removing them would trade a measured 1644 short-lived maps a second for an ownership question
nobody has measured, and it is not proposed here.

So the measured claim is: **Three reusable mutable binding maps are no longer allocated per dispatch. The three
immutable `Map.copyOf` snapshots remain, preserving downstream ownership isolation.** Read as "a compute dispatch
allocates no map at all" the change is misdescribed, and that is how this report described it until the wording
was corrected here.


## Mipmap cost (`run/p6-pixels`, 09:23 and `run/shadow-decomp2`, 09:40)

```
chains/frame:   2.0 (275 reduced over 1000 ms at 137 frames a second)
levels/chain:   11.0
pixels/frame:   464402 (232201 a chain, 63855275 a second)
bytes/frame:    about 3.5 MiB (8 bytes a pixel)
GPU ms/frame:   about 0.045 ms
% frame:        about 1.0 %
```

**The pixels are counted from each target's own dimensions and level count, not estimated.** Level one down to
the last, at the size that level really has: 528x330 + 264x165 + 132x82 + 66x41 + 33x20 + 16x10 + 8x5 + 4x2 +
2x1 + 1x1 = 232201 a chain, two chains a frame, 63855275 pixels a second. The eight bytes a pixel is the
engine's own load-time accounting, checked against the arithmetic rather than assumed: both chain-carrying
targets are doubled, so four chains of 232201 pixels are charged `7 MiB more`, and 4 x 232201 x 8 = 7.09 MiB is
the only width that lands inside that figure (four bytes would be 3.5 MiB, sixteen would be 14 MiB).

**The GPU time is measured, and by removal rather than by a clock.** There is no per-pass GPU clock on this
backend, so the reduction was priced the only way it can be: two arms of one session whose frames are otherwise
identical - same encoders, passChanged, loadedMiB, storedMiB, depthAttachments, blits, viewport, scissor and
pipeline identities, and a `blitEncoders` count of **3000 against 2400**, which is the one blit encoder a frame
the two chains share. `gpuMs` 4377.97 against 4332.61: **45.4 us a frame, 1.04 per cent of 7.30 ms**. The
session's own floor is read off the `noentity` arm, whose frame is identical in every structural counter:
-0.06 per cent, so the 1.04 clears it by an order of magnitude. It is a small number and it is a measured one.

**Decision (section 6's gate).** 0.045 ms is below the 0.1 ms gate and 1.0 per cent sits at the 1 per cent one,
so the plan's own rule applies in the direction of *not* touching it: **not worth further complexity**. The one
reading that would reopen it is the same switch at 0.2-0.3 ms, and the repeat taken in the same session
(`nomips`, `gpuMs` 4775.52) read 9.1 per cent *slower* on an arm whose structural counters differ by 0.2 per
cent and which ran last: that is the session's spread talking, not the switch, which is why the finding is
stated as the clean pair and not as an average of the two.


## Shadow decomposition (`run/shadow-decomp2`, 09:40-09:44)

Six arms, one session, one display mode, camera pinned, 600 frames each, priced with the whole-frame GPU time the
driver answers with (`gpuMs` over 600 frames). **The per-pass table is not the instrument here**: on the Metal
path `MetalDevice.getTimestampNow()` is `System.nanoTime()` and the device reports a timestamp period of `1.0`,
so a `Vitrail shadow chunk` row is the **CPU cost of encoding that pass** and not the GPU time it took to run
(`metallum/docs/performance-testing.md` and `.context/STATE.md` carry that correction, and `docs/developing.md`
did not until this pass). The earlier statement in this report that the row was the frame's top cost at "21.3
per cent of stamped pass time" is therefore a share of CPU encode time and says nothing about the shadow map's
GPU cost.

| arm | switch | gpuMs / 600 | ms a frame | against plain | encoders | loadedMiB | depthAtt. | blitEncoders |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `plain` | none | 4377.97 | 7.30 | - | 21435 | 93922.0 | 4800 | 3000 |
| `noentity` | shadow entities off | 4375.25 | 7.29 | **-0.06 %** | 21435 | 93922.0 | 4800 | 3000 |
| `absorb` | mip chains off | 4332.61 | 7.22 | **-1.04 %** | 21435 | 93922.0 | 4800 | **2400** |
| `notrans` | translucent pass off | 4125.21 | 6.88 | **-5.8 %** | 20810 | 74615.4 | 4200 | 3000 |
| `noraster` | both chunk layers off | 4087.85 | 6.81 | **-6.6 %** | 20210 | 74615.4 | 3600 | 3000 |
| `nomips` | mip chains off (repeat) | 4775.52 | 7.96 | **+9.1 %** | 21475 | 94092.5 | 4800 | **2400** |

**The three arms that share a frame are the measurement; the two that remove passes are a bound.** `plain`,
`noentity` and `absorb` report the same encoders, passChanged, loadedMiB, storedMiB, depthAttachments, blits,
blittedMiB, viewport, scissor and pipeline identities - one frame, differing in one switch and, for `absorb`, in
the one blit encoder a frame the two chains share. Across those three the session's own repeatability is read off
`noentity` at **-0.06 per cent**, which is the floor a delta has to clear. `notrans` and `noraster` also remove
625 and 1225 encoders and 20.6 per cent of the frame's loaded attachment bytes, so their deltas are the component
*plus its attachment traffic* and not the raster alone.

| component | calls/frame | CPU evidence | GPU evidence | removable? |
| --- | ---: | --- | --- | --- |
| terrain/chunk (opaque) | 1 `drawChunkLayer(OPAQUE)` a frame; 734 sections kept, 298 of them carrying geometry, of 22104 loaded | the walk runs every frame, 137 walks a second, `SAFE_ZONE SWEPT r=128 z=32` | **about 0.04 ms a frame** (`noraster` minus `notrans`: 4087.85 against 4125.21), 0.5 per cent of the frame | **no**: the pack voxelises in its shadow stage, so the engine's reuse is refused and the map is drawn in 600 of 600 frames; these are the sections the light reaches |
| translucent | 1 `drawChunkLayer(TRANSLUCENT)` a frame | the same shadow pass wrapper | **about 0.25 ms a frame** (`plain` minus `notrans`: 4377.97 against 4125.21), 3.4 per cent, and it carries 625 encoders and 20.6 per cent of the frame's loaded attachment bytes with it | **no on this pack**: `shadowTranslucent` is the pack's own directive and `shadowtex1` is defined as the map *without* translucents (`ShadowTerrain:456-471`) |
| entities and block entities | **0 casters** on this fixture | the harness strips the measurement world's entities before every run ("Took the entities out of PerfWorld"), and the new caster census reports `0 frames gathered` for the whole session | **no measurable cost**: removing the shadow entity draw moved the frame by -0.06 per cent with every structural counter identical | nothing to remove here: the fixture has no movers. A world **with casters** is `NOT MEASURED` |
| voxel side work (the pack's own `imageStore` into its volume) | not separable | UNKNOWN | **UNKNOWN**: it is inside the same fragment program as the raster, and no switch takes it out without changing what the pack's shader does. No diagnostic switch was written for it, so it stays UNKNOWN rather than estimated | unknown |
| other (block entities, copy, shadow mip chain) | 1 `copyShadowDepth` a frame; 1 blit encoder a frame shared by the 2 chains | the chain is counted by the mip census and the copy is inside `blittedMiB`, which does not move | **about 0.045 ms a frame** for the chain (`plain` minus `absorb`), 1.0 per cent | no: the chains are what the pack's `deferred4` reads at a lod |

```
Shadow walk: 138 walks (137.0 a second), kept 734 a walk, drew 298 a walk, 22104 loaded,
             terrain=true culling=SAFE_ZONE SWEPT r=128 z=32
Shadow map: the opaque world was drawn into it 600 times in the last 600 frames
Shadow casters: 0 frames gathered, 0.0 entities and 0 block entities a frame
```

**What this answers, and what it does not.** Answered: the shadow stage's GPU cost is **not** in the 298-section
opaque terrain raster, which is 0.04 ms; it is in the **translucent pass at 0.25 ms**, and the two together are
0.29 ms of a 7.30 ms frame, 4.0 per cent. The earlier reading that pointed at the terrain raster was reading CPU
encode time. Not answered: the cost of the walk itself (CPU) and of the pack's voxelisation, both UNKNOWN.

**These arms are diagnostic only and NOT SEMANTICALLY CORRECT.** Each leaves the shadow map missing a piece of
what the pack asked it to hold, so every picture taken on one is wrong by construction (`absorb` against `plain`
is already a mean channel difference of 3.54). None is a candidate change, none is enabled by default, and no
reading from one may be used to decide what to delete. They price components; the code that arms them is off
unless a JVM property names it.

## Next optimisation decision

**C. Neither is significant or avoidable on the evidence measured; stop GPU optimisation.** The reasons are
measured now rather than inferred, and the largest one is named:

- **The shadow stage costs about 0.29 ms of a 7.30 ms frame (4.0 per cent), and the translucent pass is where it
  is spent, not the terrain.** Removal arms: the opaque shadow terrain raster is **0.04 ms** and the translucent
  shadow pass is **0.25 ms**, carrying 625 encoders and 20.6 per cent of the frame's loaded attachment bytes with
  it. The pack asks for both halves itself (`shadowTranslucent`, and `shadowtex1` defined as the map without
  translucents); the map is drawn in 600 of 600 frames because the pack voxelises, so the engine's reuse is
  refused; and the walk already keeps 734 sections of 22104. **This is shader-pack-required work rather than
  avoidable Vitrail overhead.**
- **The mipmap chains cost about 0.045 ms, 1.0 per cent** - below the 0.1 ms gate section 6 sets - measured by
  removing them from a frame identical in every structural counter but the one blit encoder a frame they share.
  The code already skips a chain that is still valid, so the only candidate left is invalidating less often,
  which needs a semantic proof and buys one per cent.
- **The entities and block entities cost nothing measurable on this fixture because the fixture has none**: the
  harness strips the measurement world's entities and the caster census reports `0 frames gathered` for the whole
  session, with the removal arm at -0.06 per cent.
- Every other measured item is already decided: copy elision and attachment traffic remove bytes and no time, the
  storage boundary merges nothing on this chain, feedback copies do not happen here, and the one CPU change worth
  keeping (the three reusable mutable binding maps) is kept.

**What would reopen it**, named exactly: (1) a world with casters, to price the entity and block-entity half
where there is something to price; (2) a per-pass GPU clock on the Metal path - Apple's counter sample buffer,
behind the existing off-by-default switch - rather than the host clock the table reports today; (3) a pack whose
shadow stage does not voxelise, where the engine's amortisation applies at all. None of the three is a change to
Vitrail's shadow code, and none may be replaced by a guess.

## Remaining cost, and why work stopped there

The frame is GPU-bound: `gpuP50` 7.30 against `wallP50` 7.29 on a frame of 7.30 ms, so the card is the limit and
the pack's own raster is what fills it. What Vitrail adds inside that frame is now decomposed rather than argued:

- **the shadow stage's two chunk layers: 0.29 ms, 4.0 per cent** (translucent 0.25, opaque terrain 0.04), both
  asked for by the pack, which voxelises so the engine's amortisation is refused;
- **the two mip chains: 0.045 ms, 1.0 per cent**, below the gate;
- **entities and block entities: nothing measurable**, on a fixture whose entities the harness strips;
- the four GPU-traffic switches were each measured and each answered "removes bytes and copies, moves no time";
- the CPU-side hot paths (§34/§37/§39) have no profiling evidence of significance, and the request that produced
  this report says not to pursue them further without one.

What is left unmeasured is named rather than hidden: the pack's **voxelisation** (UNKNOWN, inseparable from the
shadow raster without changing the shader), the **shadow walk's CPU cost** (UNKNOWN), a world **with casters**
(NOT MEASURED), and **per-pass GPU timing** on the Metal path, which needs Apple's counter sample buffer rather
than the host clock `PassTimings` records today.

## Vitrail performance optimisation complete?

**NO**  -  §57.9's corpus and §57.10's lifecycle items are `NOT MEASURED`, the pack's voxelisation and the
shadow walk's CPU cost are `UNKNOWN`, and the plan's own stop condition (§58) is not met. §57.2 is answered for
this pack and for no other.
