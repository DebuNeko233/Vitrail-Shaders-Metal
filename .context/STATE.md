# Project State

Updated: 2026-09-14
Scope: `feat/backend-neutral-sodium-terrain-hook`

## Confirmed from the current checkout

- The migration preserves Vitrail shader-pack policy and the existing Vulkan path, with narrow optional Metallum capabilities for terrain binding, blending, mipmaps, pipeline eviction, storage resources, writable colour targets and compute.
- `PackCompute` routes shadow, chained and standalone computes through `BackendComputePass` when both `ComputeDeviceBackend` and `ComputeCommands` are present. Routing landed at `5e84c045332c3687dd0c7be94c894353bdfebcca`; Vulkan retains its direct descriptor/barrier implementation.
- Backend compute owns the uniform ring and opaque pipeline lifetime; Vitrail retains resource-name resolution, target-half selection and dispatch scheduling. Metallum owns native compilation, binding and synchronization.
- Optional compute bridge method lookup now happens inside a normal call and caches only the complete method set. Missing classes/signatures remain catchable and do not poison adapter initialization. Backend runtime exceptions and fatal errors propagate unchanged.
- The isolated adapter regression suite passes all three cases; running it against the original adapter reproduces both unavailable/incompatible bridge failures. Its facade stubs do not validate Minecraft or Metal ABI.
- Local JDK 25 full `./gradlew build` passed on 2026-09-14. This is local compile validation, not CI or real-device verification.

## Open validation boundaries

- No Apple-Silicon in-game compute, MRT, lifetime or Vulkan regression run was performed in this task. Preserve conservative startup/backend guards.
- Metal depth/stencil mipmaps, geometry-stage handling and remaining synchronization/startup seams need further work; do not infer support from compilation.
- `docs/metallum-port.md` owns the runtime validation matrix and historical CI evidence. Remote PR/check status was not reverified in this task.
- Companion repository: `DebuNeko233/metallum`, branch `feat/mc26.2-mrt-foundation`. Recheck its current code and checks before relying on native bridge behavior.

## Recovery entry points

- `.context/TASKS.md` and `.context/architecture/metallum-port.md`
- `docs/metallum-port.md`, `CONTRIBUTING.md`, `gradle.properties`
- `render/PackCompute.java`, `render/BackendComputePass.java`, `render/PackComputeBindings.java`
- `mixin/metallum/MetallumComputeBridge.java`
- `tests/test_metallum_compute_bridge.py`

Java paths above are relative to `common/src/main/java/dev/vitrail/`.
