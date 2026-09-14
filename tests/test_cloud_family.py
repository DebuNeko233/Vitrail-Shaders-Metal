"""Lock PHASE 7 cloud routing, post-deferred schedule and buffer-driven vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/clouds-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/CloudDraw.java'
PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/CloudProgram.java'
VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/CloudVertex.java'


class CloudFamilyTest(unittest.TestCase):
    def test_fixture_is_isolated_and_keeps_cloud_buffer_abi_live_without_a_sampler(self):
        vertex = (FIXTURE / 'gbuffers_clouds.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_clouds.fsh').read_text(encoding='utf-8')
        final_vertex = (FIXTURE / 'final.vsh').read_text(encoding='utf-8')
        final_fragment = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('gl_Color', vertex)
        self.assertIn('gl_MultiTexCoord0.st', vertex)
        self.assertIn('gl_MultiTexCoord1.st', vertex)
        self.assertIn('gl_MultiTexCoord2.st', vertex)
        self.assertIn('gl_Normal', vertex)
        self.assertNotIn('sampler2D', fragment)
        self.assertIn('const bool colortex1Clear = true;', fragment)
        self.assertIn('/* DRAWBUFFERS:1 */', fragment)
        self.assertIn('? vec4(0.0, 1.0, 0.0, 1.0)', fragment)
        self.assertIn(': vec4(1.0, 0.0, 1.0, 1.0)', fragment)
        self.assertIn('gl_Position = ftransform();', final_vertex)
        self.assertIn('uniform sampler2D colortex1;', final_fragment)
        self.assertEqual({'gbuffers_clouds.vsh', 'gbuffers_clouds.fsh', 'final.vsh', 'final.fsh'},
                         {path.name for path in FIXTURE.iterdir()})

    def test_clouds_have_fancy_and_flat_pipeline_state_but_one_pack_program_after_deferred(self):
        draw = DRAW.read_text(encoding='utf-8')
        program = PROGRAM.read_text(encoding='utf-8')

        self.assertIn('private static final String PROGRAM = "gbuffers_clouds";', draw)
        self.assertIn('this.plan.sky(PROGRAM)', draw)
        self.assertIn('fancy ? "fancy" : "flat", NAMESPACE,', program)
        self.assertIn('chainTargets.schedule().stepAfterDeferred(servedBy)', program)
        self.assertIn('Optional.of(BlendFunction.TRANSLUCENT)', program)
        self.assertIn('false, false, true, PrimitiveTopology.QUADS', program)
        self.assertIn('DepthStencilState.DEFAULT, RenderStage.CLOUDS, BindGroupLayouts.CLOUD_INFO', program)
        self.assertIn('bound, values, load,', program)
        self.assertIn('null, writes, targets, chainRuns));', program)

    def test_cloud_decoder_is_buffer_driven_and_declares_no_vertex_format(self):
        vertex = VERTEX.read_text(encoding='utf-8')
        program = PROGRAM.read_text(encoding='utf-8')

        self.assertIn('public static final List<String> ATTRIBUTES = List.of("CloudInfo", "CloudFaces");', vertex)
        self.assertIn('layout(std140) uniform CloudInfo {', vertex)
        self.assertIn('uniform isamplerBuffer CloudFaces;', vertex)
        self.assertIn('texelFetch(CloudFaces', vertex)
        self.assertIn('gl_VertexID', vertex)
        self.assertIn('#define of_Vertex vec4(of_cloudPosition(), 1.0)', vertex)
        self.assertIn('#define of_Color', vertex)
        self.assertIn('#define of_Normal of_cloudNormals[of_cloudFacing()]', vertex)
        self.assertIn('#define of_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)', vertex)
        self.assertIn('#define of_MultiTexCoord1 vec4(240.0, 240.0, 0.0, 1.0)', vertex)
        self.assertIn('// No format, which this family is alone in', program)


if __name__ == '__main__':
    unittest.main()
