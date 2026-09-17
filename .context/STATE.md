# Project State

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- The Vitrail code line contains the `76b7c69c` entity-reference-default fix plus the particle/weather diagnostic classification recorded below; companion Metallum remains `54ff6f0e22b153ea206cc726f68bccaee6ce4e70`.
- PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — remains active.
- PHASE 17 still uses the five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Runtime progress, CI, warning counts or a plausible frame do not by themselves promote a pack.

## Latest real-device session

- A follow-up 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal log runs Vitrail build `2f1ff54d` with Metallum 0.0.24, Sodium 0.9.2 and Minecraft 26.2 across the same Bliss, Complementary Reimagined, MakeUp Ultra Fast, Photon and Solas pack set.
- The log contains no Vitrail/Metallum log-level `ERROR` or `FATAL`. Photon still completes 62/62 initial leftover warm-up pipelines and 155/155 after the world reload; the later pack loads also reach their first full frames.
- Photon still compiles and dispatches `world0/deferred4_a` on Metal as groups `(1, 1, 1)` / local `(256, 1, 1)`, including the existing 36864-byte transient-storage fallback for fixed threadgroup memory. Solas still compiles and dispatches `shadowcomp` on the active backend.
- The corrected entity diagnostic is real-device verified: the whole follow-up log contains no `at_midBlock` diagnostic, while Photon submits 19 entities into the shadow map and records `shadow_item`, `shadow_cutout`, `shadow_solid`, `shadow_translucent` and `shadow_eyes` draws through `world0/shadow_entities`.
- This confirms that removing the false missing-entity-attribute diagnostic did not remove the shadow-entity path. It remains diagnostic evidence, not visual/reference acceptance.

## Vertex-input findings

- Iris 26.1 defines `at_midBlock` on `IrisVertexFormats.TERRAIN`, while `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but not `at_midBlock`. Iris shader keys use `ENTITY` for ordinary entities and shadow entities.
- Vitrail keeps `EntityVertex.ANSWERED`, the three appended entity elements and entity stride unchanged. `VertexInputDiagnostics` now distinguishes real mesh-backed answers from inputs that the Iris reference format also leaves without a backing vertex array; the existing `EntityInputDiagnostics` call site delegates to that classifier.
- Iris 26.1 uses `DefaultVertexFormat.PARTICLE` for `PARTICLES`, `PARTICLES_TRANS` and `WEATHER`. That format carries none of `mc_Entity`, `mc_midTexCoord` or `at_tangent`; Iris's core transform path does not turn those three into real particle/weather vertex fields.
- Particle and weather therefore keep an empty real-answer set. Their three observed extension names are classified separately as reference-unbacked diagnostic inputs and continue to receive Vitrail's existing synthesized constants. This is not a claim that those constants equal Iris's OpenGL generic-attribute values on every draw: generic attribute state is a different mechanism and may be undefined after array-backed draws.
- Sky `mc_midTexCoord` / `mc_Entity` and line-row `vaUV2` / `mc_Entity` warnings remain deliberately untouched until their exact Iris formats and transform paths are audited.

## Other remaining diagnostics

- Comparison-vs-ordinary shadow sampler declarations remain explicit. The current diagnostic records that the mixed declaration is undefined under Iris too; do not hide it with a backend alias without a defined reference behavior to implement.
- First-frame `nothing fills them yet` diagnostics for material maps remain evidence to investigate only where they survive into a persistent visual or semantic mismatch.
- The log still contains pack-authored menu/block/property warnings and game-pipeline format fallbacks. Their count is not a compatibility score.

## Open validation boundaries

- Re-run Photon, Complementary and/or MakeUp after the particle/weather diagnostic change and verify the particle/weather missing-real-attribute WARNs disappear without changing their draw paths. Exact numeric parity of reference-unbacked values is not asserted by this test.
- Audit sky vertex warnings against Iris 26.1 `ShaderKey`, the `POSITION`/`POSITION_TEX` formats and the exact transform/default behavior.
- Audit line-row warnings against Iris's line format and transform path before changing their classification.
- Keep comparison/ordinary shadow sampler conflicts explicit unless new reference evidence supplies a defined behavior.
- For PHASE 17 status changes, collect the required reviewed screenshot/reference evidence rather than promoting any pack from runtime logs alone.
- Continue fixing only generic owning-layer contracts. Do not add Bliss, Photon, Complementary, MakeUp or Solas production special cases.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `docs/phase17-compatibility.md`, `CONTRIBUTING.md`
- `common/src/main/java/dev/vitrail/render/GeometryProgram.java`
- `common/src/main/java/dev/vitrail/render/VertexInputDiagnostics.java`
- `common/src/main/java/dev/vitrail/render/EntityInputDiagnostics.java`
- `common/src/main/java/dev/vitrail/render/EntityProgram.java`
- `common/src/main/java/dev/vitrail/render/ParticleProgram.java`
- `common/src/main/java/dev/vitrail/render/WeatherProgram.java`
- `common/src/main/java/dev/vitrail/glsl/EntityVertex.java`
- `common/src/main/java/dev/vitrail/render/DumpedProgram.java`
- `common/src/main/java/dev/vitrail/render/DistantProgram.java`
- `common/src/main/java/dev/vitrail/render/FamilyWarmup.java`
- `common/src/main/java/dev/vitrail/pack/source/IncludeExpander.java`
- `tests/test_entity_reference_default.py`
- `tests/test_background_pipeline_warmup.py`
- `tests/test_preprocessor_macro_redefinition.py`
- companion Iris 26.1 `IrisVertexFormats.java`, `ShaderKey.java`, `ShaderAttributeInputs.java`, `VanillaCoreTransformer.java` and `CommonTransformer.java`
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
