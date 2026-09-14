# PHASE 7 hand_water_glint contract

This developer fixture isolates the first-person translucent-hand enchantment glint moment. `gbuffers_armor_glint` is deliberately the only geometry-family program present: Vitrail follows Iris in keeping glint on that program name, while the routed piece/stage distinguishes `hand_water_glint @ HAND_TRANSLUCENT` and schedules it after deferred.

`hand_water` here has the same Iris-compatible meaning as the ordinary hand-water checkpoint: the held item is a translucent block model; the player does not need to stand underwater. The shader keeps the real glint `gtexture`, transformed UV path and synthesized GLINT inputs live, writes isolated `colortex1`, and produces stable green for a valid POSITION_TEX ABI or magenta for failure. Black means this hand-glint moment did not draw.

Real-device acceptance must use first person with a glinting translucent held block model, log `hand_water_glint` through `gbuffers_armor_glint` at `HAND_TRANSLUCENT`, prove a real Minecraft enchanted-glint texture on its first draw, pass the green/magenta screenshot gate, and shut down cleanly. Neither camera armor glint nor the solid `hand_glint` checkpoint closes this one.
