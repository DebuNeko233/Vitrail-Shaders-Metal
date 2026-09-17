# Active Tasks

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## P0 - Complete PHASE 17 real shader-pack compatibility

The synthetic/runtime capability phases are closed; current work is evidence-backed real-pack compatibility on Apple Silicon.

- [x] Define the exact five public statuses: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`.
- [x] Refuse incomplete/unreviewed evidence instead of inferring compatibility from CI, warnings or a plausible image.
- [x] Collect Metal/device/exact heads, pack/log/screenshot SHA-256, real Vitrail draws, raw fallback observations, fatal observations and clean shutdown from one launcher-managed hardware session.
- [x] Keep the shader-pack artifact out of the review bundle.
- [x] Require exactly one fresh F2 screenshot per future launcher run.
- [x] Hash the staged artifact Minecraft actually tested for future launcher runs.
- [x] Add a Vitrail-owned bundle verifier that rejects malformed/extra files and verifies log/screenshot hashes without extracting the archive.
- [x] Fix the generic optional-Distant-Horizons warm-up bug by making warm-up eligibility backend-neutral and refusing unavailable distant families before detached precompile.
- [x] Fix generic active-path pack macro redefinition so a later pack `#define` replaces an earlier active pack definition without blanket-undefining engine/compiler environment macros.
- [x] Real-device verify those two fixes with Vitrail `54f454bc`: Photon, MakeUp and Complementary no longer fail warm-up on unavailable `distant*` programs, and Bliss no longer fails on `diagonal3` redefinition.
- [x] Re-run Photon and verify it compiles past the former `world0/prepare/vertex` blocker, reaches a first full frame and clean shutdown.
- [x] Verify real Photon compute execution after the compile blocker: `world0/deferred4_a` compiles and dispatches on Metal as groups `(1, 1, 1)` / local `(256, 1, 1)` while its fixed oversized shared allocation uses the existing transient-storage fallback.
- [x] Reconfirm a second real compute path with Solas `shadowcomp`, dispatched as groups `(24, 12, 24)` / local `(8, 8, 8)`.
- [ ] Do not promote Photon or any other pack from the 2026-09-18 runtime logs alone. Collect/review the screenshot/reference evidence required by `docs/phase17-compatibility.md` before changing a public compatibility status.
- [ ] Continue the matrix through the remaining required pack families, including BSL-family and Sildur-family coverage, with the same evidence discipline.

The 2026-09-18 runtime sessions exercise Bliss v2.1.2, Complementary Reimagined r5.9.1, MakeUp Ultra Fast 9.5e, Photon v1.3b and Solas Shader V3.7b without a Vitrail/Metallum log-level compile error. The follow-up run on Vitrail build `2f1ff54d` also real-device verifies that the corrected entity `at_midBlock` diagnostic is absent while shadow entities still draw. That proves execution progress and the diagnostic fix, not visual/reference correctness.

## P1 - Classify remaining vertex and sampler diagnostics against Iris 26.1

- [x] Correct the entity-shadow `at_midBlock` diagnostic without extending the entity vertex ABI.
  - Iris 26.1 `IrisVertexFormats.ENTITY` carries `iris_Entity`, `mc_midTexCoord` and `at_tangent`, but not `at_midBlock`.
  - Iris 26.1 shader keys use `ENTITY` for ordinary entities and shadow entities.
  - `VertexInputDiagnostics` now keeps real mesh-backed answers separate from reference-unbacked inputs; the entity bridge contributes `at_midBlock` only to the missing-input diagnostic filter for regular entity rows.
  - The `2f1ff54d` hardware run contains no `at_midBlock` diagnostic while Photon records `shadow_item`, `shadow_cutout`, `shadow_solid`, `shadow_translucent` and `shadow_eyes` draws.
  - Glint, text and line rows remain on their narrower diagnostic answers until their own reference paths are audited.
- [x] Classify particle/weather `mc_Entity`, `mc_midTexCoord` and `at_tangent` as reference-unbacked inputs rather than missing real mesh fields.
  - Iris 26.1 `ShaderKey` uses `DefaultVertexFormat.PARTICLE` for both particle keys and weather; that format has no backing element for those three pack extension locations.
  - Iris's core transformer leaves `mc_Entity` untouched when the format reports zero entity components and does not synthesize `mc_midTexCoord` or `at_tangent` for this format.
  - Vitrail keeps the real particle/weather answer set empty and uses its existing deterministic synthesized constants. This is a diagnostic classification only: it does **not** claim value-for-value parity with OpenGL generic-attribute state, which can depend on GL state and may be undefined after array-backed draws.
  - Acceptance: no particle/weather vertex-format or stride change, and these names no longer count as a Vitrail-only missing real attribute.
- [ ] Audit sky warnings (`mc_midTexCoord` in Photon; `mc_Entity` in Solas) against the exact Iris sky vertex formats and translator behavior before changing mesh ABI or suppressing diagnostics.
- [ ] Audit line-row warnings (`vaUV2` in Photon; `mc_Entity` in Solas) against Iris's line format and transform path before changing their diagnostic classification.
- [ ] Keep comparison-vs-ordinary shadow sampler warnings explicit unless source/reference evidence identifies a defined behavior Vitrail is missing. The current mixed declaration is already diagnosed as undefined under Iris too.
- [ ] Treat `nothing fills them yet` first-frame resource warnings as evidence to investigate only when they correspond to a persistent semantic/visual mismatch; do not convert warning count into a compatibility score.

## P2 - Keep acceptance and documentation synchronized

- [ ] Keep both PRs Draft/open/unmerged while PHASE 17 real-pack acceptance is incomplete.
- [x] Keep `.context/STATE.md` and `.context/TASKS.md` synchronized with the 2026-09-18 hardware evidence and the Iris 26.1 vertex-input findings.
- [ ] Keep Vitrail/Metallum ownership boundaries strict in every follow-up: pack semantics/defaults/diagnostics in Vitrail; generic Metal execution in Metallum.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one runtime session, CI alone, or the absence of log-level errors.
