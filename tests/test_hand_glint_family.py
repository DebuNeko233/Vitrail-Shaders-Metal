"""Lock PHASE 7 first-person hand_glint and hand_water_glint routing plus GLINT ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOLID = ROOT / 'tests/fixtures/shaderpacks/hand-glint-contract/shaders'
WATER = ROOT / 'tests/fixtures/shaderpacks/hand-water-glint-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'
GLINT = ROOT / 'common/src/main/java/dev/vitrail/glsl/GlintVertex.java'
VERTEX_INPUTS = ROOT / 'common/src/main/java/dev/vitrail/glsl/VertexInputs.java'


class HandGlintFamilyTest(unittest.TestCase):
    def test_fixtures_are_isolated_and_keep_real_glint_texture_plus_position_tex_abi_live(self):
        for fixture in (SOLID, WATER):
            vertex = (fixture / 'gbuffers_armor_glint.vsh').read_text(encoding='utf-8')
            fragment = (fixture / 'gbuffers_armor_glint.fsh').read_text(encoding='utf-8')
            final_vertex = (fixture / 'final.vsh').read_text(encoding='utf-8')
            final_fragment = (fixture / 'final.fsh').read_text(encoding='utf-8')

            self.assertIn('gl_TextureMatrix[0] * gl_MultiTexCoord0', vertex)
            self.assertIn('gl_MultiTexCoord1.st', vertex)
            self.assertIn('gl_Normal - vec3(0.0, 0.0, 1.0)', vertex)
            self.assertIn('uniform sampler2D gtexture;', fragment)
            self.assertIn('texture2D(gtexture, glintTexcoord)', fragment)
            self.assertIn('const bool colortex1Clear = true;', fragment)
            self.assertIn('const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 1.0);', fragment)
            self.assertIn('/* DRAWBUFFERS:1 */', fragment)
            self.assertIn('float sampledTextureOk = step(-0.01, minSample) * step(maxSample, 1.01);', fragment)
            self.assertIn('float contractOk = glintAbiOk * sampledTextureOk;', fragment)
            self.assertIn('? vec4(0.0, 1.0, 0.0, 1.0)', fragment)
            self.assertIn(': vec4(1.0, 0.0, 1.0, 1.0)', fragment)
            self.assertNotIn('step(0.02, textureSignal)', fragment)

            self.assertIn('gl_Position = ftransform();', final_vertex)
            self.assertIn('texcoord = gl_MultiTexCoord0.st;', final_vertex)
            self.assertNotIn('gl_FragColor', final_vertex)
            self.assertNotIn('DRAWBUFFERS', final_vertex)
            self.assertIn('uniform sampler2D colortex1;', final_fragment)
            self.assertIn('texture2D(colortex1, texcoord)', final_fragment)

            names = {path.name for path in fixture.iterdir()}
            self.assertEqual(
                {'gbuffers_armor_glint.vsh', 'gbuffers_armor_glint.fsh', 'final.vsh', 'final.fsh'},
                names,
            )
            self.assertFalse(any(name.startswith('gbuffers_hand') for name in names))

    def test_hand_glint_uses_armor_glint_program_but_has_two_independent_hand_moments(self):
        draw = DRAW.read_text(encoding='utf-8')

        self.assertIn('private static final String ARMOR_GLINT = "gbuffers_armor_glint";', draw)
        self.assertIn('private static final Element GLINT_HAND = glint("hand_glint", RenderStage.HAND_SOLID, false);', draw)
        self.assertIn('glint("hand_water_glint", RenderStage.HAND_TRANSLUCENT, true);', draw)
        self.assertIn('return new Element(RenderPipelines.GLINT, element, ARMOR_GLINT, AlphaTest.NON_ZERO,', draw)
        self.assertIn('return (hand() || glint()) ? this.afterDeferred : blended();', draw)

        hand = draw.index('if (HandDraw.drawing())')
        normal_hand = draw.index('return (HandDraw.drawingSolid() ? HAND_ELEMENTS : HAND_WATER_ELEMENTS).get(pipeline);', hand)
        camera = draw.index('if (pipeline == RenderPipelines.GLINT) {', normal_hand)
        hand_branch = draw[hand:normal_hand]
        self.assertIn('if (pipeline == RenderPipelines.GLINT) {', hand_branch)
        self.assertIn('return HandDraw.drawingSolid() ? GLINT_HAND : GLINT_HAND_WATER;', hand_branch)
        self.assertLess(hand, normal_hand)
        self.assertLess(normal_hand, camera)

    def test_hand_glint_keeps_position_tex_decoder_and_iris_synthesized_constants(self):
        draw = DRAW.read_text(encoding='utf-8')
        glint = GLINT.read_text(encoding='utf-8')
        vertex_inputs = VERTEX_INPUTS.read_text(encoding='utf-8')

        self.assertIn('return DefaultVertexFormat.POSITION_TEX;', draw)
        self.assertIn('return VertexInputs.GLINT;', draw)
        self.assertIn('public static final List<String> ATTRIBUTES = List.of("Position", "UV0");', glint)
        self.assertIn('private static final String FULL_BRIGHT = "vec4(240.0, 240.0, 0.0, 1.0)";', glint)
        self.assertIn('lines.add("#define of_MultiTexCoord1 " + FULL_BRIGHT);', glint)
        self.assertIn('lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");', glint)
        self.assertIn('LegacyGlsl.GLINT_ALPHA', glint)
        self.assertIn('\n\tGLINT,', vertex_inputs)


if __name__ == '__main__':
    unittest.main()
