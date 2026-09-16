# Entity family contract

Developer-only PHASE 7 fixture for the first family, ordinary `entities`, only.

Terrain is forced red. `gbuffers_entities` reads the two polygon attributes that the extended entity vertex ABI must carry (`mc_midTexCoord` and `at_tangent`) and paints visible entity texels green only when both look valid; an ABI/stride/attribute failure paints magenta. The entity texture alpha is still respected so transparent texels do not become a solid rectangle. `final` only exposes `colortex0`.

For the real-device check, frame one ordinary living entity close enough to occupy a visible region of the screenshot. Do not use a block entity, spider-eye/emissive layer, armor glint, hand, particle, weather, cloud, or sky element as evidence for this checkpoint; those are later PHASE 7 families.

The runtime shader can directly observe `MidTexCoord` and `Tangent`. CI separately locks the third appended field, `EntityIds`, and the Sodium 36-byte -> Vitrail 56-byte serializer that writes all three appended fields.
