# Active Tasks

Updated: 2026-09-13
Scope: active topic branch `feat/backend-neutral-sodium-terrain-hook` and draft PR #1

## P0 — Complete the Metal storage-image path

Status: active

Goal:
Make `image.NAME` resources work through Metallum using the new backend-neutral storage-image capabilities without moving shader-pack policy into the backend or regressing the Vulkan path.

Why now:
The branch already defines and bridges `StorageImageBackend` and `StorageImageCommands`; the missing work is wiring those capabilities into the existing `StorageImages` lifecycle and binding flow.

Acceptance criteria:

- [ ] `StorageImages` can allocate through the backend-neutral capability when the active backend provides it, while the existing Vulkan/VMA path remains valid.
- [ ] Vitrail carries Minecraft `GpuTexture`/`GpuTextureView` objects across the Metal seam; no Metal native handle or argument index becomes part of Vitrail policy code.
- [ ] The image uniform is bound as a storage texture and an optional sampler alias remains a sampled texture without inventing a second logical resource/binding.
- [ ] Clear-at-birth, pack-marked clears, relative resize, movable-volume detection, scratch allocation, and camera reanchor decisions remain Vitrail policy and work through backend commands on Metal.
- [ ] Relevant Vitrail and companion Metallum compile/commit gates are green after the slice.
- [ ] `docs/metallum-port.md` is updated to distinguish compile support from runtime validation.

Relevant repository evidence:

- `common/src/main/java/dev/vitrail/render/storage/StorageImages.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageBackend.java`
- `common/src/main/java/dev/vitrail/render/storage/StorageImageCommands.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/MetalDeviceMixin.java`
- `common/src/main/java/dev/vitrail/mixin/metallum/MetalCommandEncoderMixin.java`
- `docs/metallum-port.md`
- Vitrail draft PR #1 and companion Metallum draft PR #1

## P0 — Runtime-validate before merge or backend advertisement

Status: blocked on completion of the remaining Metal slices and an Apple-Silicon run

Goal:
Prove the Metal path semantically correct before changing conservative startup/backend guards or merging the draft migration work.

Acceptance criteria:

- [ ] Execute and record the current runtime validation matrix in `docs/metallum-port.md`; do not duplicate that matrix here.
- [ ] Re-run Vulkan regression coverage for behavior touched by backend-neutralization.
- [ ] Keep incomplete or unvalidated Metal capabilities out of user-facing “fully supported” paths.
- [ ] Keep the Vitrail and companion Metallum PRs in draft/unmerged state until their stated validation requirements are satisfied.

Relevant repository evidence:

- `docs/metallum-port.md`
- Draft PR #1

## P1 — Continue backend-neutralization after storage images

Status: queued

Goal:
Address geometry-stage support and the remaining synchronization/startup boundaries using the same narrow-capability model.

Constraints:

- Preserve Vitrail shader-pack semantics and the Vulkan baseline.
- Verify each version-sensitive API against the exact versions in use before implementation.
- Do not add a broad backend abstraction merely to make the port look uniform.
