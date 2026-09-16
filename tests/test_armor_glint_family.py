"""Lock PHASE 7's camera armor-glint routing and POSITION_TEX vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/armor-glint-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'
GLINT = ROOT / 'common/src/main/java/dev/vitrail/glsl/GlintVertex.java'
VERTEX_INPUTS = ROOT / 'common/src/main/java/dev/vitrail/glsl/VertexInputs.java'


class ArmorGlintFamilyTest(unittest.TestCase):
    def test_fixture_is_glint_only_and_observes_synthesized_inputs(self):
        vertex = (FIXTURE / 'gbuffers_armor_glint.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_armor_glint.fsh').read_text(encoding='utf-8')
        final_vertex = (FIXTURE / 'final.vsh').read_text(encoding='utf-8')
        final_fragment = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

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

        names = {path.name for path in FIXTURE.iterdir()}
        self.assertEqual(
            {'gbuffers_armor_glint.vsh', 'gbuffers_armor_glint.fsh', 'final.vsh', 'final.fsh'},
            names,
        )
        self.assertFalse(any('entities' in name for name in names))
        self.assertFalse(any('block' in name for name in names))
        self.assertFalse(any('spidereyes' in name for name in names))
        self.assertFalse(any('hand' in name for name in names))

    def test_camera_glint_has_two_schedule_pieces_and_hand_wins_first(self):
        draw = DRAW.read_text(encoding='utf-8')

        self.assertIn('private static final String ARMOR_GLINT = "gbuffers_armor_glint";', draw)
        self.assertIn('private static final Element GLINT_EARLY = glint("glint", RenderStage.NONE, false);', draw)
        self.assertIn('private static final Element GLINT_LATE = glint("glint_late", RenderStage.NONE, true);', draw)
        self.assertIn('return new Element(RenderPipelines.GLINT, element, ARMOR_GLINT, AlphaTest.NON_ZERO,', draw)
        self.assertIn('return (hand() || glint()) ? this.afterDeferred : blended();', draw)

        hand = draw.index('if (HandDraw.drawing())')
        camera = draw.index('if (pipeline == RenderPipelines.GLINT) {', hand + 1)
        block = draw.index('return BlockEntityGeometry.drawing()', camera)
        self.assertIn('return translucentFeatures ? GLINT_LATE : GLINT_EARLY;', draw[camera:block])
        self.assertLess(hand, camera)
        self.assertLess(camera, block)

    def test_glint_decoder_is_position_tex_with_iris_constants(self):
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
