# Active Tasks

Updated: 2026-09-16
Scope: `feat/backend-neutral-sodium-terrain-hook`

## P0 - Complete PHASE 17 real shader-pack compatibility

The synthetic/runtime capability phases are closed; current work is evidence-backed real-pack compatibility on Apple Silicon.

- [x] Define the exact five public statuses: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`.
- [x] Refuse incomplete/unreviewed evidence instead of inferring compatibility from CI, warnings or a plausible image.
- [x] Collect Metal/device/exact heads, pack/log/screenshot SHA-256, real Vitrail draws, raw fallback observations, fatal observations and clean shutdown from one hardware session.
- [x] Keep the shader-pack artifact out of the review bundle.
- [x] Require exactly one fresh F2 screenshot per future launcher run.
- [x] Hash the staged artifact Minecraft actually tested for future launcher runs.
- [x] Add a Vitrail-owned bundle verifier that rejects malformed/extra files and verifies log/screenshot hashes without extracting the archive.
- [ ] Receive and verify the first Photon v1.3b review bundle from the in-progress hardware run.
- [ ] Review Photon log and screenshot; distinguish real compatibility defects from ordinary Iris-compatible game-owned behavior.
- [ ] Obtain/reference the minimum comparison evidence required by the classifier before assigning any non-Broken status.
- [ ] If Photon exposes a defect, trace it to a missing shader-pack contract, Minecraft contract, or Metal capability and fix that layer rather than adding a Photon-specific patch.
- [ ] Continue the matrix through Complementary, BSL-family, Sildur-family and MakeUp with the same evidence discipline.

The already-started Photon run records Vitrail `7adbde248eb155bda20cb7676fca61b24d5153e2` and Metallum `493ccb9f09768e0b8fd9ed24880fa9fcc5135b4a`. Review it against those exact recorded heads; repository hardening committed after launch does not retroactively change that session.

## P1 - Keep acceptance and documentation synchronized

- [ ] Keep both PRs Draft/open/unmerged while PHASE 17 real-pack acceptance is incomplete.
- [ ] Update PR evidence and `docs/metallum-port.md` when a real-pack result is actually classified or a material validation boundary changes.
- [ ] Preserve Vitrail/Metallum ownership boundaries and the Vulkan/native fallback baseline while fixing real-pack findings.
- [ ] Do not relax startup guards or claim general Metal shader-pack support from one pack or from CI alone.
