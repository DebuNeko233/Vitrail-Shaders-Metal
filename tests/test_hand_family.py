"""Lock PHASE 7 first-person hand and hand_water routing plus carried polygon ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOLID = ROOT / 'tests/fixtures/shaderpacks/hand-contract/shaders'
WATER = ROOT / 'tests/fixtures/shaderpacks/hand-water-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'
HAND = ROOT / 'common/src/main/java/dev/vitrail/render/HandDraw.java'


class HandFamilyTest(unittest.TestCase):
    def test_fixtures_are_isolated_and_keep_real_texture_plus_polygon_abi_live(self):
        for fixture, program in ((SOLID, 'gbuffers_hand'), (WATER, 'gbuffers_hand_water')):
            vertex = (fixture / f'{program}.vsh').read_text(encoding='utf-8')
            fragment = (fixture / f'{program}.fsh').read_text(encoding='utf-8')
            final_vertex = (fixture / 'final.vsh').read_text(encoding='utf-8')
            final_fragment = (fixture / 'final.fsh').read_text(encoding='utf-8')

            self.assertIn('attribute vec2 mc_midTexCoord;', vertex)
            self.assertIn('attribute vec4 at_tangent;', vertex)
            self.assertIn('distance(mc_midTexCoord, texcoord)', vertex)
            self.assertIn('length(at_tangent.xyz)', vertex)
            self.assertIn('uniform sampler2D gtexture;', fragment)
            self.assertIn('texture2D(gtexture, texcoord)', fragment)
            self.assertIn('const bool colortex1Clear = true;', fragment)
            self.assertIn('/* DRAWBUFFERS:1 */', fragment)
            self.assertIn('? vec4(0.0, 1.0, 0.0, 1.0)', fragment)
            self.assertIn(': vec4(1.0, 0.0, 1.0, 1.0)', fragment)
            self.assertIn('gl_Position = ftransform();', final_vertex)
            self.assertIn('uniform sampler2D colortex1;', final_fragment)

            names = {path.name for path in fixture.iterdir()}
            self.assertEqual({f'{program}.vsh', f'{program}.fsh', 'final.vsh', 'final.fsh'}, names)

    def test_hand_programs_are_selected_by_current_pass_not_pipeline_blending(self):
        draw = DRAW.read_text(encoding='utf-8')
        hand = HAND.read_text(encoding='utf-8')

        self.assertIn('private static final String HAND = "gbuffers_hand";', draw)
        self.assertIn('private static final String HAND_WATER = "gbuffers_hand_water";', draw)
        self.assertIn('twins(HAND_ELEMENTS, "hand_", HAND, RenderStage.HAND_SOLID, false);', draw)
        self.assertIn('twins(HAND_WATER_ELEMENTS, "hand_water_", HAND_WATER, RenderStage.HAND_TRANSLUCENT, true);', draw)
        self.assertIn('return (HandDraw.drawingSolid() ? HAND_ELEMENTS : HAND_WATER_ELEMENTS).get(pipeline);', draw)
        self.assertIn('return (hand() || glint()) ? this.afterDeferred : blended();', draw)

        self.assertIn('return which != null && translucent(held) != (which == Half.TRANSLUCENT);', hand)
        self.assertIn('held.getItem() instanceof BlockItem block', hand)
        self.assertIn('private enum Half', hand)
        self.assertIn('SOLID,', hand)
        self.assertIn('TRANSLUCENT', hand)

    def test_hand_glint_remains_separate_but_follows_the_same_two_pass_schedule(self):
        draw = DRAW.read_text(encoding='utf-8')
        self.assertIn('private static final Element GLINT_HAND = glint("hand_glint", RenderStage.HAND_SOLID, false);', draw)
        self.assertIn('glint("hand_water_glint", RenderStage.HAND_TRANSLUCENT, true);', draw)
        self.assertIn('return HandDraw.drawingSolid() ? GLINT_HAND : GLINT_HAND_WATER;', draw)


if __name__ == '__main__':
    unittest.main()
