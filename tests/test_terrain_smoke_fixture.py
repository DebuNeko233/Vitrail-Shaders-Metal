"""Keep the deterministic terrain smoke fixture tied to PHASE 5 semantics."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/terrain-contract'
SHADERS = FIXTURE / 'shaders'


class TerrainSmokeFixtureTest(unittest.TestCase):
    def test_direct_camera_pass_programs_and_final_are_present(self):
        expected = {
            'gbuffers_terrain.vsh', 'gbuffers_terrain.fsh',
            'gbuffers_terrain_solid.vsh', 'gbuffers_terrain_solid.fsh',
            'gbuffers_terrain_cutout.vsh', 'gbuffers_terrain_cutout.fsh',
            'gbuffers_water.vsh', 'gbuffers_water.fsh',
            'final.vsh', 'final.fsh',
        }
        self.assertEqual({path.name for path in SHADERS.iterdir() if path.is_file()}, expected)

    def test_solid_is_opaque_red(self):
        source = (SHADERS / 'gbuffers_terrain_solid.fsh').read_text(encoding='utf-8')
        self.assertIn('gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);', source)

    def test_cutout_is_green_but_keeps_real_texture_alpha_on_legacy_slot_zero(self):
        source = (SHADERS / 'gbuffers_terrain_cutout.fsh').read_text(encoding='utf-8')
        self.assertIn('uniform sampler2D texture;', source)
        self.assertIn('float alpha = texture2D(texture, texcoord).a;', source)
        self.assertIn('gl_FragColor = vec4(0.0, 1.0, 0.0, alpha);', source)
        self.assertNotIn('discard', source, 'Vitrail, not the fixture, must inject fixed-function alpha test')

    def test_water_is_blue_and_non_opaque_to_exercise_pass_blending(self):
        source = (SHADERS / 'gbuffers_water.fsh').read_text(encoding='utf-8')
        self.assertIn('gl_FragColor = vec4(0.0, 0.0, 1.0, 0.75);', source)

    def test_final_only_exposes_colortex_zero(self):
        source = (SHADERS / 'final.fsh').read_text(encoding='utf-8')
        self.assertIn('uniform sampler2D colortex0;', source)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', source)
        for target in ('colortex1', 'colortex2', 'colortex3'):
            self.assertNotIn(target, source)

    def test_readme_keeps_manual_alpha_silhouette_boundary_explicit(self):
        readme = (FIXTURE / 'README.md').read_text(encoding='utf-8')
        self.assertIn('cutout silhouettes are a separate visual acceptance item', readme)
        self.assertIn('grass-block side overlay', readme)
        for material in ('flower', 'leaves', 'fire', 'water'):
            self.assertIn(material, readme)


if __name__ == '__main__':
    unittest.main()
