# Project State

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- The Vitrail code line contains the entity-reference-default and particle/weather diagnostic fixes plus the source-classified sky/line reference-unbacked handling recorded below; companion Metallum remains `54ff6f0e22b153ea206cc726f68bccaee6ce4e70`.
- The latest hardware evidence baseline is Vitrail `37d06c12890ade6c940a07939ca49d1cc03b0cbd` paired with the same Metallum feature branch. PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — remains active.
- PHASE 17 still uses the five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Runtime progress, CI, warning counts or a plausible frame do not by themselves promote a pack.

## Latest real-device session

- A 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal log runs Vitrail build `37d06c12`, Metallum 0.0.24, Sodium 0.9.2 and Minecraft 26.2. The user confirms these launcher builds continue to come from Vitrail `feat/backend-neutral-sodium-terrain-hook` and Metallum `feat/mc26.2-mrt-foundation`.
- The log contains no Vitrail/Metallum log-level `ERROR` or `FATAL`, records eight first-full-frame events across the exercised pack reloads, and reaches clean `Stopping!`.
- The corrected entity diagnostic remains verified: the whole log contains no `at_midBlock` diagnostic.
- The particle/weather diagnostic change is now real-device verified. Solas, Photon, MakeUp, Complementary and Bliss all record particle and weather draws, while the former particle/weather `mc_Entity`, `mc_midTexCoord` and `at_tangent` missing-real-attribute warnings are absent.
- Before the sky/line source classification below, the remaining synthesized vertex warnings are exactly five sky rows and two line rows in this log: Solas sky asks for `mc_Entity` three times, Photon sky asks for `mc_midTexCoord` twice, Solas lines ask for `mc_Entity` once and Photon lines ask for `vaUV2` once. All seven affected draw paths still record their first draw.
- This is execution and diagnostic evidence, not visual/reference acceptance.

## Vertex-input findings

- Iris 26.1 defines `at_midBlock` on `IrisVertexFormats.TERRAIN`, while `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but not `at_midBlock`. Iris shader keys use `ENTITY` for ordinary entities and shadow entities.
- Vitrail keeps `EntityVertex.ANSWERED`, the three appended entity elements and entity stride unchanged. `VertexInputDiagnostics` distinguishes real mesh-backed answers from inputs that the Iris reference format also leaves without a backing vertex array; `EntityInputDiagnostics` delegates to that classifier.
- Iris 26.1 uses `DefaultVertexFormat.PARTICLE` for `PARTICLES`, `PARTICLES_TRANS` and `WEATHER`. That format carries none of `mc_Entity`, `mc_midTexCoord` or `at_tangent`; Iris's core transform path does not turn those three into real particle/weather vertex fields. Vitrail therefore keeps an empty real-answer set and classifies those names only as reference-unbacked diagnostics.
- Iris 26.1 sky keys use `POSITION`, `POSITION_COLOR`, `POSITION_TEX` or `POSITION_TEX_COLOR`. None carries `mc_Entity` or `mc_midTexCoord`. With zero entity components, `VanillaCoreTransformer` leaves pack-declared `mc_Entity` unbacked; an explicit `mc_midTexCoord` likewise does not become a physical sky element. Vitrail now classifies both as reference-unbacked for sky diagnostics without changing any sky format.
- Iris 26.1 `LINES` uses `DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH`, which carries neither an entity id nor UV2. `VanillaCoreTransformer` renames `vaUV2` to an `iris_UV2` input even when `hasLight()` is false, while no UV2 element backs that input; zero entity components likewise leave pack-declared `mc_Entity` without backing data. Vitrail now classifies both as reference-unbacked for line diagnostics while leaving `LinesVertex.ANSWERED` and the line ABI unchanged.
- These reference-unbacked classifications do **not** claim value-for-value parity with OpenGL generic-attribute state on every draw. Vitrail continues to supply its existing deterministic synthesized constants; no constant value changed in this diagnostic work.

## Other remaining diagnostics

- Comparison-vs-ordinary shadow sampler declarations remain explicit. The current diagnostic records that the mixed declaration is undefined under Iris too; do not hide it with a backend alias without a defined reference behavior to implement.
- First-frame `nothing fills them yet` diagnostics for material maps remain evidence to investigate only where they survive into a persistent visual or semantic mismatch.
- The log still contains pack-authored menu/block/property warnings and game-pipeline format fallbacks. Their count is not a compatibility score.

## Open validation boundaries

- Re-run at least Solas and Photon after the sky/line diagnostic change. Verify the five sky and two line missing-real-attribute WARNs disappear while the same sky and line draw paths still record real draws.
- Do not interpret that rerun as numeric parity of the reference-unbacked values; the change is diagnostic classification only.
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
- `common/src/main/java/dev/vitrail/render/SkyProgram.java`
- `common/src/main/java/dev/vitrail/render/ParticleProgram.java`
- `common/src/main/java/dev/vitrail/render/WeatherProgram.java`
- `common/src/main/java/dev/vitrail/glsl/EntityVertex.java`
- `common/src/main/java/dev/vitrail/glsl/LinesVertex.java`
- `common/src/main/java/dev/vitrail/glsl/SkyVertex.java`
- `common/src/main/java/dev/vitrail/render/DumpedProgram.java`
- `common/src/main/java/dev/vitrail/render/DistantProgram.java`
- `common/src/main/java/dev/vitrail/render/FamilyWarmup.java`
- `common/src/main/java/dev/vitrail/pack/source/IncludeExpander.java`
- `tests/test_entity_reference_default.py`
- `tests/test_background_pipeline_warmup.py`
- `tests/test_preprocessor_macro_redefinition.py`
- companion Iris 26.1 `IrisVertexFormats.java`, `ShaderKey.java`, `ShaderAttributeInputs.java`, `VanillaCoreTransformer.java` and `CommonTransformer.java`
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
