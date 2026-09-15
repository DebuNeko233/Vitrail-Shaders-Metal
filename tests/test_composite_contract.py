"""Lock PHASE 11 Composite flip and previous-frame history to Vitrail's real target contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FLIP = ROOT / 'tests/fixtures/shaderpacks/composite-flip-contract/shaders'
HISTORY = ROOT / 'tests/fixtures/shaderpacks/composite-history-contract/shaders'
TARGET_PLAN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetPlan.java'
TARGET_SCHEDULE = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetSchedule.java'
CHAIN_PLAN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ChainPlan.java'
COLOR_TARGETS = ROOT / 'common/src/main/java/dev/vitrail/render/ColorTargets.java'
PACK_CHAIN = ROOT / 'common/src/main/java/dev/vitrail/render/PackChain.java'

EXPECTED = {
    'composite.vsh', 'composite.fsh', 'composite1.vsh', 'composite1.fsh',
    'composite2.vsh', 'composite2.fsh', 'final.vsh', 'final.fsh',
}

class CompositeContractTest(unittest.TestCase):
    def text(self, root, name):
        return (root / name).read_text(encoding='utf-8')

    def test_fixtures_are_composite_only(self):
        self.assertEqual(EXPECTED, {p.name for p in FLIP.iterdir()})
        self.assertEqual(EXPECTED, {p.name for p in HISTORY.iterdir()})
        for root in (FLIP, HISTORY):
            self.assertNotIn('deferred', '\n'.join(p.name for p in root.iterdir()))

    def test_flip_fixture_is_red_green_blue_through_three_composites(self):
        first = self.text(FLIP, 'composite.fsh')
        second = self.text(FLIP, 'composite1.fsh')
        third = self.text(FLIP, 'composite2.fsh')
        final = self.text(FLIP, 'final.fsh')
        self.assertIn('/* DRAWBUFFERS:0 */', first)
        self.assertIn('vec4(1.0, 0.0, 0.0, 1.0)', first)
        self.assertIn('uniform sampler2D colortex0;', second)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', second)
        self.assertIn('uniform sampler2D colortex0;', third)
        self.assertIn('vec4(0.0, 0.0, 1.0, 1.0)', third)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', final)
        self.assertNotIn('vec4(0.0, 0.0, 1.0', final)

    def test_history_fixture_requires_a_second_frame(self):
        first = self.text(HISTORY, 'composite.fsh')
        second = self.text(HISTORY, 'composite1.fsh')
        third = self.text(HISTORY, 'composite2.fsh')
        final = self.text(HISTORY, 'final.fsh')
        self.assertIn('const bool colortex2Clear = false;', first)
        self.assertIn('uniform sampler2D colortex2;', first)
        self.assertIn('uniform sampler2D colortex2;', second)
        self.assertIn('uniform sampler2D colortex2;', third)
        self.assertIn('/* DRAWBUFFERS:02 */', third)
        self.assertIn('first ? vec4(1.0, 0.0, 0.0, 1.0)', third)
        self.assertIn('steady ? vec4(0.0, 1.0, 1.0, 1.0)', third)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);', final)
        self.assertNotIn('colortex2', final)

    def test_production_contract_preserves_persistent_flipped_content(self):
        target_plan = TARGET_PLAN.read_text(encoding='utf-8')
        schedule = TARGET_SCHEDULE.read_text(encoding='utf-8')
        chain = CHAIN_PLAN.read_text(encoding='utf-8')
        targets = COLOR_TARGETS.read_text(encoding='utf-8')
        pack_chain = PACK_CHAIN.read_text(encoding='utf-8')

        self.assertIn('allocated.stream().filter(index -> !draft.directives.clears(index)).forEach(persistent::add);', target_plan)
        self.assertIn('a target in the flipped set is read from ALT', schedule)
        self.assertIn('Set<Integer> back = new TreeSet<>(plan.schedule().flippedAtEnd());', chain)
        self.assertIn('back.retainAll(plan.persistent());', chain)
        self.assertIn('ALT to MAIN at end of frame: still flipped, and kept between frames.', chain)
        self.assertIn('TargetSurface alt = this.altSide.get(index);', targets)
        self.assertIn('TargetSurface main = this.mainSide.get(index);', targets)
        self.assertIn('encoder.copyTextureToTexture(from, to, 0, 0, 0, 0, 0, main.width(), main.height());', targets)
        self.assertIn('this.targets.copyBack(device.createCommandEncoder(), this.chain.chain().swapBack());', pack_chain)
        self.assertIn('the pack keeps that target between frames', chain)

if __name__ == '__main__':
    unittest.main()
