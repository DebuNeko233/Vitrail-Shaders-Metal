# Active Tasks

Updated: 2026-09-16
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
- [x] Review Photon v1.3b production execution and classify the observed run as `Broken` from the high-confidence `world0/prepare/vertex` compile failure, independently of visual-reference evidence.
- [x] Fix the independent Metallum storage-capability false negative that caused `RGBA16_FLOAT` compute/image work to be rejected despite the generic shader-writable texture backend being present.
- [x] Real-device verify that the old `RGBA16_FLOAT` `makes no storage image` rejection is gone: the current log allocates `colortex4` as `RGBA16_FLOAT ... writable from a compute` and allocates the pack's storage images.
- [ ] Verify actual Photon compute dispatch after the shader compile fatal is cleared; the current log reaches allocation but contains no dispatch evidence.
- [x] Add generic Metallum shader compile diagnostics that preserve the compiler cause and print prepared GLSL context around a reported source line.
- [x] Use the fresh compile context to identify the `unexpected SLASH` root cause: Metallum's old independent block-comment regex starts at the second slash of the pack's `//*` line-comment switch and can leave the first slash live.
- [x] Replace Metallum's independent comment regexes with a generic one-pass lexical stripper and lock `//* ... //*/`, real block comments, spliced line comments, division and token separation into executable CI coverage. Metallum `ab8728c4` is CI-green in run `35060087502`.
- [ ] Re-run Photon on Metallum `ab8728c4` or later and verify `world0/prepare/vertex` compiles past the former lone-slash blocker.
- [ ] If another compile/runtime blocker appears, use the prepared-source/runtime diagnostics to fix the owning generic contract rather than adding a real-pack special case.
- [ ] After the fatal blocker clears, review real draws and visual/reference evidence needed before promoting Photon away from `Broken`.
- [ ] Continue the matrix through Complementary, BSL-family, Sildur-family and MakeUp with the same evidence discipline.

The latest reviewed Photon log reports Apple M5 Pro, macOS 27.0, Metal and Vitrail build `94e2090a`. It proves the previous storage-capability rejection is gone and preserves the fatal shader-compile evidence, but it is not a visual-correctness result and it does not prove compute dispatch.

## P1 - Keep acceptance and documentation synchronized

- [ ] Keep both PRs Draft/open/unmerged while PHASE 17 real-pack acceptance is incomplete.
- [ ] Update `docs/metallum-port.md` after the current Photon compile blocker is cleared or another material validation boundary changes.
- [x] Preserve Vitrail/Metallum ownership boundaries in the Photon fixes: storage policy stays in Vitrail; generic shader preparation/compile diagnostics stay in Metallum; no Photon-specific production path was added.
- [x] Record that the Vitrail line-spliced-comment lexer correction is independent: real-device Vitrail `94e2090a` still hit the Photon slash fatal, so that fix is not used as evidence that Photon advanced.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one pack or from CI alone.
