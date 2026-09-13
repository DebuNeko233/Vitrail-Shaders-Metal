# Metallum / Metal backend port status

This page tracks the backend work needed to run Vitrail shader packs through Metallum on macOS. The detailed roadmap and repository rules live in [`AGENTS.md`](../AGENTS.md); this page records what has actually been implemented and what is still only planned.

## Version baseline

The active compatibility baseline is:

- Minecraft Java Edition 26.2
- Java 25
- Sodium 0.9.2 stable for Minecraft 26.2
- Vitrail uses the CaffeineMC Maven artifact `net.caffeinemc:sodium-fabric:0.9.2+mc26.2`
- Metallum uses the Modrinth release artifact form `mc26.2-0.9.2-fabric`

The two Sodium strings are intentionally different because the two repositories resolve Sodium from different Maven coordinates.

## Metallum foundation

A dedicated Metallum branch and draft pull request implement the Minecraft 26.2 multi-render-target contract:

- repository: `DebuNeko233/metallum`
- branch: `feat/mc26.2-mrt-foundation`
- draft PR: `DebuNeko233/metallum#1`

The implementation preserves the indexed nullable color-attachment model exposed by Minecraft 26.2. A render pass such as `[RT0, unused, RT2]` therefore keeps RT2 at attachment index 2 instead of compacting it to index 1.

The draft currently covers:

- up to eight indexed Metal color attachments;
- `RenderPassDescriptor.colorAttachments()` including unused/null slots;
- `RenderPipeline.getColorTargetStates()` including unused/null slots;
- per-target Metal pixel format, write mask, and blend state;
- per-target clear values;
- full attachment-set identity when deciding whether a Metal render encoder can be reused;
- common attachment extent validation;
- depth-only render-pass sizing;
- Sodium 0.9.2 stable alignment in Metallum.

This is deliberately backend-only work. `colortex*` naming, `DRAWBUFFERS`, ping-pong/history, depth-copy meaning, shader-pack program routing, and frame scheduling remain Vitrail responsibilities.

## Validation status

The Metallum changes are **not yet considered runtime-complete**.

The fork currently has no GitHub Actions run history, so opening the draft PR did not produce a build run. The feature branch adds branch/PR build triggers for future CI, but a successful Minecraft/Gradle build has not yet been observed through GitHub Actions. The local execution environment used during this work also cannot reach GitHub or Maven repositories, so it cannot download the dependencies needed to substitute for CI.

Before the Metallum PR is ready to merge, it still needs:

1. a successful `./gradlew build` against Minecraft 26.2 and Sodium 0.9.2;
2. a macOS/Apple-Silicon MRT smoke test that writes distinct values to at least four targets and reads them back or visualizes them;
3. confirmation that a pass with an unused middle attachment slot preserves fragment-output locations;
4. confirmation that ordinary single-target vanilla/Sodium rendering is unchanged.

## Next Vitrail work

After the Metallum MRT foundation is build- and runtime-verified, Vitrail should proceed with the backend bridge rather than importing Metal details into the shader-pack engine. The first bridge slice should expose backend identity/capabilities and make the existing Vulkan-only boundaries explicit while leaving shader-pack semantics in Vitrail.

Any new Minecraft, Sodium, or Metallum API used by that bridge must be checked against the exact Minecraft 26.2 / Sodium 0.9.2 source or published API before code is committed, as required by `AGENTS.md`.
