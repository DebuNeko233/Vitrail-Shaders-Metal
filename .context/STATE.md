# Project State

Updated: 2026-09-16
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — is active.
- Vitrail PHASE 17 has a five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Incomplete/unreviewed evidence is refused rather than promoted.
- `tests/phase17_collect_session.py` records exact runtime metadata and conservative raw observations from one real Metal session. Its production-log matching covers normal Fabric/Vitrail lines that do not carry a literal `(Vitrail)` logger prefix.
- `tests/phase17_verify_bundle.py` verifies an uploaded/reviewed `.tar.gz` without extracting it to disk: exactly one top-level directory, exactly `evidence.json`, `latest.log`, and `screenshot.png`, no links/extra files, valid PHASE 17 evidence schema, and matching log/screenshot SHA-256 values. It does not assign compatibility status.
- Companion Metallum `tools/run-vitrail-phase17-pack.sh` requires exactly one fresh F2 screenshot, hashes the staged shader-pack artifact that Minecraft actually tested, and suppresses macOS AppleDouble metadata when writing the review tarball. The review bundle never contains the shader pack.

## Active real-pack validation

- The first real matrix entry is Photon v1.3b.
- A second reviewed Apple M5 Pro / macOS 27.0 / Metal log runs Vitrail build `94e2090a` and still reaches the same high-confidence pack-chain fatal while warming `world0/prepare/vertex`: Minecraft's `GlslCompiler` reports `0:3166: syntax error, unexpected SLASH`, and Metallum's prepared-source diagnostic shows the surrounding source blank except for a lone `/` immediately after the reported line. Vitrail stops the pack, Minecraft continues, saves the world, and later reaches `Stopping!`. Photon therefore remains `Broken`; this is not a native Metal/device crash.
- That run proves the earlier Vitrail line-spliced `//` lexer correction did not clear Photon's fatal. The lexer correction remains a valid independent lexical fix, but it is not the Photon root cause.
- The same run real-device-verifies the important half of the storage-capability correction: `colortex4` and its alternate are now allocated as `RGBA16_FLOAT ... writable from a compute`, the previous `makes no storage image` rejection is absent, and the pack's RGBA16F/3D storage images are allocated. The log contains no compute-dispatch evidence before the shader compile fatal, so actual compute dispatch is still unverified.
- Exact source inspection identifies the `unexpected SLASH` root cause in Metallum's generic shader preparation. Photon uses the legal line-comment switch idiom `//* ... //*/`; the old Metallum path removed block comments first with an independent `/\*.*?\*/` regex, so it could start a block comment at the second slash of `//*`, consume through a later block closer, and leave the first slash as live GLSL. The lone prepared `/` is exactly that failure mode.
- Companion Metallum now uses a one-pass lexical `GlslCommentStripper` instead of independent block/line comment regexes. Its behavior contract compiles and executes the actual Java stripper and covers `//* ... //*/`, genuine block comments, comment delimiters inside line comments, spliced line comments, ordinary division, token separation, and unterminated block comments. Metallum head `ab8728c4f1551181795d049d151f606186b70b1e` completed consolidated Apple-Silicon CI successfully in run `35060087502`, including the new comment contract and full Gradle build.
- No Photon name, shader-family rule, compatibility verdict or Vitrail target semantic was added to Metallum production code. The fix is backend-generic shader-source lexing.
- Photon remains `Broken` until a fresh real-device run on the lexical Metallum sanitizer clears or advances the fatal blocker. CI success proves the implementation/contracts exercised by CI, not Photon execution or visual correctness.

## Open validation boundaries

- Re-run Photon with Vitrail `94e2090a` or later and Metallum `ab8728c4` or later. First acceptance gate: `world0/prepare/vertex` must compile past the former lone-slash failure. If a later shader fails, use the retained prepared-source diagnostic to identify the next generic contract rather than adding a pack-specific workaround.
- Once the compile blocker clears, verify a real Photon frame reaches compute execution. Only then close the remaining dispatch half of the earlier RGBA16F storage finding.
- After the fatal blocker clears, review actual Vitrail draws and visual/reference evidence before promoting Photon away from `Broken`.
- Continue the real-pack matrix only from evidence-backed findings. Fix missing contracts/capabilities at the owning layer; do not add pack-specific hacks.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `CONTRIBUTING.md`
- `common/src/main/java/dev/vitrail/glsl/GlslLexer.java`
- `common/src/main/java/dev/vitrail/render/GpuFormats.java`
- `common/src/main/java/dev/vitrail/render/storage/ShaderWritableTextureBackend.java`
- `tests/test_glsl_line_comment_splice.py`
- `tests/test_compute_storage_contract.py`
- `tests/phase17_compatibility.py`
- `tests/phase17_collect_session.py`
- `tests/phase17_verify_bundle.py`
- companion Metallum `src/main/java/com/metallum/render/GlslCommentStripper.java`
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
- companion Metallum `tools/ci-shader-diagnostics.py`
- companion Metallum `tools/run-vitrail-phase17-pack.sh`
