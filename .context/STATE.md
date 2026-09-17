# Project State

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- Current branch heads are Vitrail `54f454bc2e4d45c9c8d72faad4a9cc15ef8bed31` and companion Metallum `54ff6f0e22b153ea206cc726f68bccaee6ce4e70`.
- PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — remains active.
- PHASE 17 still uses the five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Runtime progress, CI, warning counts or a plausible frame do not by themselves promote a pack.

## Latest real-device session

- A 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal log exercises Bliss v2.1.2, Complementary Reimagined r5.9.1, MakeUp Ultra Fast 9.5e, Photon v1.3b and Solas Shader V3.7b in one client session. The module-cache path records Vitrail build `54f454bc`; the mod list records Metallum 0.0.24.
- Every tested pack reaches a first full frame. Background warm-up completes without a Vitrail/Metallum compile error: the initial Bliss load reports 62/62 leftover pipelines and the later pack loads report 155/155.
- The optional-Distant-Horizons warm-up failures previously seen in Photon, MakeUp and Complementary are absent. This real-device run therefore validates the `DumpedProgram` warm-up eligibility gate added by `54f454bc` for the observed no-DH configuration.
- Bliss no longer reports the `diagonal3` macro-redefinition compile failure. This real-device run therefore validates the generic active-path macro redefinition handling added by `54f454bc` for the observed Bliss path.
- Photon now compiles past the earlier `world0/prepare/vertex` slash failure, reaches the live chain, records a first full frame, compiles `world0/deferred4_a`, serves its fixed single-workgroup 36864-byte shared allocation through the existing transient-storage fallback, and dispatches it on Metal as groups `(1, 1, 1)` / local `(256, 1, 1)`.
- Solas still compiles and dispatches `shadowcomp` through the active backend as groups `(24, 12, 24)` / local `(8, 8, 8)`.
- The only log-level `ERROR` in this session is a missing/invalid PNG header for `minecraft:block/lime_concrete_powder_s` while reading the enabled Prime's HD resource pack. It is not a Vitrail/Metallum shader-backend compile failure.
- The client shuts down cleanly after the pack sequence.

This runtime log clears the previously recorded Photon compile blocker and proves real compute dispatch for the observed Photon path. It is not a PHASE 17 compatibility promotion by itself because no reviewed screenshot/reference bundle is attached to this evidence.

## Vertex-ABI finding from the remaining warnings

- Repeated entity-shadow warnings say the Vitrail entity mesh does not carry `at_midBlock`. Do **not** fix this by adding another entity vertex element or changing entity stride.
- Iris 26.1 defines `at_midBlock` on `IrisVertexFormats.TERRAIN`, while `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but not `at_midBlock`. Iris shader keys use `ENTITY` for ordinary entities and shadow entities.
- Therefore an entity program asking for `at_midBlock` is not evidence that Vitrail's entity ABI is missing a reference attribute. The current warning wording overstates the situation; the next fix should classify the reference-parity default/constant behavior correctly rather than inventing a Vitrail-only ABI.
- Sky `mc_midTexCoord` / `mc_Entity`, particle/weather extended attributes, comparison-vs-ordinary shadow sampler declarations, and first-frame `nothing fills them yet` diagnostics remain separate review items. They must be compared against the exact Iris format/translation behavior before changing runtime contracts or suppressing diagnostics.

## Open validation boundaries

- Correct the entity `at_midBlock` diagnostic/reference-default classification without changing `IrisVertexFormats.ENTITY` parity or Vitrail entity stride.
- Audit sky, particle and weather attribute warnings against Iris 26.1 `ShaderKey`, vertex formats and translation/default behavior before deciding whether each is a real missing contract or reference-parity input.
- Keep comparison/ordinary shadow sampler conflicts explicit. The current diagnostic already records that the mixed declaration is undefined under Iris too; do not hide it with a backend alias unless new evidence identifies a real contract to implement.
- Review first-frame one-pixel resource diagnostics pack by pack only where they survive into a visual or semantic mismatch; a first-frame warning alone is not a compatibility verdict.
- For PHASE 17 status changes, collect the required reviewed screenshot/reference evidence rather than promoting any of the five packs from this runtime log alone.
- Continue fixing only generic owning-layer contracts. Do not add Bliss, Photon, Complementary, MakeUp or Solas production special cases.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `docs/phase17-compatibility.md`, `CONTRIBUTING.md`
- `common/src/main/java/dev/vitrail/render/GeometryProgram.java`
- `common/src/main/java/dev/vitrail/render/DumpedProgram.java`
- `common/src/main/java/dev/vitrail/render/DistantProgram.java`
- `common/src/main/java/dev/vitrail/render/FamilyWarmup.java`
- `common/src/main/java/dev/vitrail/pack/source/IncludeExpander.java`
- `tests/test_background_pipeline_warmup.py`
- `tests/test_preprocessor_macro_redefinition.py`
- companion Iris 26.1 `IrisVertexFormats.java` and `ShaderKey.java` for vertex-ABI reference
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
