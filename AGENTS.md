# Agent Instructions

This repository uses `.context/` as durable agent memory. Repository memory is a recovery aid, not the source of truth; repository evidence wins when the two disagree.

## Start

Read, in order:

1. `.context/STATE.md`
2. `.context/TASKS.md`
3. `.context/architecture/metallum-port.md` when working on the Metal/Metallum path
4. `.context/architecture/roadmap.md` and its condensed English form `docs/roadmap.md` when the work is about migration scope, phase ordering, or whether a change belongs to Vitrail or to the backend
5. `docs/performance.md` when the work is about performance, Metal 4, or MetalFX

Then load only the repository evidence relevant to the task. `docs/README.md` routes the long-form project documentation, and `CONTRIBUTING.md` defines the repository workflow and build/commit rules.

## Repository rules

- Do not depend on previous conversation history. Discover repository structure instead of assuming paths.
- Treat volatile memory as something to verify. Prefer current implementation, configuration, checks, documentation, and version history over stale memory.
- For Minecraft, Sodium, Mixin, Metallum, SPIRV-Cross, Metal, or any other version-sensitive external API, read the exact active versions from repository evidence and verify the matching documentation or source before using or changing the API. Do not guess from memory.
- Keep the Vitrail/Metallum boundary strict: Vitrail owns shader-pack policy, semantics, and scheduling; a backend owns native GPU execution. Prefer Minecraft's public GPU types across the seam. Add narrow semantic capabilities only where the public API cannot express the required operation; do not expose native Metal handles through backend-neutral code.
- **Metal-only is the current architecture, not a migration target.** Vitrail supports exactly one target: macOS on Apple Silicon, the Metallum backend mod, and Apple Metal. Metallum is a required runtime dependency and owns Metal device creation, pipeline compilation, resource binding, argument buffers, render/blit/compute encoder lifecycle, synchronization, mipmaps, compute, storage resources and CAMetalLayer presentation; Vitrail owns shader-pack semantics. When Metallum is missing, API-incompatible, or its Metal device cannot be created, Vitrail fails clearly and early. It never falls back to another graphics API and it has no second path to keep alive.
- **The deleted graphics API is a hard rule for the whole tree.** No occurrence of its name, or of the vendor portability layer that used to present it on Apple hardware, may re-enter the repository — in code, comments, identifiers, class or file names, metadata, CI, tests, documentation or commit subjects, case-insensitively and including inside a larger identifier. The whole repository is gated by an automated contract test that refuses both strings, so a reintroduction fails the build rather than being caught in review. When external material has to be cited, describe the mechanism (an encoder, a layout-free texture, a driver limit, a presentation layer) rather than naming the deleted API.
- **Removing an implementation does not remove its meaning.** Behaviour that existed only to serve the deleted path is deleted; the *shader-pack semantics* it carried are not. Before deleting code that serves packs, say which pack-visible contract it was holding — pass order, MRT numbering, ping-pong parity, depth convention, sampler binding, a capability refusal — and where that contract now lives. Unsupported or unvalidated behaviour must still be explicit: a plausible image is not proof of correct shader-pack semantics, and shader-pack semantics are still Vitrail's alone.
- After code changes, check the repository documentation whose claims may have changed. `docs/metallum-port.md` is the current evidence for Metal-port implementation/validation status.
- Follow `CONTRIBUTING.md` rather than duplicating its branch, commit, changelog, and build rules here.

## Repository memory maintenance

- `.context/STATE.md` owns current facts.
- `.context/TASKS.md` owns active intent and acceptance criteria.
- `.context/architecture/` owns compressed mental models that are expensive to reconstruct.
- `.context/architecture/roadmap.md` is the one file there that is not compressed: it is the migration plan, and the source of record for the phase numbering everything else uses. It was imported whole and has since been rewritten around the Metal-only product surface, so it keeps its own typography rather than the house style. `docs/roadmap.md` is its condensed English form.
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
