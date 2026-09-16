# PHASE 16 PBR normal/specular acceptance

This checkpoint uses one shader pack plus one Minecraft resource pack so the PBR verdict is tied to the texture actually bound by a terrain draw.

Enable `phase16-pbr-resources` as the active resource pack and `phase16-pbr-contract` as the shader pack. Place or face a vanilla stone block so a large patch of its face is visible.

The resource pack replaces `minecraft:block/stone` with a constant albedo marker and supplies constant `stone_n.png` and `stone_s.png` companion maps under the same sprite identity. The terrain fragment shader first requires the albedo marker, then reads `normals` and `specular`. Marker stone is GREEN only when both companion samplers return their distinct expected values; marker stone becomes MAGENTA when either PBR map is missing or incorrectly bound. Every non-marker terrain pixel is dark, so a screenshot with no visible marker stone cannot pass the verifier.

Expected hardware result: a visible stone face is GREEN with no meaningful MAGENTA. The screenshot verifier requires a minimum GREEN population and rejects MAGENTA independently.

The fixture deliberately tests the static companion-map production path. Animated PBR companions remain a documented PHASE 16 compatibility limitation and are not claimed by this checkpoint.
