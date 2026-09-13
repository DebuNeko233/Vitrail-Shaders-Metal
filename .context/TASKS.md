# Active Tasks

Updated: 2026-09-14
Scope: `feat/backend-neutral-sodium-terrain-hook`

## P0 - Validate routed backend compute

Implementation and local compilation are present; runtime acceptance is open.

- [x] Route shadow, chained and standalone compute when both device and command capabilities exist.
- [x] Preserve Vulkan descriptors/barriers and keep pack resource/scheduling policy in Vitrail.
- [x] Close backend compute state through its owning backend.
- [x] Make missing/incompatible optional compute bridge lookup catchable without class-initialization poisoning.
- [x] Verify bridge failure regressions and full local Gradle build.
- [x] Verify companion Metallum bridge signatures and successful build at `81295f0`.
- [ ] Verify Vitrail remote CI on the published head before treating both repositories as compile-validated together.
- [ ] Exercise compute writes to storage images/buffers and subsequent render/compute reads on Apple Silicon.
- [ ] Execute the runtime matrix and Vulkan regression coverage in `docs/metallum-port.md`.

Do not mark Metal fully supported or relax startup guards from a compile-only result. Keep draft migration work unmerged until its runtime acceptance criteria are met.

## P1 - Continue backend-neutralization

Geometry-stage support and remaining synchronization/startup boundaries follow the validated compute slice. Verify exact active API versions before changing integrations; preserve the narrow capability model and native fallback.
