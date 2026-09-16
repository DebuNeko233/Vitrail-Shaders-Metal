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
- [x] Review the first directly supplied Photon v1.3b production log: classify the observed run as `Broken` from the high-confidence `world0/prepare/vertex` compile failure, independently of visual-reference evidence.
- [x] Fix the independent Metallum storage-capability false negative that caused `RGBA16_FLOAT` compute/image work to be skipped despite the generic shader-writable texture backend being present.
- [x] Add generic Metallum shader compile diagnostics that preserve the compiler cause and print prepared GLSL context around a reported source line.
- [ ] Re-run Photon on current heads and verify the `RGBA16_FLOAT` storage-image warning is gone and the affected compute dispatches.
- [ ] Use the fresh compile-context lines around `world0/prepare/vertex` line 3166 to identify and fix the generic source/translation rule producing `unexpected SLASH` if the blocker remains.
- [ ] After the fatal blocker clears, review real draws and any visual/reference evidence needed before promoting Photon away from `Broken`.
- [ ] Continue the matrix through Complementary, BSL-family, Sildur-family and MakeUp with the same evidence discipline.

The direct Photon log that exposed these blockers reports Apple M5 Pro, macOS 27.0, Metal, and Vitrail build `7224c0e7`. It is sufficient to establish the fatal shader-compile result and the storage-capability observation, but it is not a visual-correctness result and does not replace a launcher bundle when exact artifact/head hashing is needed.

## P1 - Keep acceptance and documentation synchronized

- [ ] Keep both PRs Draft/open/unmerged while PHASE 17 real-pack acceptance is incomplete.
- [ ] Update PR evidence and `docs/metallum-port.md` when the Photon blocker is cleared or another material validation boundary changes.
- [x] Preserve Vitrail/Metallum ownership boundaries and the Vulkan/native fallback baseline in the first Photon fixes: storage policy stays in Vitrail, generic shader compiler diagnostics stay in Metallum, and no Photon-specific production path was added.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one pack or from CI alone.
