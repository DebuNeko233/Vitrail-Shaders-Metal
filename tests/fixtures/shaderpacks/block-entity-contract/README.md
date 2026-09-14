# PHASE 7 block-entity contract

This developer-only pack isolates block entities from ordinary entities and every later PHASE 7 family.

- terrain is solid red;
- only `gbuffers_block` can produce green/magenta geometry;
- the block vertex stage reads the carried `mc_midTexCoord` and `at_tangent` polygon ABI;
- green means those carried fields are plausible, magenta means the entity-format ABI failed;
- `final` only exposes `colortex0`.

There is deliberately no `gbuffers_entities`, `gbuffers_spidereyes`, glint or hand program here. Use a visible chest or another ordinary model-backed block entity as the real-device subject. The block-state identifier lane is locked statically by `test_block_entity_family.py`; this fixture does not claim that current entity translation exposes that lane as `mc_Entity`.
