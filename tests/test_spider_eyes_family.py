"""Lock PHASE 7's spider-eyes/emissive-eye routing and full-bright contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/spider-eyes-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'


class SpiderEyesFamilyTest(unittest.TestCase):
    def test_fixture_is_eye_only_and_observes_fullbright_on_an_isolated_target(self):
        vertex = (FIXTURE / 'gbuffers_spidereyes.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_spidereyes.fsh').read_text(encoding='utf-8')
        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('gl_MultiTexCoord1.st', vertex)
        self.assertIn('step(239.0, light.x)', vertex)
        self.assertIn('step(light.x, 241.0)', vertex)
        self.assertIn('step(239.0, light.y)', vertex)
        self.assertIn('step(light.y, 241.0)', vertex)
        self.assertIn('uniform sampler2D gtexture;', fragment)
        self.assertIn('texture2D(gtexture, texcoord)', fragment)
        self.assertIn('const bool colortex1Clear = true;', fragment)
        self.assertIn('const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);', fragment)
        self.assertIn('/* DRAWBUFFERS:1 */', fragment)
        self.assertIn('vec4(0.0, 1.0, 0.0, texel.a)', fragment)
        self.assertIn('vec4(1.0, 0.0, 1.0, texel.a)', fragment)
        self.assertIn('uniform sampler2D colortex1;', final)
        self.assertIn('texture2D(colortex1, texcoord)', final)

        names = {path.name for path in FIXTURE.iterdir()}
        self.assertIn('gbuffers_spidereyes.vsh', names)
        self.assertIn('gbuffers_spidereyes.fsh', names)
        self.assertFalse(any('gbuffers_entities' in name for name in names))
        self.assertFalse(any('gbuffers_block' in name for name in names))
        self.assertFalse(any('glint' in name for name in names))
        self.assertFalse(any('hand' in name for name in names))

    def test_fixed_eye_rows_always_select_spidereyes_and_fullbright(self):
        draw = DRAW.read_text(encoding='utf-8')

        self.assertIn('private static final String SPIDER_EYES = "gbuffers_spidereyes";', draw)
        self.assertIn(
            'FIXED.put(RenderPipelines.EYES, new Element(RenderPipelines.EYES, "eyes", SPIDER_EYES,',
            draw,
        )
        self.assertIn('AlphaTest.NON_ZERO, RenderStage.NONE, false, true));', draw)
        self.assertIn(
            'FIXED.put(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE,',
            draw,
        )
        self.assertIn(
            'new Element(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, "eyes_emissive", SPIDER_EYES,',
            draw,
        )
        self.assertIn('CUTOUT, RenderStage.NONE, false, true));', draw)
        self.assertIn(
            'return this.fullbright ? VertexInputs.ENTITY_FULLBRIGHT : VertexInputs.ENTITY;',
            draw,
        )

    def test_fixed_eye_rows_precede_hand_and_block_entity_routing(self):
        draw = DRAW.read_text(encoding='utf-8')
        fixed = draw.index('Element fixed = FIXED.get(pipeline);')
        fixed_return = draw.index('if (fixed != null)', fixed)
        hand = draw.index('if (HandDraw.drawing())', fixed_return)
        block = draw.index('return BlockEntityGeometry.drawing()', hand)
        self.assertLess(fixed, fixed_return)
        self.assertLess(fixed_return, hand)
        self.assertLess(hand, block)


if __name__ == '__main__':
    unittest.main()
