# Project State

Updated: 2026-09-16
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration boundary remains strict: Vitrail owns shader-pack semantics, scheduling, fallback interpretation and compatibility status; Metallum owns generic Metal execution. The two Draft PRs remain open and unmerged.
- PHASE 2 and PHASE 5-16 have completed their recorded Apple-Silicon real-device acceptance. PHASE 17 — Real Shader Pack Compatibility — is active.
- Vitrail PHASE 17 has a five-status conservative classifier: `Supported`, `Partially Supported`, `Fallback`, `Unsupported`, `Broken`. Incomplete/unreviewed evidence is refused rather than promoted.
- `tests/phase17_collect_session.py` records exact runtime metadata and conservative raw observations from one real Metal session. Its production-log matching now covers normal Fabric/Vitrail lines that do not carry a literal `(Vitrail)` logger prefix.
- `tests/phase17_verify_bundle.py` verifies an uploaded/reviewed `.tar.gz` without extracting it to disk: exactly one top-level directory, exactly `evidence.json`, `latest.log`, and `screenshot.png`, no links/extra files, valid PHASE 17 evidence schema, and matching log/screenshot SHA-256 values. It does not assign compatibility status.
- Companion Metallum `tools/run-vitrail-phase17-pack.sh` requires exactly one fresh F2 screenshot, hashes the staged shader-pack artifact that Minecraft actually tested, and suppresses macOS AppleDouble metadata when writing the review tarball. The review bundle never contains the shader pack.

## Active real-pack validation

- The first real matrix entry is Photon v1.3b.
- A directly supplied Apple M5 Pro / macOS 27.0 / Metal `latest.log` from Vitrail build `7224c0e7` is high-confidence `Broken` execution evidence: Photon opens and reaches world warmup, then `world0/prepare/vertex` fails in `GlslCompiler` with `0:3166: syntax error, unexpected SLASH`; Vitrail stops drawing the pack while Minecraft saves normally. This is a pack-chain shader compile failure, not a native Metal/device crash.
- The same run exposed an independent false negative in Vitrail's compute/storage capability query: `RGBA16_FLOAT` `colortex4` was declared shader-written, but `GpuFormats.storageCapable()` only consulted Vulkan format bits and therefore returned false on Metallum. Commit `0334378e0dc3d21e2291ded7c4e2736fd361ac2c` now treats Vitrail's generic `ShaderWritableTextureBackend` seam as storage-capable before falling back to Vulkan format features; `9fc4663cd93ac8c912fafa24ad75a948ef6641f3` locks that behavior into the compute-storage contract. Vitrail PR CI is 10/10 successful at that head.
- The `unexpected SLASH` root cause is not yet proven. Companion Metallum commit `a0fa63a5` adds generic compile-failure source context from the exact prepared GLSL handed to `GlslCompiler`, and current Metallum head `ac785de220ce284cb2cdedc524fc3cbbd80df74e` runs that diagnostic contract in the consolidated Apple-Silicon CI; run `35054010201` completed successfully. No Photon-specific translation rule has been added.
- Photon remains `Broken` until a fresh run on these fixes clears or advances the first fatal blocker. The storage-capability fix alone does not change its compatibility status, and no visual correctness claim follows from this log-only run.

## Open validation boundaries

- Re-run Photon on the current Vitrail/Metallum branches and inspect the fresh `latest.log`. If `world0/prepare/vertex` still fails, the exception should now print the prepared GLSL around line 3166; use that source evidence to fix the owning generic translator/compiler path rather than guessing from the pack source.
- Verify that the previous `RGBA16_FLOAT` storage-image warning is gone and that the affected compute is actually dispatched before closing that defect.
- Continue the real-pack matrix only from evidence-backed findings. Fix missing contracts/capabilities at the owning layer; do not add pack-specific hacks.
- Keep both PRs Draft/open/unmerged until the PHASE 17 matrix and broader migration acceptance policy permit merge.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `CONTRIBUTING.md`
- `common/src/main/java/dev/vitrail/render/GpuFormats.java`
- `common/src/main/java/dev/vitrail/render/storage/ShaderWritableTextureBackend.java`
- `tests/test_compute_storage_contract.py`
- `tests/phase17_compatibility.py`
- `tests/phase17_collect_session.py`
- `tests/phase17_verify_bundle.py`
- companion Metallum `src/main/java/com/metallum/render/MetalDevice.java`
- companion Metallum `tools/ci-shader-diagnostics.py`
- companion Metallum `tools/run-vitrail-phase17-pack.sh`
