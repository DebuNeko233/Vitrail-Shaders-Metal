# PHASE 7 armor-glint contract

This developer fixture isolates the camera's armor/item glint family from ordinary entities, block entities, spider eyes and the hand.

`gbuffers_armor_glint` is the only geometry-family program present. It writes draw buffer 1, whose `colortex1` target is explicitly cleared to black, and `final` displays only that target. A normal entity or armor body therefore cannot fake the acceptance colour.

The vertex stage is written against the glint contract rather than the carried entity mesh: it uses `gl_TextureMatrix[0] * gl_MultiTexCoord0`, checks Iris-compatible full-bright `gl_MultiTexCoord1` and the synthesized viewer-facing `gl_Normal`. The fragment still samples the real glint texture through the transformed UV, but the animated stripe texture no longer modulates the acceptance colour. A passing glint writes stable opaque green over the glint geometry; a synthesized-input or sampled-texture-range failure writes magenta; no served glint leaves the screenshot black.

Real-device acceptance separately requires the log to prove the camera's `glint_late` piece reached `gbuffers_armor_glint` and that its first draw read `minecraft:textures/misc/enchanted_glint_armor.png`. This keeps texture-resource evidence independent from the stable pixel gate. A hand or held-item glint is deliberately excluded and remains part of the later hand checkpoint.
