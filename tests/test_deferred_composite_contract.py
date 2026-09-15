"""Lock the PHASE 10A deferred/composite ping-pong acceptance contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/deferred-composite-contract/shaders'
SCHEDULE = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetSchedule.java'


class DeferredCompositeContractTest(unittest.TestCase):
    def text(self, name):
        return (FIXTURE / name).read_text(encoding='utf-8')

    def test_fixture_is_exact_ordered_chain(self):
        self.assertEqual(
            {path.name for path in FIXTURE.iterdir()},
            {
                'deferred.vsh', 'deferred.fsh',
                'composite.vsh', 'composite.fsh',
                'composite1.vsh', 'composite1.fsh',
                'final.vsh', 'final.fsh',
            },
        )

    def test_deferred_seeds_red(self):
        source = self.text('deferred.fsh')
        self.assertIn('DRAWBUFFERS:0', source)
        self.assertIn('vec4(1.0, 0.0, 0.0, 1.0)', source)

    def test_composite_requires_deferred_half(self):
        source = self.text('composite.fsh')
        self.assertIn('uniform sampler2D colortex0;', source)
        self.assertIn('texture2D(colortex0, texcoord)', source)
        self.assertIn('bool sawDeferred', source)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', source)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', source)

    def test_composite1_requires_composite_half(self):
        source = self.text('composite1.fsh')
        self.assertIn('uniform sampler2D colortex0;', source)
        self.assertIn('bool sawComposite', source)
        self.assertIn('vec4(0.0, 0.0, 1.0, 1.0)', source)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', source)

    def test_final_only_presents_latest_colortex0(self):
        source = self.text('final.fsh')
        self.assertIn('uniform sampler2D colortex0;', source)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', source)
        self.assertNotIn('vec4(0.0, 0.0, 1.0', source)

    def test_schedule_keeps_fullscreen_reads_and_writes_on_opposite_halves(self):
        source = SCHEDULE.read_text(encoding='utf-8')
        self.assertIn('a target in the flipped set is read from ALT', source)
        self.assertIn('A full screen one writes the other half', source)
        self.assertIn('if (step.fullscreen() != flipped.contains(index))', source)
        self.assertIn('if (!step.fullscreen())', source)
        self.assertIn('flip(flipped, index);', source)
        self.assertIn('List.of("begin", "prepare", "deferred", "composite")', source)


if __name__ == '__main__':
    unittest.main()
