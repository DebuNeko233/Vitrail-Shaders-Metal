"""Lock the PHASE 10 deferred-family depth acceptance contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/deferred-depth-contract/shaders'
PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'
SAMPLERS = ROOT / 'common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java'


class DeferredDepthContractTest(unittest.TestCase):
    def text(self, name):
        return (FIXTURE / name).read_text(encoding='utf-8')

    def test_fixture_is_exact_deferred_family_plus_observer(self):
        self.assertEqual({path.name for path in FIXTURE.iterdir()}, {
            'deferred.vsh', 'deferred.fsh', 'deferred1.vsh', 'deferred1.fsh',
            'deferred2.vsh', 'deferred2.fsh', 'final.vsh', 'final.fsh',
        })

    def test_every_deferred_pass_samples_depthtex0(self):
        for program in ('deferred', 'deferred1', 'deferred2'):
            source = self.text(program + '.fsh')
            self.assertIn('uniform sampler2D depthtex0;', source)
            self.assertGreaterEqual(source.count('texture2D(depthtex0'), 3)
            self.assertIn('variation = max(abs(center - right), abs(center - down)) > 0.0000001;', source)
            self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', source)

    def test_variation_bit_is_carried_through_all_three_deferred_passes(self):
        first = self.text('deferred.fsh')
        second = self.text('deferred1.fsh')
        third = self.text('deferred2.fsh')
        final = self.text('final.fsh')

        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', first)
        self.assertIn('vec4(0.0, 1.0, 1.0, 1.0)', first)

        self.assertIn('uniform sampler2D colortex0;', second)
        self.assertIn('bool previousValid = previous.g > 0.75 && previous.r < 0.25;', second)
        self.assertIn('bool previousVariation = previous.b > 0.50;', second)
        self.assertIn('variation != previousVariation', second)
        self.assertIn('vec4(0.0, 0.0, 1.0, 1.0)', second)
        self.assertIn('vec4(0.0, 1.0, 1.0, 1.0)', second)

        self.assertIn('uniform sampler2D colortex0;', third)
        self.assertIn('bool previousValid = previous.b > 0.75 && previous.r < 0.25;', third)
        self.assertIn('bool previousVariation = previous.g > 0.50;', third)
        self.assertIn('variation != previousVariation', third)
        self.assertIn('vec4(1.0, 0.0, 0.0, 1.0)', third)
        self.assertIn('vec4(1.0, 1.0, 0.0, 1.0)', third)

        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', final)
        self.assertNotIn('vec4(1.0, 1.0, 0.0', final)

    def test_production_binds_deferred_depthtex0_to_the_opaque_world_snapshot(self):
        pack_pass = PASS.read_text(encoding='utf-8')
        samplers = SAMPLERS.read_text(encoding='utf-8')
        self.assertIn('"depthtex0", "depthtex1", "depthtex2", "gdepthtex"', samplers)
        self.assertIn('which for a deferred is the opaque world and for a composite the whole', pack_pass)
        self.assertIn('case DEPTH -> depth(binding.sampler(), targets, depthView);', pack_pass)
        self.assertIn('return depthView == null ? targets.white() : depthView;', pack_pass)


if __name__ == '__main__':
    unittest.main()
