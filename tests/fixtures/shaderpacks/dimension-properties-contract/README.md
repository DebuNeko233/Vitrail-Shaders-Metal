# PHASE 13 Dimension Routing — dimension.properties

Developer-only smoke fixture proving arbitrary folder names, namespace normalization, wildcard fallback, and an exact custom-dimension declaration.

`shaders/dimension.properties` maps:

- `surface` -> `minecraft:overworld` (written as `overworld`)
- `under` -> `minecraft:the_nether` (written as `the_nether`)
- `moon` -> `vitrail:moon`
- `catchall` -> `*`

Expected hardware Final output:

- Overworld / `surface`: CYAN
- Nether / `under`: YELLOW
- End / no exact declaration -> `catchall`: WHITE
- root: MAGENTA (failure signature)

The `moon` folder is ORANGE and is locked by the source contract as an exact namespaced custom-dimension route. No test-only dimension policy is added to the backend.
