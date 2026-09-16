"""Lock PHASE 7's first family: ordinary entities and their extended vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/entity-contract/shaders'
ENTITY_VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/EntityVertex.java'
ENTITY_MESH = ROOT / 'common/src/main/java/dev/vitrail/render/EntityMesh.java'
ENTITY_DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'
SERIALIZER = ROOT / 'common/src/main/java/dev/vitrail/sodium/EntityMeshSerializer.java'


class EntityFamilyTest(unittest.TestCase):
    def test_fixture_observes_entity_polygon_abi_only(self):
        vertex = (FIXTURE / 'gbuffers_entities.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_entities.fsh').read_text(encoding='utf-8')
        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('attribute vec2 mc_midTexCoord;', vertex)
        self.assertIn('attribute vec4 at_tangent;', vertex)
        self.assertIn('distance(mc_midTexCoord, texcoord)', vertex)
        self.assertIn('length(at_tangent.xyz)', vertex)
        self.assertIn('abs(at_tangent.w)', vertex)
        self.assertIn('uniform sampler2D gtexture;', fragment)
        self.assertIn('texture2D(gtexture, texcoord)', fragment)
        self.assertIn('discard;', fragment)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', fragment)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', fragment)
        self.assertIn('uniform sampler2D colortex0;', final)

        names = {path.name for path in FIXTURE.iterdir()}
        self.assertFalse(any('block' in name for name in names))
        self.assertFalse(any('spidereyes' in name for name in names))
        self.assertFalse(any('hand' in name for name in names))

    def test_entity_format_keeps_all_nine_attributes_in_order(self):
        vertex = ENTITY_VERTEX.read_text(encoding='utf-8')
        mesh = ENTITY_MESH.read_text(encoding='utf-8')

        self.assertIn(
            'public static final List<String> APPENDED = List.of(IDENTIFIERS, MID_TEX_COORD, TANGENT);',
            vertex,
        )
        for ordered in (
            'Stream.of("Position", "Color", "UV0", "UV1", "UV2", "Normal"), APPENDED.stream()',
            'builder.addAttribute(EntityVertex.IDENTIFIERS, GpuFormat.RGBA16_UINT)',
            '.addAttribute(EntityVertex.MID_TEX_COORD, GpuFormat.RG32_FLOAT)',
            '.addAttribute(EntityVertex.TANGENT, GpuFormat.RGBA8_SNORM)',
        ):
            self.assertIn(ordered, vertex if ordered.startswith('Stream.of') else mesh)
        self.assertIn('boolean entity = declared == DefaultVertexFormat.ENTITY;', mesh)
        self.assertIn('return carrying && entity ? FORMAT : declared;', mesh)

    def test_sodium_serializer_writes_every_appended_field(self):
        source = SERIALIZER.read_text(encoding='utf-8')

        self.assertIn('private static final int WRITTEN = DefaultVertexFormat.ENTITY.getVertexSize();', source)
        self.assertIn('private static final int CARRIED = EntityMesh.format().getVertexSize();', source)
        self.assertIn(
            'registerSerializer(DefaultVertexFormat.ENTITY,\n\t\t\t\tEntityMesh.format(), new EntityMeshSerializer())',
            source,
        )
        for needle in (
            'MemoryUtil.memPutShort(into + IDENTIFIERS, entity);',
            'MemoryUtil.memPutShort(into + IDENTIFIERS + 2L, blockEntity);',
            'MemoryUtil.memPutShort(into + IDENTIFIERS + 4L, item);',
            'MemoryUtil.memPutShort(into + IDENTIFIERS + 6L, (short) 0);',
            'MemoryUtil.memPutFloat(into + MID_TEX_COORD, midU);',
            'MemoryUtil.memPutFloat(into + MID_TEX_COORD + 4L, midV);',
            'MemoryUtil.memPutInt(into + TANGENT, tangent);',
        ):
            self.assertIn(needle, source)

    def test_ordinary_entity_rows_are_separate_from_later_phase7_families(self):
        draw = ENTITY_DRAW.read_text(encoding='utf-8')

        for needle in (
            'put(new Element(RenderPipelines.ENTITY_SOLID, "solid", ENTITIES, AlphaTest.OFF));',
            'put(new Element(RenderPipelines.ENTITY_CUTOUT, "cutout", ENTITIES, CUTOUT));',
            'put(new Element(RenderPipelines.ENTITY_TRANSLUCENT, "translucent", ENTITIES_TRANSLUCENT,',
        ):
            self.assertIn(needle, draw)
        self.assertIn('FIXED.put(RenderPipelines.EYES, new Element(RenderPipelines.EYES, "eyes", SPIDER_EYES,', draw)
        self.assertIn('boolean covers() {\n\t\t\treturn !shadow() && !afterStage();', draw)


if __name__ == '__main__':
    unittest.main()
