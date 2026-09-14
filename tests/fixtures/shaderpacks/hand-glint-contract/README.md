# PHASE 7 hand_glint contract

This developer fixture isolates the first-person solid-hand enchantment glint moment. `gbuffers_armor_glint` is deliberately the only geometry-family program present: Vitrail follows Iris in using the glint program name for camera glint and hand glint alike, while the routed piece/stage distinguishes `hand_glint @ HAND_SOLID` from the camera pieces.

The shader keeps the real `gtexture`, transformed `gl_TextureMatrix[0] * gl_MultiTexCoord0`, synthesized full-bright light coordinates and synthesized normal live. It writes isolated `colortex1`, cleared black, and `final` displays only that target. Valid GLINT/POSITION_TEX ABI writes stable opaque green, failure writes magenta, and no served hand glint stays black. The animated stripe texture is sampled but does not modulate the acceptance colour; the launcher verifies the real glint texture independently from the log.

Real-device acceptance must use first person with a glinting opaque/non-translucent held item, log `hand_glint` through `gbuffers_armor_glint` at `HAND_SOLID`, prove a real Minecraft enchanted-glint texture on its first draw, pass the green/magenta screenshot gate, and shut down cleanly. Camera armor glint does not close this checkpoint.
