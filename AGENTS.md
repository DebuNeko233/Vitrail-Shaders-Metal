# Agent Instructions

This repository uses `.context/` as durable agent memory. Repository memory is a recovery aid, not the source of truth; repository evidence wins when the two disagree.

## Start

Read, in order:

1. `.context/STATE.md`
2. `.context/TASKS.md`
3. `.context/architecture/metallum-port.md` when working on the Metal/Metallum migration
4. `.context/architecture/roadmap.md` and its condensed English form `docs/roadmap.md` when the work is about migration scope, phase ordering, or whether a change belongs to Vitrail or to the backend
5. `docs/performance.md` when the work is about performance, Metal 4, MetalFX, or removing the Vulkan path

Then load only the repository evidence relevant to the task. `docs/README.md` routes the long-form project documentation, and `CONTRIBUTING.md` defines the repository workflow and build/commit rules.

## Repository rules

- Do not depend on previous conversation history. Discover repository structure instead of assuming paths.
- Treat volatile memory as something to verify. Prefer current implementation, configuration, checks, documentation, and version history over stale memory.
- For Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross, Metal, or any other version-sensitive external API, read the exact active versions from repository evidence and verify the matching documentation or source before using or changing the API. Do not guess from memory.
- Keep the Vitrail/Metallum boundary strict: Vitrail owns shader-pack policy, semantics, and scheduling; a backend owns native GPU execution. Prefer Minecraft's public GPU types across the seam. Add narrow semantic capabilities only where the public API cannot express the required operation; do not expose native Metal handles through backend-neutral code.
- **Vulkan is no longer a preservation target.** Metal is the only path that is maintained, and Vulkan-specific behaviour, code, CI contracts and documentation may be changed or removed as the work requires; nothing has to keep working beside it. Read that carefully, because it is narrower than it sounds in both directions: the Vulkan path is *still in the tree*, and its removal is scheduled work recorded in `docs/performance.md` rather than something already done, so a change that happens to delete some of it owes the same explanation any other change does. And what the rule does not lift: unsupported or unvalidated behaviour must still be explicit, a plausible image is still not proof of correct shader-pack semantics, and shader-pack semantics are still Vitrail's alone.
  - The consequence to know before relying on it: the game's own renderer is not something Vitrail controls, so a build with no Vulkan path has no working path on any platform whose renderer is not Metal. That narrowing was accepted deliberately rather than discovered later, and it is the reason `HostReport.otherBackend` and the validation-only stance on Metal are expected to change: `HostReport` still calls Vulkan the production path and accepts Metal only behind the developer smoke switch, which is the state this rule is meant to end.
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

## Finishing a turn

End every turn that changed state with these three things, in this order, so the owner never has to work out what to do next or what this session needs back.

1. **What happened**, in a line or two, with the evidence that says it. The reasoning stays above this; this is the part that has to be readable on its own.
2. **What the owner does next, in order.** Numbered steps that can be followed without re-reading the turn, and name the button where a click is the action.
3. **What to hand back**, named exactly: which log, from which instance, with which marker or setting in force, and what was done by hand during the session. A run handed over without the state it was taken in cannot be read, and rebuilding a session to recover that state costs more than asking for it.

A turn that only answered a question owes the same three, with the steps being whatever the answer implies.
