# Agent Instructions

This repository uses `.context/` as durable agent memory. Repository memory is a recovery aid, not the source of truth; repository evidence wins when the two disagree.

## Start

Read, in order:

1. `.context/STATE.md`
2. `.context/TASKS.md`
3. `.context/architecture/metallum-port.md` when working on the Metal/Metallum migration
4. `.context/architecture/roadmap.md` and its condensed English form `docs/roadmap.md` when the work is about migration scope, phase ordering, or whether a change belongs to Vitrail or to the backend

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
- `.context/architecture/roadmap.md` is the one file there that is not compressed: it is the imported migration plan, kept verbatim because it is the source of record for the phase numbering everything else uses. `docs/roadmap.md` is its condensed English form.
- Add a decision record only for an important non-obvious choice whose rationale needs to survive.
- Create `.context/HANDOFF.md` only when unfinished execution state cannot be reconstructed safely from memory plus repository evidence; delete it when that condition ends.
- Do not copy README pages, configuration, obvious directory structure, commit history, or routine debugging output into `.context/`.

Before finishing substantive work, checkpoint only information that crossed a durability threshold, then audit memory for staleness, duplication, bloat, and unsupported claims.
