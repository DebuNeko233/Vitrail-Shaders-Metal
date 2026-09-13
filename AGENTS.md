# Agent Instructions

This repository uses `.context/` as durable agent memory. Repository memory is a recovery aid, not the source of truth; repository evidence wins when the two disagree.

## Start

Read, in order:

1. `.context/STATE.md`
2. `.context/TASKS.md`
3. `.context/architecture/metallum-port.md` when working on the Metal/Metallum migration

Then load only the repository evidence relevant to the task. `docs/README.md` routes the long-form project documentation, and `CONTRIBUTING.md` defines the repository workflow and build/commit rules.

## Repository rules

- Do not depend on previous conversation history. Discover repository structure instead of assuming paths.
- Treat volatile memory as something to verify. Prefer current implementation, configuration, checks, documentation, and version history over stale memory.
- For Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross, Metal, or any other version-sensitive external API, read the exact active versions from repository evidence and verify the matching documentation or source before using or changing the API. Do not guess from memory.
- Keep the Vitrail/Metallum boundary strict: Vitrail owns shader-pack policy, semantics, and scheduling; a backend owns native GPU execution. Prefer Minecraft's public GPU types across the seam. Add narrow semantic capabilities only where the public API cannot express the required operation; do not expose native Metal/Vulkan handles through backend-neutral code.
- During the Metal port, preserve the Vulkan baseline unless a behavior change is intentional and separately justified. Unsupported or unvalidated behavior must be explicit and should use a safe fallback where one exists. A plausible image is not proof of correct shader-pack semantics.
- After code changes, check the repository documentation whose claims may have changed. `docs/metallum-port.md` is the current evidence for Metal-port implementation/validation status.
- Follow `CONTRIBUTING.md` rather than duplicating its branch, commit, changelog, and build rules here.

## Repository memory maintenance

- `.context/STATE.md` owns current facts.
- `.context/TASKS.md` owns active intent and acceptance criteria.
- `.context/architecture/` owns compressed mental models that are expensive to reconstruct.
- Add a decision record only for an important non-obvious choice whose rationale needs to survive.
- Create `.context/HANDOFF.md` only when unfinished execution state cannot be reconstructed safely from memory plus repository evidence; delete it when that condition ends.
- Do not copy README pages, configuration, obvious directory structure, commit history, or routine debugging output into `.context/`.

Before finishing substantive work, checkpoint only information that crossed a durability threshold, then audit memory for staleness, duplication, bloat, and unsupported claims.
