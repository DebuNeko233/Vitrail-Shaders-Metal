"""Lock PHASE 7 opaque/translucent particle routing, schedule and PARTICLE vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
OPAQUE = ROOT / 'tests/fixtures/shaderpacks/particles-opaque-contract/shaders'
TRANSLUCENT = ROOT / 'tests/fixtures/shaderpacks/particles-translucent-contract/shaders'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/ParticleDraw.java'
PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/ParticleProgram.java'
VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/ParticleVertex.java'
VERTEX_INPUTS = ROOT / 'common/src/main/java/dev/vitrail/glsl/VertexInputs.java'


class ParticleFamilyTest(unittest.TestCase):
    def test_fixtures_are_independent_and_keep_particle_abi_plus_real_atlas_live(self):
        for fixture, program in ((OPAQUE, 'gbuffers_particles'),
                                 (TRANSLUCENT, 'gbuffers_particles_translucent')):
            vertex = (fixture / f'{program}.vsh').read_text(encoding='utf-8')
            fragment = (fixture / f'{program}.fsh').read_text(encoding='utf-8')
            final_vertex = (fixture / 'final.vsh').read_text(encoding='utf-8')
            final_fragment = (fixture / 'final.fsh').read_text(encoding='utf-8')

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

            names = {path.name for path in fixture.iterdir()}
            self.assertEqual({f'{program}.vsh', f'{program}.fsh', 'final.vsh', 'final.fsh'}, names)

    def test_particle_halves_have_distinct_programs_and_opposite_deferred_sides(self):
        draw = DRAW.read_text(encoding='utf-8')
        program = PROGRAM.read_text(encoding='utf-8')

        self.assertIn('RenderPipelines.OPAQUE_PARTICLE, "particles",', draw)
        self.assertIn('"gbuffers_particles", false', draw)
        self.assertIn('RenderPipelines.TRANSLUCENT_PARTICLE, "particles_translucent",', draw)
        self.assertIn('"gbuffers_particles_translucent", true', draw)
        self.assertIn('return RenderStage.PARTICLES;', draw)
        self.assertIn('VertexInputs.PARTICLE, !this.afterDeferred', draw)
        self.assertIn('element.afterDeferred()', program)
        self.assertIn('chainTargets.schedule().stepAfterDeferred(servedBy)', program)
        self.assertIn('chainTargets.schedule().step(servedBy)', program)
        self.assertIn('!element.afterDeferred(), false, element.afterDeferred(),', program)
        self.assertIn('DefaultVertexFormat.PARTICLE', program)

    def test_particle_decoder_locks_all_four_mesh_elements_and_legacy_aliases(self):
        vertex = VERTEX.read_text(encoding='utf-8')
        inputs = VERTEX_INPUTS.read_text(encoding='utf-8')

        self.assertIn('public static final List<String> ATTRIBUTES = List.of("Position", "UV0", "Color", "UV2");', vertex)
        self.assertIn('lines.add("#define of_Vertex vec4(Position, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_Color Color");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord0 vec4(UV0, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord1 vec4(UV2, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_MultiTexCoord2 vec4(UV2, 0.0, 1.0)");', vertex)
        self.assertIn('lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");', vertex)
        self.assertIn('\n\tPARTICLE,', inputs)


if __name__ == '__main__':
    unittest.main()
