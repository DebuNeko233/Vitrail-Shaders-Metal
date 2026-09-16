# MRT contract smoke shader pack

This directory is a developer-only shader-pack fixture. It is not packaged into Vitrail and it is not a compatibility sample for end users.

It exercises two contracts on the Vitrail + Metallum Apple-Silicon smoke path:

- `composite.fsh` writes four color targets in one pass: colortex0 red, colortex1 green, colortex2 blue, and colortex3 white. `final.fsh` samples those four targets into four screen quadrants. A successful MRT smoke therefore shows all four colors at once.
- `gbuffers_terrain.fsh` deliberately declares `DRAWBUFFERS:0` while writing three fragment-output ranks. On an opaque terrain pass Vitrail owns the first pack target and appends its coverage attachment after the fragment outputs, so the render-pass shape is `[PACK, UNUSED, UNUSED, COVERAGE]`. The two `UNUSED` entries become null texture views. Metallum must preserve their indices rather than compacting the attachment list.

The second case is intentionally not `DRAWBUFFERS:02`: Vitrail interprets draw-buffer values as target identities in fragment-output order, so `02` means output rank 0 -> colortex0 and rank 1 -> colortex2. It does not mean native attachment slots 0 and 2.

Run it only through the experimental Metal smoke path. From the Metallum checkout, launch with `./tools/run-vitrail-smoke.sh ../Vitrail-Shaders-Metal`, put this fixture under that dev profile's `shaderpacks` directory (a directory copy is sufficient), select it in Vitrail, select **Prefer Metal**, and restart the same smoke command.

Evidence to record:

1. The game reports the Metal backend and Vitrail reports the compatible Metallum preference/capability path after device creation.
2. The final image contains red, green, blue, and white quadrants. Their vertical orientation is not significant.
3. Metal validation reports no render-pass or pipeline attachment-index error while opaque terrain is visible.
4. Switching away from Metal, disabling the pack, and the normal Vulkan path remain usable.

A compile-green run only proves that the fixture and contract harness still have the intended shape. It is not a substitute for the real Apple-Silicon smoke run.
