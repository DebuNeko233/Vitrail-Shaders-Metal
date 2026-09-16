"""Lock PHASE 7 weather routing, post-deferred schedule and PARTICLE vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/weather-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/WeatherDraw.java'
PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/WeatherProgram.java'
VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/ParticleVertex.java'


class WeatherFamilyTest(unittest.TestCase):
    def test_fixture_is_isolated_and_keeps_weather_texture_plus_particle_abi_live(self):
        vertex = (FIXTURE / 'gbuffers_weather.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_weather.fsh').read_text(encoding='utf-8')
        final_vertex = (FIXTURE / 'final.vsh').read_text(encoding='utf-8')
        final_fragment = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('gl_Color', vertex)
        self.assertIn('gl_MultiTexCoord0.st', vertex)
        self.assertIn('gl_MultiTexCoord1.st', vertex)
        self.assertIn('gl_MultiTexCoord2.st', vertex)
        self.assertIn('gl_Normal - vec3(0.0, 0.0, 1.0)', vertex)
        self.assertIn('uniform sampler2D gtexture;', fragment)
        self.assertIn('texture2D(gtexture, texcoord)', fragment)
        self.assertIn('const bool colortex1Clear = true;', fragment)
        self.assertIn('/* DRAWBUFFERS:1 */', fragment)
        self.assertIn('? vec4(0.0, 1.0, 0.0, 1.0)', fragment)
        self.assertIn(': vec4(1.0, 0.0, 1.0, 1.0)', fragment)
        self.assertIn('gl_Position = ftransform();', final_vertex)
        self.assertIn('uniform sampler2D colortex1;', final_fragment)
        self.assertEqual({'gbuffers_weather.vsh', 'gbuffers_weather.fsh', 'final.vsh', 'final.fsh'},
                         {path.name for path in FIXTURE.iterdir()})

    def test_weather_has_one_pack_program_two_game_depth_states_and_runs_after_deferred(self):
        draw = DRAW.read_text(encoding='utf-8')
        program = PROGRAM.read_text(encoding='utf-8')

        self.assertIn('private static final String PROGRAM = "gbuffers_weather";', draw)
        self.assertIn('RenderPipelines.WEATHER_NO_DEPTH_WRITE, "weather", RenderStage.RAIN_SNOW', draw)
        self.assertIn('RenderPipelines.WEATHER_DEPTH_WRITE, "weather_depth", RenderStage.RAIN_SNOW', draw)
        self.assertIn('VertexInputs.PARTICLE, false', draw)
        self.assertIn('chainTargets.schedule().stepAfterDeferred(servedBy)', program)
        self.assertIn('false, false, true, game.getPrimitiveTopology()', program)
        self.assertIn('DefaultVertexFormat.PARTICLE', program)

    def test_weather_reuses_the_complete_particle_decoder_without_a_weather_specific_layout(self):
        vertex = VERTEX.read_text(encoding='utf-8')
        self.assertIn('public static final List<String> ATTRIBUTES = List.of("Position", "UV0", "Color", "UV2");', vertex)
        self.assertIn('lines.add("#define of_Color Color");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord1 vec4(UV2, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord2 vec4(UV2, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");', vertex)


if __name__ == '__main__':
    unittest.main()
