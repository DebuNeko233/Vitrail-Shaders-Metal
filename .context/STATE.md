# Project State

Updated: 2026-09-23
Scope: `dev` plus the Metal-only conversion branch; the seam it needs is on companion Metallum
`feat/shader-module-seam`. The previous scope line named `perf/optimisation` and Metallum
`master` at `5debcb9`.

Short by design. The long form of almost everything here is in `docs/` - `docs/README.md` routes it - and this file
names the document rather than restating it. The exception is a section of findings whose long form is nowhere else,
which is what a memory file is for. The previous, much longer form of this file is in the history
(`git show 2aa2ea9e:.context/STATE.md`); `M:` prefixes a path or a run directory in the companion repository.
**Two copies of the companion exist on this machine** and only one of them is the one below: the checkout beside this
repository, `/Users/neko/ProjectFile/github_proj/metallum`, is on the branch the seam is on and holds every run and log
this file names; `/Users/neko/ProjectFile/metallum` is an older clone at `master` whose `run/` has the same shape and
whose `run/logs` stops in September. A reading taken from the wrong one answers about a tree the runs never used.

## Current focus

One thing is in flight on this branch and nothing on `dev`: the Metal-only conversion's own real-device
acceptance (`.context/TASKS.md`, "The Metal-only tree's own real-device acceptance"). The conversion itself is
done and green - the tree is Metal-only, its source, documentation and metadata carry no trace of the deleted
backend, and `./gradlew build` and the whole CI contract list pass - so what remains is evidence, not code: the
seven gates the recipe below has closed, the rest of the fixture corpus, the three startup refusals, and the
behaviours no fixture covers. The long-term performance programme is measured, decided and merged on both
repositories, and so is the Metal port that preceded this branch. Two release decisions are the owner's:
`dev` -> `main` for the next tag, and the companion's first release.

## Confirmed now

**The removal is verified on a real device, not only by contract.** Three Apple-Silicon runs on
2026-09-23 with `M:tools/run-vitrail-smoke.sh` against a build of that branch:
the game came up on Metal 4 (`Apple M5 Pro`, `macOS 27.0`), `Vitrail.initClient` installed the
shader-module seam, and the seam's hook fired hundreds of times a second across the compile workers.
With **Complementary Reimagined r5.9.1** loaded it dropped **867 unreached samplers across 41 pack
modules**, zeroed locals in 4 of them, stripped 9625 debug names, served **188 modules from the disk
cache**, and compiled **62 of 62** warm-up pipelines with none refused, over a 600-frame window at
`wallP50=16.72 ms`. With **compute-storage-contract** loaded into `PerfWorld` it compiled and
dispatched both compute programs through the backend (`Dispatched compute composite/a through the
active backend: groups=(1,1,1), local=(1,1,1)`), bound `Phase15Buffer` as a storage buffer and
`phase15Tex` as a storage image, and opened 9 render passes in the pack's first full frame. No
Vitrail error appeared in any run.

Three things that run proved and no static check could: the Metallum-side mixin's three
`require = 1` injection points all apply; the hook works on the compile-worker threads, not only the
render thread; and the seam's contract is *called*, not merely installed.

**Family gates can be driven from a framebuffer capture, which needs no F2.** Metallum photographs
its own render target when a request file is dropped in `run/metallum`, so an unattended session can
run a screenshot gate by machine. The recipe, which is what a later session needs and what nothing
else writes down: build `:fabric:jar`; copy the fixture into `M:run/shaderpacks/`; write
`M:run/vitrail/pack.txt` as `pack=<fixture>`/`enabled=true`; set `vignette:false` in `M:run/options.txt`
for the capture and put it back afterwards, because of the overlay below; launch
`./gradlew runClient -PvitrailSmokeJar=<jar> -PvitrailPerfVmArgs="-Dmetallum.clientScreenshot=true"
--args="--quickPlaySingleplayer <world>"`; wait for `first full frame opened`; `touch
M:run/metallum/screenshot-request`; read `M:run/metallum/client-screenshot.png`; verify that picture
with `java tests/Verify<Name>Screenshot.java <png>`. The profile's own window size wins over `--width`
and `--height`, which is why this round's captures are 3416x1920 where the earlier ones were 1708x960.
Eight are now closed on real hardware, all of them in `新的世界`:

- **MRT** - `MRT screenshot quadrant swatches: [BLUE, WHITE, RED, GREEN]`, `MRT screenshot pixel
  check: PASS`. Attachment location, format, clear and store, in pixels.
- **Wide resources** - `GREEN=1327968 MAGENTA=0 OTHER=0`, a perfectly uniform frame: thirty-three
  named sampled images all resolved beyond Metal's sixteen direct slots, and Metallum's own line
  reports `Wide resource pipeline ... uses Metal Argument Buffers: resources=34, sampledImages=33`.
  This is the active-resource narrowing the seam now performs, and its failure signature is absent.

- **Composite history** - `PHASE 11 composite history screenshot check: PASS`, a uniform cyan frame
  with `OTHER=0`. The pack's composites read the previous frame's target, so this is the cross-frame
  ordering the deleted barrier helpers used to carry - verified in pixels after their removal, which
  is the gap the compute rewrite left open and could not close from `common/`.
- **Composite flip** - `PHASE 11 composite flip screenshot check: PASS`, 91.2 per cent blue and no
  failure colour: the within-frame target flip holds on the same terms.

- **Per-attachment blending** - the phase16 fixture's BLEND quarter,
  `PHASE 16 BLEND: GREEN=275679 MAGENTA=0 OTHER=56961 -> PASS`, with the black it paints where no
  solid terrain covered the pixel. `blend.gbuffers_terrain_solid.colortex3=ONE ZERO` and
  `.colortex1=ZERO ZERO` resolve to attachment ranks 0 and 1 and land that way.

- **PHASE 16 advanced features, closed.** One frame passes all four quarters with the overlay below
  turned off: `VOLUME GREEN=332640 MAGENTA=0 OTHER=0`, `NOISE ... OTHER=0`, `BLEND
  GREEN=213565 MAGENTA=0 OTHER=119075` - the black it paints where no solid terrain covered the pixel -
  and `COMPARE GREEN=332640 MAGENTA=0 OTHER=0`. The 3D-volume read and the hardware comparison sampler
  are closed with it, on a session with `vitrail/soft-shadow-compare` unarmed.

- **Shadow depth mipmaps, closed on the generation that carries the road.** `shadow-mipmap-contract`
  reads `GREEN=145081 BLUE=1183618 MAGENTA=133` and PASSES in a session executing Metal 3, whose probe
  line shows the progressive reduction's own passes (1024x1024 then 512x512, 256x256 and 128x128 at 45
  passes a second) and whose log carries the census's `Mip chains:` line every second. In a session
  executing Metal 4 the same fixture reads a uniform `GREEN=0 BLUE=1328832 MAGENTA=0` - the
  matching-depth colour, level nought everywhere - and the census line never appears, which is the
  signature the fixture's own README calls "mip generation failed or both samplers remain clamped".
  That is a fact about the frozen Metal 4 line and not about this engine's seam: the depth road is the
  encoder's, and the encoder that implements it is the Metal 3 one.

- **Deferred colour-chain mipmaps, closed on Metal 4.** `deferred-mipmap-contract` PASSES on a session
  whose probe line reads `executingGeneration=metal4`: `CYAN=1328832 MAGENTA=0 OTHER=0`, a uniform cyan
  frame, with the census reporting `Mip chains: 770 reduced over 1000 ms ... by target Vitrail
  colortex0=385, Vitrail colortex0 alt=385`. So mipmap generation is closed on both halves and the
  generation is the variable rather than the feature: a **colour** chain is filled on Metal 4 and Metal
  3 alike, a **depth** chain on Metal 3 only.

**A start-up refusal was driven on the device, and it refuses.** With the companion's integration API
version bumped for one launch and put back after (the tree was clean before and is clean after,
checked), the client came up on Metal and Vitrail logged at ERROR: "This game is running the Metal
backend, and Vitrail's programs are translated for Metal alone. A pack it is asked for is neither read
nor drawn: the game keeps its own image. Metallum answers API v2 and this build of Vitrail understands
v1, so the two cannot talk to each other". No pack drew and the game kept its own image. The other two
refusals cannot be produced through this harness, which is a fact about the harness rather than a gap
in the code: its client *is* Metallum, so an absent Metallum cannot arise there, and no switch makes a
Metal device fail to come up. Both are covered where they can be - the metadata's required dependency,
and `tests/test_metallum_status.py`, which runs the real `MetallumStatus` in a JVM against synthetic API
shapes for missing, wrong-version, malformed and preference-off.

**The capture has one known trap and one dimming that is now named, and the difference matters.** It
photographs the GUI, so a screen on top of the world fails a coverage rule, and `pauseOnLostFocus`
does not reliably keep that screen away.

**The dimming is the client's own moody-brightness vignette, and it is neither the pack's nor this
engine's.** `Hud.extractCameraOverlays` blits `textures/misc/vignette.png` over the finished frame
whenever `Options.vignette()` is on, at a strength `updateVignetteBrightness` eases toward
`1 - Lightmap.getBrightness(dimensionType, level.getMaxLocalRawBrightness(eyePos))` - a multiply that
is strongest when the light level at the camera entity's eye is lowest. Measured against the client's
own texture on a flat-colour pack captured in the failing fixture's own world, the strength implied at
38,758 samples is **0.7522 with a standard deviation of 0.0167**, flat across the texture's darkness
bands from 0.0 to 0.8 (the residual is PNG quantisation), and 100 per cent of the frame's pixels are
pure green hue. So the picture was the pack's image times that texture and nothing else. This is the
attribution two earlier rounds got wrong in both directions: the wide-resource fixture's first
`OTHER=157601` and the phase16 outer quarters' 73 per cent green were one overlay, seen at different
strengths because each session's spawn put the camera entity's eye at a different light level - which
is also why the wide-resource run passed after "the world changed". It is not a screen, not the pack
chain, not the capture road and not the Apple Metal HUD. The recipe above turns the option off for a
capture; a gate that fails on this signature with the option on has not tested the pack.

**A refused mip chain is said out loud now, because the picture cannot say it.** Nothing filled the
shadow depth chain in a Metal 4 session, and until this round nothing said so: the allocation line
still read "the pack asks for a chain the light fills every frame, 10 levels", the census's `Mip
chains:` line never appeared, and the only symptom was a diagnostic that reads the same as a pack that
asked for no chain. `MipmapCensus.refused` now reports it once per image, on the branch that asked the
backend, so a session that loses a chain says which image and how many levels. What is still not right
there: `GpuFormats.blitsBothWays` is a constant `true`, so the levels are allocated whatever the
generation can fill, where `ShadowTargets.levels`'s own javadoc says the question is asked before the
memory is taken. The safety net is what makes that harmless rather than wrong - a sampler only gets mip
access when the fill reported success - so this is an allocation and a log line to correct, not a
picture.

What no run reached: a pixel check of threadgroup-memory fallback, geometry fold-or-refuse, pipeline
eviction, resize, resource reload, shader reload or dimension change. The compute run's
logs are kept at `M:run/logs/vitrail-metal-validation-{compute,mipmap,history,flip,phase16}.log`; the other runs rotated.

**The font-sheet intensity mapping is a known, reported gap.** `GlyphIntensity` asks the backend for
a view that reads one channel four times and names it once when nothing answers. On this platform the
mapping is fixed when a resource is described rather than when a view is made, so the road that used
to write it never applied here - which means a pack's intensity text has been drawing red. Fixing it
needs a backend that can describe such a resource; no run has shown it working.

**Metal is the only path, and the tree is Metal-only.** `AGENTS.md` states that as a current
architecture fact rather than a migration: macOS on Apple Silicon, Metallum and Apple Metal is the
single supported target, Metallum is a required runtime dependency and the only provider, and the
deleted graphics API is gone from code and documentation. Its name, at any case and inside any
identifier, is refused by a contract test, so a reintroduction fails the build rather than being
caught in review. Metal 4 is frozen as an experimental backend: kept, compilable, manually
selectable, and not a performance target.

**The port's status is `docs/metallum-port.md`, and it is the authority.** PHASE 2, PHASE 5-15 and PHASE 16 are
closed with real-device evidence; PHASE 17 is closed by the owner's judgement with no per-row status, and no run
promotes any pack. `v0.12.0-metal-beta` was released from `main` at `55d6d6a8`; `dev` has opened `0.13.0-dev`, which
nothing will tag. The companion has no released version, so obtaining it is still a build-from-source matter.

**The performance programme's result** is on one page in the companion: `M:docs/long-term-performance-summary.md`.
Tracks A-G (Metal 3 native census, Vitrail CPU, GPU structure, the seam, MetalFX, startup, measurement), the
acceptance on the final head, and the audit of its eleven success criteria: ten met, and the one that is not (a batch
of low-risk CPU optimisations landing) is not met by the plan's own exit condition, every candidate having measured
under one per cent. The Vitrail half of the evidence is `docs/performance-report.md` and `docs/performance.md`.

**The owner's three decisions**, recorded rather than left open: the shadow map's default stays at one kept frame;
dynamic resolution (E4) is declined and will not be built; no fifth corpus pack (Solas) will be supplied.

**Measurement discipline** (`M:docs/performance-testing.md`): the deterministic fixture is the frozen world with
25 seconds of settle and a 600-frame window at Photon v1.3b, render scale 55, camera pinned; the counters' floor is
about a quarter of a per cent and the frame-time floor 1.4 per cent on no-pack; a comparison repeats, and a
configuration whose own repeats disagree by more than the effect is reported unresolved. Every arm now writes
`source-revision.txt` - the checkout it ran and whether the worktree was that checkout, for both repositories and for
the tools that measure them - because a baseline from another machine state is not a baseline.

**The versions are `gradle.properties`'s to state** (Minecraft 26.2, Sodium 0.9.2+mc26.2, Java 25, `0.13.0-dev`), and
the Skill/Metal API must be checked against that exact source before use, as `AGENTS.md` requires.

**Build and CI hygiene.** Neither repository's CI may author a commit, and both refuse the shape statically
(`M:tools/ci-contracts.py`, `tests/test_workflow_contract.py` run by `build.yml`); `contents: write` is reserved for
`release.yml`. Vitrail's contracts are 67 test scripts (`build.yml`), the companion's 15 (`M:tools/ci-*`).

## Findings whose long form is nowhere else

Everything below is measured, and the document that would normally own it does not carry it. Compressed, not
abbreviated away: the numbers are the finding.

- **The packed scene's frame period is mostly the client's own work**, and the second population in it is the client
  tick. An earlier attribution of that population to a pacing resource is **withdrawn** (`M:run/c-makeup-trace/t3`).
- **The Metal 4 upload road is priced and does not explain the frame's slow population**: 0.00021 MiB and 0.2293 MiB
  in the two arms, so moving the window's whole upload CPU into its slow tenth buys 0.81 ms against 4-5 ms. The
  direct-write replacement is **REJECTED as a performance change**.
- **The render-scale line latches**: Metal 3 at 100 per cent and Metal 4 at 100 per cent each write
  "The render scale is 100%, so the world is drawn at the window's own size and MetalFX is off" exactly once and
  neither writes a scaled-size line, so a 55 per cent session can open with the 100 per cent line while the value is
  `WHOLE` and the pack's own arrives later. **The 55 -> 100 direction is NOT MEASURED live**: once a pack's scale is
  in force the file is re-read only by the reload key or a world move, and the owner chose not to drive the key
  (`M:run/scale-live-evidence`).
- **The frame's own CPU, per scene**: 136-144 ms over a 600-frame ~1005 ms window with 37-43 MiB allocated on
  no-pack, and 703 ms with 107 MiB on MakeUp (the 13-17 per cent of wall and 71-183 KiB a frame are in
  `M:docs/vitrail-cpu-performance.md`; these absolute figures are not).
- **The per-pass timing report is the host clock.** `MetalDevice.getTimestampNow()` is `System.nanoTime()`, so
  `PassTimings`' claim that "the numbers are the card's, not the clock's" is false on this backend, and
  `M:tools/ci-frame-probe.py` pins the substitution. Read a per-pass table as CPU time.
- **566 fps is the ceiling the frame-path migration is judged against** - the harness's no-pack, fullscreen,
  uncapped baseline. The harness itself was found duplicating a `--fullscreen` case whose first copy took an
  argument, swallowing the following `--world`.
- **The harness had been staging the nether**: 81 records were the overworld and 9 the nether, and the same scene
  goes from no cloud pass to 683 in a 120-frame Metal 4 window. `freeze-world.py --dimension` is the correction.
- **`freeze-world.py` was freezing nothing.** `level.dat` is no longer read; the clock, weather and game rules live in
  `world_clocks.dat`, `weather.dat` and `game_rules.dat` with namespaced names (`minecraft:advance_time`,
  `minecraft:spawn_mobs`). With both spellings absent the clock read 14630 after a nominal freeze, and the corrected
  fixture reads 4000.
- **`--spectator` is not cosmetic**: the body the camera is in is the last thing inside a frame that varies. The
  acceptance floor sentence ("a quarter of a per cent on the counters, 1.4 per cent on the frame time") belongs with
  it.
- **Pinning the camera does not narrow the packed scene's spread**; the arming moment is the next suspect. The
  packed scene's own floor is 3.4 per cent, measured over eight readings (6.89 to 7.30), and pixels are still not
  identical between a run pair - 79 per cent of the pair differed at all.
- **The seam's per-scene composition**: MakeUp 11 calls a frame at 0.70 per cent of the wall, Complementary 42 at
  0.16, Photon 12 at 0.066, no-pack none; a call costs 1.4-6.2 us. Two reflective lookups a session across seven
  bridges, **six on Photon**.
- **The command encoder the game hands out is a wrapper**, and the capabilities are mixed into
  `CommandEncoderBackend` behind it. `encoder instanceof X` could therefore never answer yes, which means P1's store
  half and the storage boundary were never delivered and their earlier nulls are void (pinned by
  `tests/test_backend_capability_reach.py`). The conclusion that survives: P1's load half (32.3 per cent) is
  untouched, and both re-opened readings need re-measuring.
- **`-Dvitrail.narrowStorageBoundary=true` is a no-op on Photon** - its passes all answer "may read" - recorded so a
  later session does not read that arm's null as a delivered boundary.
- **The injected encoder Mixin's removal had one symptom**: darker Photon shadows, because the shadow depth chain had
  lost its mipmap path. It also skipped silently on a sealed package with one `Error loading class` warning, which
  is why the adapter replaced it.
- **Two more silent faults in the same seam**: reflection cannot enter a package-private class, and a selector an
  object does not answer to is an exception that ends the process - `setInputContentOriginX:` killed a session.
- **The F3 screen had been naming the device's newest family as if it were the API in use**, in three places; it now
  answers what executes (`079b7bf7` with companion `802a54f`), pinned by `tests/test_debug_entry_lines.py`.
- **The Metal execution preference's precedence** (`MetalExecutionChoice`, `vitrail/metal-execution.txt`, one word,
  default `metal3`): a JVM `-D` outranks the file, the file is read before the device exists, and the six-launch
  matrix that establishes it is in the history. A stored `metal4` is a *preference* that says so when unsatisfied;
  `-Dmetallum.execution=metal4` remains the strict demand that fails a launch rather than falling back.
- **The Metal 4 migration's per-step frames** (M0-M3): the architecture guards and `selectedGeneration=`; capability
  selection with `-Dmetallum.execution=auto|metal3|metal4`; the shader-language profile following the executing
  generation (`msl3.2`/`msl3.1`/`msl3.0`, `msl4.0` unreachable until Metal 4 executes a frame); the
  `com.metallum.render.shared` / `render.metal3` / `mtl.metal3` / `mtl.metal4` package moves with their costs
  (118 members for the frame-path move against 2 for `MetalFence`, eight package-private members for the command
  wrappers). `docs/metal4-migration.md` carries the mechanisms; these frames and costs are not there.
  **M3's render-side facade split is NOT done**: `Metal3CommandEncoder` and `Metal3RenderPass` do not exist.
- **The two binding shapes the frame's own passes need are proven on Metal 4** with readbacks
  `(0.25, 0.5, 0.75, 1)` for the uniform and `(0.25, 0.5, 0.5, 1)` for the stride-16 vertex case.
- **The present is drawn, not copied**, by a triangle that flips V: uvs `(0,1) (2,1) (0,-1)` under
  `(-1,1) (3,1) (-1,-3)`, which is why `copyFromTexture:toTexture:` and its V-flip bug are gone.
- **The Metal 4 overworld was lighter than Metal 3's** because its sky gradient's upper end is mixed about a third of
  the way to white - `(154.5,180.9,242.1)` to `(189.1,206.7,243.9)`, row bands 0.353 at the top tenth against 0.001 at
  the horizon. The root cause is **NOT MEASURED** and would need a pinned-profile diagnostic.
- **One earlier no-pack run reported a 5.60 ms worst frame with the GPU's worst at 2.41** while the next reported
  2.80, which is why a single outlier is an outlier until it repeats.
- **The shadow decomposition's absolute figures**: on `M:run/shadow-decomp2`, 0.04 ms of a 7.30 ms frame for the
  translucent shadow pass, which carries 625 encoders and 20.6 per cent of the frame's loaded attachment bytes. The
  earlier claim that `Vitrail shadow chunk` was the frame's top cost at 21.3 per cent of stamped pass time was
  reading **CPU encode time** and is withdrawn.
- **The original PR #1 was merged with a merge commit by mistake** and repaired by resetting `dev` and `main` to
  `b4da068d`; `git diff 85b21157 b4da068d` is empty, and `85b21157` is deliberately not in `dev`'s history.

## Important incomplete areas

- The two PHASE 17 findings that were open when the phase closed and are still open: the view-centred LPV
  voxel-centre mismatch behind Photon's Nether-portal emissive light, and the abrupt terrain/render-distance
  boundary at the shared 256-block horizon. `docs/phase17-compatibility.md` carries both, the mechanism each was
  narrowed to, and the audit material behind them.
- No PHASE 17 reviewed screenshot/reference evidence was ever collected, so no pack carries a compatibility status
  and four of the five matrix rows have never run on device.
- The companion has no release.
- The Metal 4 line stays verifiable while frozen: all four of its architecture contracts pass on the current head.

## Repository evidence to verify first

- `README.md`, `docs/README.md` (the documentation router), `INSTALL.md`
- `docs/metallum-port.md` (the port's implementation and validation status), `docs/performance.md` (the plan, with
  phases P1-P7), `docs/performance-report.md` (the programme's arm-by-arm record), `docs/phase17-compatibility.md`
- `CONTRIBUTING.md` and `.github/workflows/` (branch, commit, changelog and build gates)
- `gradle.properties` (the active Minecraft, Sodium, Java and toolchain versions)
- the companion `DebuNeko233/metallum`: `docs/long-term-performance-summary.md`, `docs/performance-testing.md` (the
  harness and the rules), `docs/metal4-migration.md`, `docs/metal4-full-frame-report.md`, and `tools/` - every
  harness script, `freeze-world.py` and every `run/...` session named above live there, not here
- `.context/architecture/metallum-port.md` (the boundary model and the vertex-ABI discipline) and
  `.context/architecture/roadmap.md` (the migration plan of record, kept verbatim - it is the source of the phase
  numbering everything else uses)

## Recovery entry points

- `./gradlew build` is this repository's check; `python3 tests/<script>.py` runs one contract, several of which take
  `--self-test`. `build.yml` is the list that CI runs.
- `.context/TASKS.md` for what is open, and `.context/architecture/metallum-port.md` for the model.
- The sky-ownership work: `render/SkyProgram`, `SkyOwnership`, `SkyDraw`, `HorizonCone`, `mixin/SkyRendererMixin`,
  `render/GeometryProgram`; its tests `tests/test_sky_claim_coverage.py`, `tests/test_entity_reference_default.py`.
- The vertex-input classification: `render/VertexInputDiagnostics`, `render/EntityInputDiagnostics`,
  `glsl/EntityVertex`, `glsl/LinesVertex`, `glsl/SkyVertex`, and `render/{Entity,Particle,Weather,Distant,Dumped}Program`.
- The pack-preparation and warm-up path: `pack/source/IncludeExpander`, `render/FamilyWarmup`,
  `tests/test_background_pipeline_warmup.py`, `tests/test_preprocessor_macro_redefinition.py`.
- The seam: `compat/metallum/` (the bridges, the capability resolver, `BridgeCensus`) and the two remaining Mixin
  targets, `MetalBackendMixin` and `MetalDeviceMixin`.
- The companion Iris checkout (branch `26.2`, cloned beside the Vitrail checkout) for reference semantics:
  `IrisVertexFormats.java`, `ShaderKey.java`, `ShaderAttributeInputs.java`, `VanillaCoreTransformer.java`,
  `CommonTransformer.java`.
