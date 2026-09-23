# Active Tasks

Updated: 2026-09-23
Scope: `dev` and the Metal-only conversion branch, whose seam needs companion Metallum `feat/shader-module-seam`;
`dev`'s own state is what the rest of this file records, and the branch's remaining acceptance is its own section
below.

This file held five days of closed phases, each with its measurements, and its long form is in `docs/`; it now holds
only what is open. The previous, much longer form is in the history (`git show 94219c13:.context/TASKS.md`), the
migration plan of record is `.context/architecture/roadmap.md`, and `docs/metallum-port.md` - "Acceptance required
before merge" and "Next work" - is the long form of most of what is below. `M:` prefixes a path in the companion.

Nothing below is a queue of features. The port and the performance programme are both closed and merged; what is
left is verification evidence the PHASE 17 policy asks for, findings that were narrowed but not fixed, and two
release decisions that are the owner's.

## P0 — The owner's release decisions

Status: waiting on the owner, not on engineering.

- [ ] `dev` -> `main` for the next released version, by the fast-forward `CONTRIBUTING.md` describes, with the
      release request's own template and both places the version is written checked (`build/open-0.13.0-dev`).
- [ ] The companion's first release: it has no version, so obtaining the backend is still a build-from-source
      matter (`docs/metallum-port.md`, "Current validation status").

## P1 — The Metal-only tree's own real-device acceptance

Status: open, and it is the one place where this branch's evidence is thinner than `dev`'s was. Every gate the
port's phases closed was closed on `dev`, before the other backend's implementation was deleted, so none of it
validates this tree. What has been driven on the converted build is eight fixtures, each through the framebuffer
capture recipe in `.context/STATE.md` (which also says why the client's vignette option must be off for one):
`mrt-contract`, `wide-resources-contract`, `composite-history-contract`, `composite-flip-contract`,
`compute-storage-contract`, `phase16-advanced-contract`, `shadow-mipmap-contract` (Metal 3, whose depth road the
Metal 4 encoder does not carry) and `deferred-mipmap-contract` (Metal 4, colour chains). The task's own smoke list
(`00-basic-color` through `15-compute`) names no artifact this repository has; the fixtures below are its
equivalents and are finer grained.

- [ ] The rest of the fixture corpus, one gate each: `terrain`, `sky`, `clouds`, `weather`,
      `particles-opaque`, `particles-translucent`, `entity`, `block-entity`, `spider-eyes`, `armor-glint`,
      `hand`, `hand-water`, `hand-glint`, `hand-water-glint`, `pre-hand`, `pre-translucent`, the six
      `gbuffer-*` fixtures, `depth-conversion`, `depthtex0/1/2`, `shadow-depth`, `shadow-color`,
      `shadow-terrain`, `shadow-entities`, `deferred`, `deferred-depth`, `deferred-mrt`, `deferred-tail`,
      `final-chain`, `final-direct`, `phase16-pbr`, `dimension-convention`, `dimension-properties`,
      `attachment-traffic`.
- [x] The start-up refusal for an API of another version: driven on the device by bumping the companion's
      API version for one launch, which came up on Metal, logged the clause naming both versions at ERROR and
      drew no pack. The other two remain, and the reason they are not drivable here is recorded in
      `.context/STATE.md`: this harness's client *is* the backend, and no switch makes a Metal device fail.
- [ ] Metallum absent and a failed Metal device creation, each refusing early and saying which one it was,
      neither reaching a world - by whatever means can produce them, since the smoke client cannot.
- [ ] The behaviours no fixture covers: threadgroup-memory fallback, geometry fold-or-refuse,
      pipeline eviction and reload, window resize, resource reload, shader reload, and a dimension change
      mid-session.
- [ ] Metal validation against a serious-error-free session is asserted in `docs/metallum-port.md` but has no
      artifact of its own in this tree; the companion keeps the validation logs the runs produced.

Evidence: `docs/metallum-port.md`, `.context/STATE.md` (the recipe and the seven closed gates),
`tests/fixtures/shaderpacks/`, `tests/Verify*Screenshot.java`.

## P1 — The reviewed PHASE 17 evidence that was never collected

Status: open, and not a blocker - the owner closed the phase without it. This is what a status would need.

- [ ] One exact pack artifact against one exact Vitrail/Metallum head pair, with the reviewed screenshot and
      reference material the policy defines, carried through `tests/phase17_compatibility.py` rather than asserted.
- [ ] The primary visual case: a Bliss v2.1.2 `gbuffers_skybasic` fragment `discard` no longer lets the vanilla scene
      seed repaint the claimed sky. The best evidence so far is a tester's "no obvious problem" in the `3d951772`
      multi-pack run, which is a regression observation.
- [ ] The End sky branch - the other `covers=true` branch - is exercised; the `ac33fed3` run did not.
- [ ] The matrix's five rows carry no status today, and Complementary, BSL-family and Sildur-family have never run
      under Vitrail on Metal.

Evidence: `docs/phase17-compatibility.md`, `tests/phase17_collect_session.py`, `tests/phase17_compatibility.py`,
`tests/fixtures/phase17/catalog.json`.

## P1 — The view-centred LPV voxel-centre mismatch behind Photon's Nether-portal emissive light

Status: open. The specific `c296caec` observation is closed and nothing has re-opened it; this is the later
view-only re-observation of the same light.

- [ ] A stationary Nether-portal emissive light does not shift or flicker under a view-only rotation with
      `Voxel Volume Center = Ahead`, with the chain and shadow map staying active and the run crossing the former
      `Render list is full` point.
- [ ] Ordinary shadow terrain and at least one non-view-centred voxel pack are reconfirmed after the scheduling
      move, because the light walk is now scoped and restored within the frame.

Evidence: `docs/phase17-compatibility.md` (the mechanism), `docs/metallum-port.md` item 2,
`common/src/main/java/dev/vitrail/render/storage/StorageImages.java` (`reanchor`), the `ShadowTerrain` walk.

## P1 — The terrain/render-distance boundary transition Photon shows at distance

Status: open, and it is the finding most likely to decide whether Photon would be `Supported` or `Partially
Supported` if its record were written. It is not a cloud problem: the reversed-`smoothstep` candidate produced no
visual improvement and was reverted in `c3a619e`.

- [ ] The abrupt transition is reproduced with Photon's `BORDER_FOG` at its default, which the pack enables to hide
      exactly this boundary.
- [ ] `BORDER_FOG` is A/B'd off only as a diagnostic, to say whether the pack's intended chunk-boundary fade reaches
      the image; no cloud math is patched from the symptom.

Evidence: `docs/metallum-port.md` ("Next work"), `render/HorizonCone`, `render/SkyDraw`, `SceneSeed`.

## P1 — The Metal 4 audit's remaining phases, and section 25's standing documents

Status: open. Only D1, C1, C2, C3, F1 and J1 of that audit are recorded, in `M:docs/metal4-full-frame-report.md`;
Metal 4 is frozen, so these are a record to finish rather than a performance queue.

- [ ] D1's fixture gaps are closed: it has no dedicated pack yet for clear-to-copy, for a partial view, or for the
      frame-end flush.
- [ ] Phases D2, E, G, H, I, K and L are either measured or explicitly closed as not worth measuring, with the
      reason recorded. D2 is the pass-stitching census: the attachment tuple, load/store, depth, sample count, area,
      any intervening copy or compute, and the candidate-or-reject reason.
- [ ] Section 25's three standing documents are written or the requirement is withdrawn.

Evidence: `M:docs/metal4-full-frame-report.md`, `M:docs/metal4-migration.md`.

## P1 — Complementary r5.9.1's Advanced Colored Lighting, and the sampler ceiling behind it

Status: open, and the diagnosis is done. Why ACL (and possibly WSR) does not run is the pack's own
`!defined MC_OS_MAC` gate: `-Dvitrail.shaderPlatformNonMac=true` compiles it, and then Metal's 16-sampler ceiling
refuses ACL's pipelines. The remedy is argument buffers on that path, and it is not written.

- [ ] The argument-buffer remedy is either implemented or recorded as refused, with the reason.
- [ ] The classification is generic: a sampler-slot ceiling, not an ACL or Complementary special case.
- [ ] `docs/compatibility.md`'s partial note on the sampler limit is corrected to whatever this decides.

Evidence: `pack/option/EngineDefines.java` (the platform define and the switch), `M:tools/ci-argument-buffer-samplers.py`
(the slot-versus-image rule), `tests/test_shader_platform_switch.py`.

## P1 — Two readings the capability fix voided, owed again

Status: open. P1's store half and the boundary-narrowing switch were both measured through
`encoder instanceof X` on the wrapper the game hands out, which could never answer yes: the mechanism never
arrived, so "the store half removes nothing" and "`narrowStorageBoundary` merges nothing" are readings of an
instrument that was not connected. The load half's 32.3 per cent is unaffected.

- [ ] The store half of P1 and `-Dvitrail.narrowStorageBoundary=true` are measured again through
      `Backends.encoder(...)`, or recorded as unreachable with the reason, so no later session inherits a verdict
      taken from the wrapper.

Evidence: `docs/performance.md` (P6's correction), `tests/test_backend_capability_reach.py`, `M:run/b2-attach`.

## P2 — The distance does not join the sky (no Distant Horizons installed)

Status: open, and it needs a fresh reading before anything is changed.

- [ ] The owner says which way the mismatched band moves: with the weather and the sun, which makes it the game's
      fog colour the engine clears with, or staying put with the sky, which makes it the pack's own horizon. Only
      the owner can see that - the code cannot - and the engine-side reading is already done (the pack declares no
      `colortex*Clear*`, so the target is re-cleared every frame with the game's fog colour).
- [ ] The finding is reported as an interaction for the owner to decide on, not as an engine change; a session where
      the pack's own `distant*` programs are exercised is worth having first, since this machine has no Distant
      Horizons install and the earlier no-DH attempts are what the packs skip.

Evidence: `docs/performance.md` (the pack's detached warm-up behaviour), the `distant*` program path.

## P2 — Per-pass timings that are the card's numbers and not the clock

Status: open. `MetalDevice.getTimestampNow()` is `System.nanoTime()`, so `PassTimings`' claim that the numbers are
the card's is false on this backend and every per-pass table is CPU time. No counter-sample-buffer implementation
exists (`sampleCounters` appears nowhere in the companion).

- [ ] Either the pass timings take a real GPU counter sample buffer, or the report is relabelled as host-clock
      timing wherever it is published, so a reader cannot take a per-pass table for GPU time.

Evidence: `M:tools/ci-frame-probe.py` (which pins the substitution), `M:docs/performance.md`.

## P2 — The load page, reviewed on device

Status: open, and it is a picture question rather than a timing one.

- [ ] The loading page is reviewed on device for what it draws and where, including the pixel geometry that decides
      whether the bar and the mark land where they are meant to at the window's own resolution.

Evidence: the loading-page work in `docs/performance.md`; `docs/developing.md` for how a screen is written.

## P2 — The PHASE 17 catalog's own decisions

Status: open, documentation-shaped.

- [ ] Bliss, Solas and Sundial Lite get their catalog decision: a row, or a recorded statement that they are
      deliberately outside the matrix.
- [ ] `ShadowGeometryValues`' narrowing is either completed or recorded as abandoned, since the shadow publish
      defect it was part of is closed.

Evidence: `tests/fixtures/phase17/catalog.json`, `docs/phase17-compatibility.md`.

## Maintained, and not tasks to close

- Comparison-versus-ordinary shadow sampler declarations stay explicit until reference behaviour supplies a defined
  contract to implement; the current diagnostic records that the mixed declaration is undefined under Iris too.
- First-frame `nothing fills them yet` material-map diagnostics are investigated only where they survive into a
  persistent visual or semantic mismatch.
- Backend-neutral regression coverage is kept for every shared contract changed while fixing these findings: the
  removed path is gone, but a contract it exercised is still a contract.
- No pack-specific production special case is added for Bliss, Photon, Complementary, MakeUp or Solas; findings are
  fixed at the owning generic contract.
- A green Gradle build, a successful launch, a full warm-up count or a successful compute dispatch is useful
  evidence, and none of them alone proves rendering correctness or shader-pack compatibility.
