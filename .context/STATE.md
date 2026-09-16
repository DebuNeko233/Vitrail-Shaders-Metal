# Project State

Updated: 2026-09-16
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — is active.
- Vitrail PHASE 17 has a five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Incomplete/unreviewed evidence is refused rather than promoted.
- `tests/phase17_collect_session.py` records exact runtime metadata and conservative raw observations from one real Metal session. Raw game-owned fallback warnings remain observations until compatibility review.
- `tests/phase17_verify_bundle.py` verifies an uploaded/reviewed `.tar.gz` without extracting it to disk: exactly one top-level directory, exactly `evidence.json`, `latest.log`, and `screenshot.png`, no links/extra files, valid PHASE 17 evidence schema, and matching log/screenshot SHA-256 values. It does not assign compatibility status.
- Companion Metallum `tools/run-vitrail-phase17-pack.sh` now requires exactly one fresh F2 screenshot and hashes the staged shader-pack artifact that Minecraft actually tested, rather than re-hashing the original source path after the run. The review bundle still never contains the shader pack.
- Current Vitrail head after PHASE 17 review-bundle hardening: `14afa1b3ed5087ca16d94c250b5c8b30c5baea47`.
- Current companion Metallum head after launcher hardening: `8ceab806081282e15bce0064708b525360c512f6`. Its consolidated Apple-Silicon CI run `35050550858` completed successfully.

## Active real-pack validation

- The first real matrix entry is Photon v1.3b.
- The currently running Photon hardware session intentionally records the earlier exact heads Vitrail `7adbde248eb155bda20cb7676fca61b24d5153e2` and Metallum `493ccb9f09768e0b8fd9ed24880fa9fcc5135b4a`. Later repository hardening does not invalidate that already-started run; its bundle must be reviewed against the heads it records.
- No Photon compatibility status has been assigned. Official compatibility statements, static source inspection, CI and warning counts are context only; the status must come from the captured real run plus compatibility/reference review.
- Unknown abnormal exits that do not match a high-confidence fatal marker remain unclassified when clean shutdown is absent. This conservative refusal is intentional; do not broaden fatal matching merely to force a `Broken` result.

## Open validation boundaries

- Review the Photon `.tar.gz` with the bundle verifier, then inspect the real log/screenshot and determine whether additional reference evidence is required before classification.
- Continue the real-pack matrix only from evidence-backed findings. Fix missing contracts/capabilities at the owning layer; do not add pack-specific hacks.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `CONTRIBUTING.md`
- `tests/phase17_compatibility.py`
- `tests/phase17_collect_session.py`
- `tests/phase17_verify_bundle.py`
- `tests/test_phase17_compatibility_contract.py`
- `tests/test_phase17_collect_session.py`
- `tests/test_phase17_verify_bundle.py`
- companion Metallum `tools/run-vitrail-phase17-pack.sh`
