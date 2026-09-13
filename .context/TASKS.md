# Active Tasks

Updated: 2026-09-13
Scope: active topic branch `feat/backend-neutral-sodium-terrain-hook` and draft PR #1

## P0 — Complete the Metal storage-image path

Status: active, implementation/lifecycle fixes landed; final compile checkpoint still open

Goal:
Make `image.NAME` resources work through Metallum using the backend-neutral storage-image capabilities without moving shader-pack policy into the backend or regressing the Vulkan path.

Acceptance criteria:

- [x] `StorageImages` can allocate through the backend-neutral capability when the active backend provides it, while the existing Vulkan/VMA path remains valid.
- [x] Vitrail carries Minecraft `GpuTexture`/`GpuTextureView` objects across the Metal seam; no Metal native handle or argument index becomes part of Vitrail policy code.
- [x] The image uniform is bound as a storage texture and an optional sampler alias remains a sampled texture without inventing a second logical resource/binding.
- [x] Clear-at-birth, pack-marked clears, relative resize, movable-volume detection, scratch allocation, and camera reanchor decisions remain Vitrail policy and work through backend commands on Metal.
- [ ] Vitrail build/commit gates are green on the final storage-image head. Build #33 reached javac and exposed two incorrect `VkDependencyInfo.calloc(1, stack)` single-struct allocations; `0592b2b0097f5deeeca1250de4cbbc37701dc579` fixed those. Storage-image birth-state cleanup landed at `8b30e6f9788aca8c04b439e91f62b253df02f1d6`; its successor workflows are pending.
- [x] Companion Metallum code through storage-image draw binding is compile-green: head `881c4337426fe2dd88b08bcad2d029a00bfa3d71`, Actions run `34761479404`.
- [x] `MTLStorageTexturePipelines.close()` is wired into `MetalDevice.close()` at Metallum commit `f9bc3aee46e1491536ce0601a15d354e0c4e4cce`; its build gate is pending before the lifecycle fix is considered validated.
- [x] `newlyBorn()` no longer marks an allocation prepared before backend birth preparation succeeds. `laidOut` is committed only after successful preparation, and a preparation exception destroys the allocated set, clears bindings, and forces the next attempt to allocate from scratch.
- [x] `docs/metallum-port.md` distinguishes the implemented resource path from the still-Vulkan-only shader-pack compute path and from runtime validation.

Relevant repository evidence:

- `common/src/main/java/dev/vitrail/render/storage/StorageImages.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageCommands.java`
- `common/src/main/java/dev/vitrail/mixin/RenderPassMixin.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/MetalDeviceMixin.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/MetalCommandEncoderMixin.java`
- `docs/metallum-port.md`
- Vitrail draft PR #1 and companion Metallum draft PR #1

## P0 — Backend-neutralize shader-pack compute

Status: next hard boundary exposed by storage-image integration

Goal:
Allow Vitrail custom compute programs to compile and dispatch without requiring `VulkanDevice`, `VulkanBindGroupLayout.Entry`, or Vulkan shader modules, while preserving the current Vulkan implementation and the same storage-resource naming/binding policy.

Why now:
The Metal storage-image resource path can allocate, bind, clear, and reanchor writable textures, but `ComputeShader.compile(...)` still creates Vulkan-native modules/layout entries. Packs that depend on `shadowcomp` or other compute programs therefore cannot yet exercise those Metal storage resources end-to-end.

Acceptance criteria:

- [ ] Define the smallest backend-neutral compute compile/dispatch capability required by Vitrail rather than mirroring the whole Vulkan compute implementation.
- [ ] Keep SPIR-V resource naming, storage-buffer/image policy, dispatch scheduling, and pack semantics in Vitrail.
- [ ] Metallum owns MSL translation, `MTLComputePipelineState`, argument binding, encoder/fence lifetime, and native resource handles.
- [ ] Existing Vulkan compute behavior remains unchanged behind its provider.
- [ ] Compile-check the exact Minecraft 26.2, LWJGL SPIRV-Cross, and Metal APIs used before committing the bridge.
- [ ] Add an Apple-Silicon runtime case that writes a storage image/buffer in compute and consumes the result in a later render/compute stage.

Relevant repository evidence:

- `common/src/main/java/dev/vitrail/render/ComputeShader.java`
- current compute dispatch/descriptor mixins under `common/src/main/java/dev/vitrail/`
- companion Metallum shader compiler and command encoder

## P0 — Runtime-validate before merge or backend advertisement

Status: blocked on completion of the remaining Metal slices and an Apple-Silicon run

Goal:
Prove the Metal path semantically correct before changing conservative startup/backend guards or merging the draft migration work.

Acceptance criteria:

- [ ] Execute and record the current runtime validation matrix in `docs/metallum-port.md`; do not duplicate that matrix here.
- [ ] Re-run Vulkan regression coverage for behavior touched by backend-neutralization.
- [ ] Keep incomplete or unvalidated Metal capabilities out of user-facing "fully supported" paths.
- [ ] Keep the Vitrail and companion Metallum PRs in draft/unmerged state until their stated validation requirements are satisfied.

Relevant repository evidence:

- `docs/metallum-port.md`
- Draft PR #1

## P1 — Continue backend-neutralization after compute

Status: queued

Goal:
Address geometry-stage support and the remaining synchronization/startup boundaries using the same narrow-capability model.

Constraints:

- Preserve Vitrail shader-pack semantics and the Vulkan baseline.
- Verify each version-sensitive API against the exact versions in use before implementation.
- Do not add a broad backend abstraction merely to make the port look uniform.
