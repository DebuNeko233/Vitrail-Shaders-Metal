# Vitrail performance optimisation  -  status report

Measured on the reference configuration unless a line says otherwise: Photon v1.3b, render scale 55, shadow map
scale 100, fullscreen at the display's own 1920x1200, camera pinned at `548.5,63,-248.5 yaw 0 pitch 7.8`, 600
frames, Unlimited FPS, vsync off, MAILBOX with `displaySyncEnabled=false`. Every number here comes from an arm in
`metallum/run/`; anything not run is `NOT MEASURED`, never estimated.

## Starting SHAs

Vitrail: `01d0b1c0`   Metallum: `33866a7`

## Benchmark protocol

pack Photon v1.3b · preset 55 % · resolution 1920x1200 (display mode, fullscreen) · frames 600 · warm-up 25 s
settle · FPS limiter Unlimited · present mode unchanged within a pair.

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
| Shadow reuse | NOT MEASURED | - | - | - | - | - | no (pack refuses reuse: it voxelises) |
| Target copy elision | 7.31 | 7.25 | -0.8 % (inside the floor) | blits 6600 → 4800, blittedMiB 22159.3 → 21260.2 | 0 | 0 | no  -  rejected, measured |
| Feedback copies | NOT MEASURED | - | - | - | - | - | no  -  zero copies taken on this pack |
| Attachment traffic | 7.22 | 7.30 | +1.1 % (inside the floor) | loadedMiB 93943.3 → 65229.4 (-30.6 %), storedMiB -1.2 % | 0 | 0 | no  -  rejected, measured |
| Storage boundary | 7.31 | 7.31 | 0 | 0 | 0 | 0 | no  -  rejected, measured (zero boundaries merged) |
| Mipmap planning | 7.27 | 7.27 | 0 (telemetry only) | 0 | 0 | 0 | no change made; 274 chains a second measured |
| Compute bindings | 7.27 | 7.26 | 0 (CPU change) | 0 | 0 | 0 | **YES** - the 3 reusable mutable binding maps are no longer allocated per dispatch; the 3 immutable `Map.copyOf` snapshots remain |
| Capability lookup | NOT MEASURED | - | - | - | - | - | no change made |

## Definition of Done (§57)

| item | state | evidence |
| --- | --- | --- |
| 57.1 measurement complete | partial | anchor + repeat taken; final baseline after the one kept change taken (`run/p7-reuse`) |
| 57.2 major GPU work understood | partial | passes, copies, clears, submits, shadow frequency and traffic known; mipmap count measured (274 chains a second, 11 levels a chain), pixels reduced NOT MEASURED |
| 57.3 target copy policy | **answered** | rejected, measured: 3 blits a frame removed, no time; refusal half proved on the history fixture |
| 57.4 feedback copies | **answered** | zero copies on this pack; `PackChain:2066` is live, so it is a fact about the pack |
| 57.5 attachment traffic | **answered** | rejected, measured: -30.6 % loaded bytes, no time |
| 57.6 storage boundary | **answered** | rejected, measured: zero boundaries merged, because every pass answers "may read" |
| 57.7 compute hot allocations | **answered** | kept: 3 reusable mutable binding maps a dispatch no longer allocated, the 3 immutable `Map.copyOf` snapshots remain (downstream ownership isolation preserved), same 6.5 bindings, same scene, time unchanged |
| 57.8 no unmeasured high-value switch | **yes** | all three named switches have conclusions |
| 57.9 correctness corpus | **not met** | fixtures cover the elision cases; no cross-pack corpus run this pass |
| 57.10 reload/lifecycle | **not met** | NOT MEASURED |
| 57.11 architecture clean | **yes** | Vitrail gained no Metal type, no generation path, no command type |


## Mipmaps (phase 6, `run/p6-mips`, 02:02)

Load time: **2 targets carry a chain** (colortex5, colortex11) because one program reads them at a lod, 7 MiB
more. Per frame, from the census added this pass:

```
Mip chains: 276 reduced over 1006 ms (274.1 a second), 3036 levels (11.0 a chain)
```

- **about 2 chains a frame**, one per chain-carrying target - both are invalidated and rebuilt every frame;
- **11 levels a chain**, which is a full chain to level nought;
- **about 3036 level reductions a second**;
- **pixels reduced: 63 623 074 a second, 232 201 a chain, about 464 400 a frame** - counted from each target's
  own width and height at every level the chain holds, not estimated (`run/p6-pixels`, 09:23).

The arm is the reference scene exactly (`encoders` 21435, `loadedMiB` 93922.0, `blits` 6600, `wallP50` 7.27), so
counting costs nothing measurable. Nothing was optimised here: the code already skips a valid chain, and the only
candidate left is invalidating less often, which needs a semantic proof before a line of it is touched.


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

So the measured claim is: **three reusable mutable binding maps are no longer allocated per dispatch, and the
three immutable snapshots remain.**


## Mipmap cost (phase 6, `run/p6-pixels`, 09:23)

```
chains/frame:   about 2 (274 a second at 137 fps)
levels/chain:   11.0
pixels/frame:   about 464400 (232201 a chain, 63623074 a second)
GPU ms/frame:   NOT MEASURED
% frame:        NOT MEASURED
```

The volume is measured and the time is not, and the reason is concrete rather than vague: a mip reduction is a
blit, not a labelled pass, so the pass table has no row for it and the probe has no counter that separates it
from the frame-end copy-backs. What can be said from measured numbers alone: the reduction moves about 1.8 MiB a
frame (464400 pixels at four bytes), against the frame's whole blit traffic of `blittedMiB` 22159.3 over 600
frames, which is **36.9 MiB a frame**. So the reductions are roughly **5 per cent of the frame's blit traffic**,
and blits are a small part of a frame that is GPU-bound at 7.3 ms on rendered passes.

**Decision (section 6's gate).** Since the volume is about 5 per cent of a small traffic class and no timing
exists to say it costs 0.2-0.3 ms, the plan's own rule applies in the direction of *not* touching it: with no
measurement showing a real cost, changing invalidation would be complexity bought on a guess, and the code
already skips a chain that is still valid. **Mipmap: not worth further complexity on this evidence**, and the
one thing that would reopen it is a GPU timing that separates the reduction from the other blits - which needs a
harness capability or a device-side timer, not another census.

## Next optimisation decision

**A. Pursue the measured shadow cost - with the walk counts now beside it.** It is the only component with a measured share of the frame: `Vitrail
shadow chunk` is the top row of the pass table at 21.3 per cent of stamped pass time, drawn every frame because
this pack voxelises in its shadow stage. Mipmap volume is about 5 per cent of the frame's blit traffic with no
timing against it (B), and every other measured item has already been answered or rejected (C for the rest).



## Shadow decomposition (phase 1, `run/shadow-walk`, 09:26)

What the walk does, read from the engine's own record once a second:

```
Shadow walk: 70 walks (69.2 a second), kept 734 a walk, drew 298 a walk, 22104 loaded,
             terrain=true culling=SAFE_ZONE SWEPT r=128 z=32
shadow-cull SAFE_ZONE SWEPT r=128 z=32 kept=734 camera=487 drawn=287 blocks=2
Shadow map: the opaque world was drawn into it 598 times in the last 600 frames
```

| component | calls/frame | CPU evidence | GPU evidence | removable? |
| --- | ---: | --- | --- | --- |
| terrain/chunk (walk + raster) | **1 walk a frame; 734 sections kept, 298 of them carrying block geometry, of 22104 loaded** | walk runs every frame, `SAFE_ZONE SWEPT r=128 z=32` | shadow map written in **598 of 600 frames**; `Vitrail shadow chunk` is the top pass row at 21.3 per cent of stamped pass time | **no**: the pack voxelises in its shadow stage, so reuse is refused and the map is drawn every frame; the 298 drawn sections are the ones the light reaches that carry geometry |
| entities | NOT COUNTED | NOT MEASURED | NOT MEASURED | unknown |
| translucent | NOT COUNTED | NOT MEASURED | NOT MEASURED | unknown |
| voxel side work | NOT COUNTED | NOT MEASURED | NOT MEASURED | unknown |
| other (block entities) | `blocks=2` in the walk's own line | the count exists, its cost does not | NOT MEASURED | unknown |

**The scene drifted in this arm and the report says so**: `loadedMiB` 312294.5 against the anchor's 93922.0 and
`wallP50` 14.66 against 7.27, so its times are not the reference frame's times. The walk counts are the traversal
facts of that heavier world; a count on the anchor scene needs an arm that lands on it.

**What is established and what is not.** Established: the shadow stage walks the world every frame and rasters
298 geometry-carrying sections into the map every frame, with reuse refused by the pack itself. Not established:
how the 21.3 per cent splits between the walk (CPU), the chunk raster (GPU), the entities, the translucent
casters and the voxel side work - that needs a counter at each of those draw sites, which is the next step, and
no optimisation is written before it.

## Remaining cost, and why work stopped there

The frame is GPU-bound (gpuP50 7.34 against wallP50 7.29) on the pack's own work: the pass-timings ranking puts
`Vitrail shadow chunk` first at 21.3 % of the stamped pass total and `Vitrail chunk` second at 16.2 %, then the
sky, the composite chain and the atlas animation. Four GPU-traffic switches were measured and each answered
"removes bytes and copies, moves no time" - the frame's cost is not in the traffic they remove. What remains
unmeasured is the mipmap chain reduction (2 targets, 7 MiB, one lod-reading program, per frame cost unknown) and
the CPU-side hot paths (§34/§37/§39), none of which has profiling evidence of significance yet.

## Vitrail performance optimisation complete?

**NO**  -  §57.2's mipmap count, §57.9's corpus and §57.10's lifecycle items are open, and the plan's own stop
condition (§58) is not met.
