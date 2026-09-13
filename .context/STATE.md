# Project State

Updated: 2026-09-13
Scope: active topic branch `feat/backend-neutral-sodium-terrain-hook` and draft PR #1

## Current focus

The active work is making Vitrail's backend-specific seams usable with Metallum/Metal while keeping shader-pack semantics in Vitrail and preserving the existing Vulkan behavior.

## Confirmed now

- Draft PR #1 targets `dev`; it is intentionally not ready to merge yet.
- The branch contains a backend-neutral Sodium terrain hook plus optional Metallum adapters for independent blending, mipmap generation, selective pipeline-cache eviction, shader-storage-buffer allocation, and storage-image allocation/commands.
- Shader-storage-buffer support is bridged end-to-end at the Vitrail boundary: Vitrail carries Minecraft `GpuBuffer` objects while Metallum determines the real storage resource kind from raw SPIR-V.
- `StorageImages` now uses the backend-neutral storage-image seam when the active backend provides it. Vulkan keeps its direct VMA/native-descriptor path; Metal carries backend-owned `GpuTexture`/`GpuTextureView` objects through the Minecraft facade.
- Storage-image policy remains in one Vitrail implementation: clear-at-birth selection, per-shadow-stage clears, relative resize, movable-volume detection, scratch allocation, and camera reanchor decisions are not duplicated in Metallum.
- The common `RenderPass.bindTexture` seam substitutes a backend-owned storage-image view only for names held by `StorageImages`; the original backend call and existing particle tail hook still run.
- Metallum companion code now reflects storage images, binds a storage resource as a texture without sampler state, creates writable 1D/2D/3D Metal textures, clears them with typed compute kernels, and copies exact regions with its blit encoder. Backend build head `881c4337426fe2dd88b08bcad2d029a00bfa3d71` passed GitHub Actions run `34761479404`.
- Vitrail head `0592b2b0097f5deeeca1250de4cbbc37701dc579` fixes the LWJGL `VkDependencyInfo` single-struct allocation error exposed by build run #33. Build/commit workflows for run #34 are pending at this checkpoint.
- Vitrail's custom shader-pack `ComputeShader` path is still Vulkan-specific: it accepts `VulkanDevice`, builds `VulkanBindGroupLayout.Entry` objects, and creates a Vulkan shader module. Storage-image resource plumbing therefore does not yet mean shader-pack compute programs can execute on Metal.
- Metal is not a fully supported shader-pack backend yet. Startup/backend guards remain conservative.
- Companion backend work lives in `DebuNeko233/metallum`, branch `feat/mc26.2-mrt-foundation`, draft PR #1. Treat that repository/PR as external evidence and re-check it before relying on its current state.

## Important incomplete areas

- Apple-Silicon runtime validation is still required; a green Java/Gradle build is not evidence that Metal rendering semantics are correct.
- Metal depth/stencil mipmap handling is still incomplete; the safe base-level fallback must remain until a correct path is implemented and validated.
- Shader-pack compute compilation/dispatch is still Vulkan-specific and is now the next hard backend boundary exposed by the storage-image work.
- Geometry-stage handling and remaining synchronization/startup boundaries are still part of the active migration.
- Metallum's storage-zero compute pipeline cache has an explicit `close()` and still needs to be wired into device teardown before this slice is considered lifecycle-complete.

## Repository evidence to verify first

- `README.md` — project purpose and supported user-facing scope.
- `docs/README.md` — documentation router and the project's core translation model.
- `docs/metallum-port.md` — current Metal-port implementation and validation status.
- `gradle.properties` — active Minecraft/Java/Sodium and toolchain versions.
- `CONTRIBUTING.md` plus `.github/workflows/` — branch, commit, changelog, and build gates.
- `common/src/main/java/dev/vitrail/render/storage/StorageImages.java` — current shared storage-image policy and Vulkan/Metal allocation paths.
- `common/src/main/java/dev/vitrail/render/ComputeShader.java` — remaining Vulkan-only shader-pack compute boundary.
- Draft PR #1 and the active branch diff against `dev` — what the current migration branch actually changes.
