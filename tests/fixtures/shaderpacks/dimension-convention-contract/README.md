# PHASE 13 Dimension Routing — conventional folders

Developer-only smoke fixture for the conventional OptiFine/Iris dimension folders when no `dimension.properties` exists.

Expected Final output:

- root: MAGENTA (routing failure signature)
- `world0` / `minecraft:overworld`: GREEN
- `world-1` / `minecraft:the_nether`: RED
- `world1` / `minecraft:the_end`: BLUE

A dimension directory replaces the root; it is not layered over it. This fixture intentionally contains only `final` programs so each screenshot identifies the selected directory directly.
