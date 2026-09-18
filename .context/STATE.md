# Project State

Updated: 2026-09-18
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- The Vitrail code line contains the entity-reference-default and particle/weather diagnostic fixes, the source-classified sky/line reference-unbacked handling, and the sky-ownership replay recorded below; companion Metallum is `82a0c75e53e28390472b3c26b569cdc2335d90b4` on `feat/mc26.2-mrt-foundation`.
- The latest hardware evidence baseline is Vitrail `bc5e180a8580757bf8863abb2d7c130ca1476913` paired with Metallum `82a0c75e53e28390472b3c26b569cdc2335d90b4`. PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — remains active.
- PHASE 17 still uses the five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Runtime progress, CI, warning counts or a plausible frame do not by themselves promote a pack.

## Latest real-device session

- The 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal run of Vitrail `0caa74ba` reaches the new zero-output sky-coverage path, but exposes two Vitrail contract bugs before the ownership picture can be accepted. Photon `gbuffers_skybasic` generates `ofCoverage` at colour rank 0, then fails shader compilation because the wrapper calls `ofOrderOutputs()` while the zero-pack-output path did not emit that helper. Later Solas reaches a real claimed-sky draw and fails in `SkyOwnership.claim`: the sibling pipeline marked pack colour slots unused even though the already-open render pass has real attachments there, so Minecraft 26.2 rejects the pipeline on attachment-format validation. Both failures are Vitrail-side; neither requires a Metallum change.
- The same run confirms the earlier 26.2 direct/indexed Mixin correction is past its former startup blocker: sky drawing reaches the ownership hook rather than failing injection before the first sky draw. Photon reaches a first full frame before the later pack switch; Solas reaches its disc first draw before the ownership pipeline mismatch.
- A later 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal attempt runs Vitrail module cache `44742449` on Minecraft 26.2 and reaches the first level frame, then aborts before sky drawing because `SkyRendererMixin.vitrail$claim` requires seven `RenderPass.draw(IIII)` matches but the 26.2 class contains only two. The crash reports `(2/7) succeeded`; the other five sky methods use `drawIndexed(IIIII)`. This is a Vitrail mixin-shape regression in the unverified sky-ownership work, not evidence about Bliss rendering.
- A 2026-09-18 Apple M5 Pro / macOS 27.0 / Metal log runs Vitrail build `bc5e180a`, Metallum branch head `82a0c75e`, Sodium 0.9.2 and Minecraft 26.2. The user confirms these launcher builds continue to come from Vitrail `feat/backend-neutral-sodium-terrain-hook` and Metallum `feat/mc26.2-mrt-foundation`.
- The log contains no Vitrail/Metallum log-level `ERROR` or `FATAL` and reaches clean shutdown after the exercised pack reloads.
- The corrected entity and particle/weather diagnostics remain clean: there is no `at_midBlock` / `at_tangent` regression and the exercised particle/weather draw paths remain active.
- The `bc5e180a` sky/line diagnostic classification is hardware-verified for every path exercised in this run: the former sky `mc_Entity` / `mc_midTexCoord` and line `vaUV2` missing-real-attribute warnings are absent while Photon sky/line and Solas sky draws still record. The Solas line draw was not triggered in this particular run, so that one observed `mc_Entity` line path remains source-classified but not re-exercised here.
- The Metallum `82a0c75e` fixed-array accounting change is hardware-verified by routing Bliss `world0/composite2` back to the direct resource path. The log no longer reports an Argument Buffer for that 15-resource / 14-sampled-image pass, while Photon's genuinely wide `deferred4` remains on the Argument Buffer path.
- Bliss still renders incorrectly after that backend correction. Source/runtime audit narrows the next Vitrail-side candidate to claimed sky ownership: a pack-authored fragment `discard` kills the ordinary translated coverage epilogue, after which `SceneSeed` can treat the sky pixel as unanswered and paint the game's sky back into the pack target.
- This is execution and diagnostic evidence, not visual/reference acceptance.

## Vertex-input findings

- Iris 26.1 defines `at_midBlock` on `IrisVertexFormats.TERRAIN`, while `IrisVertexFormats.ENTITY` contains `iris_Entity`, `mc_midTexCoord` and `at_tangent` but not `at_midBlock`. Iris shader keys use `ENTITY` for ordinary entities and shadow entities.
- Vitrail keeps `EntityVertex.ANSWERED`, the three appended entity elements and entity stride unchanged. `VertexInputDiagnostics` distinguishes real mesh-backed answers from inputs that the Iris reference format also leaves without a backing vertex array; `EntityInputDiagnostics` delegates to that classifier.
- Iris 26.1 uses `DefaultVertexFormat.PARTICLE` for `PARTICLES`, `PARTICLES_TRANS` and `WEATHER`. That format carries none of `mc_Entity`, `mc_midTexCoord` or `at_tangent`; Iris's core transform path does not turn those three into real particle/weather vertex fields. Vitrail therefore keeps an empty real-answer set and classifies those names only as reference-unbacked diagnostics.
- Iris 26.1 sky keys use `POSITION`, `POSITION_COLOR`, `POSITION_TEX` or `POSITION_TEX_COLOR`. None carries `mc_Entity` or `mc_midTexCoord`. With zero entity components, `VanillaCoreTransformer` leaves pack-declared `mc_Entity` unbacked; an explicit `mc_midTexCoord` likewise does not become a physical sky element. Vitrail classifies both as reference-unbacked for sky diagnostics without changing any sky format.
- Iris 26.1 `LINES` uses `DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH`, which carries neither an entity id nor UV2. `VanillaCoreTransformer` renames `vaUV2` to an `iris_UV2` input even when `hasLight()` is false, while no UV2 element backs that input; zero entity components likewise leave pack-declared `mc_Entity` without backing data. Vitrail classifies both as reference-unbacked for line diagnostics while leaving `LinesVertex.ANSWERED` and the line ABI unchanged.
- These reference-unbacked classifications do **not** claim value-for-value parity with OpenGL generic-attribute state on every draw. Vitrail continues to supply its existing deterministic synthesized constants; no constant value changed in this diagnostic work.

## Sky ownership / scene-seed finding

- The scene seed cuts itself only against the depth-valued coverage texture. Geometry coverage is normally written by the translated fragment epilogue after the pack's fragment `main`, so a pack-authored `discard` prevents that write.
- That fragment-survival rule is correct for terrain, entities and other ordinary geometry: a discarded fragment is a pixel the pass did not cover. It is not sufficient for the sky elements whose mesh metadata says `covers=true`. Those elements mean the pack owns every pixel the mesh spans; under the reference renderer a discarded pack fragment does not cause the vanilla sky shader to be drawn there afterwards.
- The fix is Vitrail-only and sky-only. A claimed sky draw is replayed through a second pipeline that reuses the exact translated pack vertex stage and the same bound vertex buffer, but writes only `gl_FragCoord.z` into the existing coverage attachment. The pack fragment stage is not run on this replay, so its colour writes and discards cannot change the ownership answer.
- The replay uses the pack vertex stage rather than vanilla placement because a pack may change `gl_Position`; the claim must rasterize the same pixels as the sky it belongs to. The horizon cone is replayed immediately after its own ordinary draw while its vertex buffer is still bound.
- The coverage value remains a depth rather than a boolean. `SceneSeed` can therefore still carry a game feature whose live depth stands in front of the claimed sky. No terrain/entity/particle/hand coverage path is changed.
- The ownership sibling is prepared only when the ordinary sky program really has a coverage attachment. A device/compiler refusal of the sibling leaves the existing fragment-written coverage in place and is diagnostic rather than disabling the pack sky.
- Minecraft 26.2 records the top/dark discs and sunrise with direct `draw`, while sun, moon, stars, End sky and End flash use `drawIndexed`. Ownership replay mirrors that split with strict injector counts (2 direct targets for the non-main-disc hook, 5 indexed targets) and repeats the exact indexed arguments while leaving the game's bound index buffer standing.
- The observed `gbuffers_skybasic` programs add a second edge case: they declare zero pack colour outputs. The translator previously refused coverage solely because `maxFragmentOutput` was `-1`, so the ownership sibling had no attachment to write. Zero-output coverage is now legal at colour rank 0; `GeometryProgram` omits the inert default colortex attachment in that shape, leaving the single slot for the coverage texture. The ordinary fragment still obeys `discard`; only the sky sibling turns semantic ownership into a mark afterwards.
- This is source/implementation evidence until the new code is exercised on hardware. The observed Bliss sky anomaly remains the primary visual acceptance case; no Bliss name or pack-specific branch is used by the fix.

## Other remaining diagnostics

- Comparison-vs-ordinary shadow sampler declarations remain explicit. The current diagnostic records that the mixed declaration is undefined under Iris too; do not hide it with a backend alias without a defined reference behavior to implement.
- First-frame `nothing fills them yet` diagnostics for material maps remain evidence to investigate only where they survive into a persistent visual or semantic mismatch.
- The log still contains pack-authored menu/block/property warnings and game-pipeline format fallbacks. Their count is not a compatibility score.

## Open validation boundaries

- Hardware-run the sky ownership replay with Bliss and verify `gbuffers_skybasic` no longer lets the scene seed restore vanilla sky over a claimed disc/cone region after pack fragment discard.
- Exercise the End sky branch if practical, because it is the other `covers=true` sky branch.
- If Solas lines are triggered again, reconfirm the source-classified `mc_Entity` line warning remains absent; the latest `bc5e180a` run did not hit that draw.
- Keep comparison/ordinary shadow sampler conflicts explicit unless new reference evidence supplies a defined behavior.
- For PHASE 17 status changes, collect the required reviewed screenshot/reference evidence rather than promoting any pack from runtime logs alone.
- Continue fixing only generic owning-layer contracts. Do not add Bliss, Photon, Complementary, MakeUp or Solas production special cases.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `docs/phase17-compatibility.md`, `CONTRIBUTING.md`
- `common/src/main/java/dev/vitrail/render/GeometryProgram.java`
- `common/src/main/java/dev/vitrail/render/SkyProgram.java`
- `common/src/main/java/dev/vitrail/render/SkyOwnership.java`
- `common/src/main/java/dev/vitrail/render/SkyDraw.java`
- `common/src/main/java/dev/vitrail/render/HorizonCone.java`
- `common/src/main/java/dev/vitrail/mixin/SkyRendererMixin.java`
- `common/src/main/java/dev/vitrail/render/VertexInputDiagnostics.java`
- `common/src/main/java/dev/vitrail/render/EntityInputDiagnostics.java`
- `common/src/main/java/dev/vitrail/render/EntityProgram.java`
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
- `tests/test_sky_claim_coverage.py`
- `tests/test_background_pipeline_warmup.py`
- `tests/test_preprocessor_macro_redefinition.py`
- companion Iris 26.1 `IrisVertexFormats.java`, `ShaderKey.java`, `ShaderAttributeInputs.java`, `VanillaCoreTransformer.java` and `CommonTransformer.java`
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
