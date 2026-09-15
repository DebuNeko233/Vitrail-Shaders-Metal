"""Lock the PHASE 10 deferred-family ping-pong acceptance contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/deferred-contract/shaders'
SCHEDULE = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetSchedule.java'


class DeferredContractTest(unittest.TestCase):
    def text(self, name):
        return (FIXTURE / name).read_text(encoding='utf-8')

    def test_fixture_is_exact_deferred_family_plus_observer(self):
        self.assertEqual({path.name for path in FIXTURE.iterdir()}, {
            'deferred.vsh', 'deferred.fsh', 'deferred1.vsh', 'deferred1.fsh',
            'deferred2.vsh', 'deferred2.fsh', 'final.vsh', 'final.fsh',
        })

    def test_state_machine_is_red_green_blue_with_magenta_failures(self):
        first = self.text('deferred.fsh')
        second = self.text('deferred1.fsh')
        third = self.text('deferred2.fsh')
        final = self.text('final.fsh')
        self.assertIn('vec4(1.0, 0.0, 0.0, 1.0)', first)
        self.assertIn('bool sawDeferred', second)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', second)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', second)
        self.assertIn('bool sawDeferred1', third)
        self.assertIn('vec4(0.0, 0.0, 1.0, 1.0)', third)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', third)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', final)
        self.assertNotIn('vec4(0.0, 0.0, 1.0', final)

    def test_deferred_family_uses_authoritative_fullscreen_half_walk(self):
        source = SCHEDULE.read_text(encoding='utf-8')
        self.assertIn('A full screen one writes the other half', source)
        self.assertIn('if (step.fullscreen() != flipped.contains(index))', source)
        self.assertIn('flip(flipped, index);', source)
        self.assertIn('int deferredRank = ProgramNames.frameRank("deferred");', source)
        self.assertIn('afterDeferred = sortedCopy(flipped);', source)


if __name__ == '__main__':
    unittest.main()
