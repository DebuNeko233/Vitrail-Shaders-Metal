# Project State

Updated: 2026-09-13
Scope: active topic branch `feat/backend-neutral-sodium-terrain-hook` and draft PR #1

## Current focus

The active work is making Vitrail's backend-specific seams usable with Metallum/Metal while keeping shader-pack semantics in Vitrail and preserving the existing Vulkan behavior.

## Confirmed now

- Draft PR #1 targets `dev`; it is intentionally not ready to merge yet.
- The branch already contains a backend-neutral Sodium terrain hook plus optional Metallum adapters for independent blending, mipmap generation, selective pipeline-cache eviction, shader-storage-buffer allocation, and storage-image allocation/commands.
- Shader-storage-buffer support is bridged end-to-end at the Vitrail boundary: Vitrail carries Minecraft `GpuBuffer` objects while the backend determines the real storage resource kind from compiled shader information.
- Storage-image backend capabilities now exist, but `StorageImages` itself has not yet been migrated onto them. Its allocation/lifecycle policy still follows the existing Vulkan/VMA implementation.
- Metal is not a fully supported shader-pack backend yet. Startup/backend guards remain conservative.
- Companion backend work lives in `DebuNeko233/metallum`, branch `feat/mc26.2-mrt-foundation`, draft PR #1. Treat that repository/PR as external evidence and re-check it before relying on its current state.

## Important incomplete areas

- Apple-Silicon runtime validation is still required; a green Java/Gradle build is not evidence that Metal rendering semantics are correct.
- Metal depth/stencil mipmap handling is still incomplete; the safe base-level fallback must remain until a correct path is implemented and validated.
- Storage-image integration, geometry-stage handling, and remaining synchronization/startup boundaries are still part of the active migration.

## Repository evidence to verify first

- `README.md` — project purpose and supported user-facing scope.
- `docs/README.md` — documentation router and the project's core translation model.
- `docs/metallum-port.md` — current Metal-port implementation and validation status.
- `gradle.properties` — active Minecraft/Java/Sodium and toolchain versions.
- `CONTRIBUTING.md` plus `.github/workflows/` — branch, commit, changelog, and build gates.
- Draft PR #1 and the active branch diff against `dev` — what the current migration branch actually changes.
