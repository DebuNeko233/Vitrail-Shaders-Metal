# PHASE 7 spider-eyes contract

This developer fixture isolates the camera's glowing-eye family from ordinary entities, block entities, glint and hand rendering.

`gbuffers_spidereyes` is the only entity-family program present. It writes draw buffer 1, whose `colortex1` target is explicitly cleared to black, so the ordinary entity body and terrain cannot fake the acceptance colour. The fragment keeps the sampled eye-texture alpha so Vitrail's per-row alpha test still shapes the eye layer.

The vertex stage checks the full-bright light coordinate expected from the fixed eye rows. A passing eye writes green; a failure writes magenta. `final` displays only `colortex1`, so no `gbuffers_spidereyes` draw leaves the screenshot black.

This closes only the PHASE 7 spider-eyes/emissive-eyes routing checkpoint. It does not close armor glint, hand, shadow entities, particles, weather, clouds or sky.
