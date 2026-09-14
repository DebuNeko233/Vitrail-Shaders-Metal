"""Lock PHASE 7 sky routing, four Minecraft sky formats and pre-deferred coverage semantics."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/sky-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/SkyDraw.java'
PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/SkyProgram.java'
VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/SkyVertex.java'


class SkyFamilyTest(unittest.TestCase):
    def test_fixture_keeps_basic_and_textured_sky_paths_live(self):
        basic_v = (FIXTURE / 'gbuffers_skybasic.vsh').read_text(encoding='utf-8')
        basic_f = (FIXTURE / 'gbuffers_skybasic.fsh').read_text(encoding='utf-8')
        textured_v = (FIXTURE / 'gbuffers_skytextured.vsh').read_text(encoding='utf-8')
        textured_f = (FIXTURE / 'gbuffers_skytextured.fsh').read_text(encoding='utf-8')
        final_v = (FIXTURE / 'final.vsh').read_text(encoding='utf-8')
        final_f = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        for vertex in (basic_v, textured_v):
            self.assertIn('gl_Position = ftransform();', vertex)
            self.assertIn('gl_Color', vertex)
            self.assertIn('gl_MultiTexCoord0.st', vertex)
            self.assertIn('gl_MultiTexCoord1.st', vertex)
            self.assertIn('gl_MultiTexCoord2.st', vertex)
            self.assertIn('gl_Normal - vec3(0.0, 0.0, 1.0)', vertex)
        self.assertNotIn('sampler2D', basic_f)
        self.assertIn('vec4(0.0, 1.0, 1.0, 1.0)', basic_f)
        self.assertIn('vec4(1.0, 1.0, 0.0, 1.0)', basic_f)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', basic_f)
        self.assertIn('uniform sampler2D gtexture;', textured_f)
        self.assertIn('texture2D(gtexture, texcoord)', textured_f)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', textured_f)
        self.assertIn('const bool colortex1Clear = true;', basic_f)
        self.assertIn('const bool colortex1Clear = true;', textured_f)
        self.assertIn('/* DRAWBUFFERS:1 */', basic_f)
        self.assertIn('/* DRAWBUFFERS:1 */', textured_f)
        self.assertIn('gl_Position = ftransform();', final_v)
        self.assertIn('uniform sampler2D colortex1;', final_f)
        self.assertEqual({
            'gbuffers_skybasic.vsh', 'gbuffers_skybasic.fsh',
            'gbuffers_skytextured.vsh', 'gbuffers_skytextured.fsh',
            'final.vsh', 'final.fsh'
        }, {path.name for path in FIXTURE.iterdir()})

    def test_sky_maps_all_four_vertex_formats_and_render_stages(self):
        draw = DRAW.read_text(encoding='utf-8')
        self.assertIn('put(new Element(DISC, "gbuffers_skybasic", "disc", DefaultVertexFormat.POSITION,', draw)
        self.assertIn('PrimitiveTopology.TRIANGLE_FAN, Optional.empty(), true, false, RenderStage.SKY', draw)
        self.assertIn('put(new Element("Sky dark", "gbuffers_skybasic", "dark", DefaultVertexFormat.POSITION,', draw)
        self.assertIn('RenderStage.VOID', draw)
        self.assertIn('put(new Element("Stars", "gbuffers_skybasic", "stars", DefaultVertexFormat.POSITION,', draw)
        self.assertIn('RenderStage.STARS', draw)
        self.assertIn('put(new Element("Sunrise sunset", "gbuffers_skybasic", "sunrise",', draw)
        self.assertIn('DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.TRIANGLE_FAN', draw)
        self.assertIn('RenderStage.SUNSET', draw)
        self.assertIn('put(new Element("Sky sun", "gbuffers_skytextured", "sun", DefaultVertexFormat.POSITION_TEX,', draw)
        self.assertIn('RenderStage.SUN', draw)
        self.assertIn('put(new Element("Sky moon", "gbuffers_skytextured", "moon", DefaultVertexFormat.POSITION_TEX,', draw)
        self.assertIn('RenderStage.MOON', draw)
        self.assertIn('put(new Element("End sky", "gbuffers_skytextured", "endsky",', draw)
        self.assertIn('DefaultVertexFormat.POSITION_TEX_COLOR, PrimitiveTopology.QUADS', draw)
        self.assertIn('put(new Element("End flash", "gbuffers_skytextured", "endflash",', draw)
        self.assertGreaterEqual(draw.count('RenderStage.CUSTOM_SKY'), 2)

    def test_sky_runs_before_deferred_with_element_coverage_and_no_depth(self):
        program = PROGRAM.read_text(encoding='utf-8')
        self.assertIn('chainTargets.schedule().step(servedBy)', program)
        self.assertIn('element.covers(), true, false, element.topology()', program)
        self.assertIn('true, null, element.stage()', program)
        self.assertIn('bound, values, load, element.format(), writes, targets, chainRuns', program)
        self.assertIn('this.body.atlas(view);', program)
        self.assertIn('this.body.sampler(sampler);', program)

    def test_sky_decoder_declares_only_the_bound_format_and_synthesizes_the_rest(self):
        vertex = VERTEX.read_text(encoding='utf-8')
        self.assertIn('public static final List<String> ATTRIBUTES = List.of("Position", "Color", "UV0");', vertex)
        self.assertIn('for (String attribute : bound)', vertex)
        self.assertIn('bound.contains("Color")', vertex)
        self.assertIn('bound.contains("UV0")', vertex)
        self.assertIn('#define of_Vertex vec4(Position, 1.0)', vertex)
        self.assertIn('for (int unit = 1; unit <= 2; unit++)', vertex)
        self.assertIn('vec4(240.0, 240.0, 0.0, 1.0)', vertex)
        self.assertIn('#define of_Normal vec3(0.0, 0.0, 1.0)', vertex)


if __name__ == '__main__':
    unittest.main()
