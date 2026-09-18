"""A pass says whether its fragment stage can leave a pixel of its targets unwritten.

That answer is half of the load-action decision: a pass drawn over the whole area that writes every
pixel of a target it does not also sample has no use for what stood there, and emptying a target is a
tile fill where loading it is the target's bytes read back off the device.

This file pins the half that exists and the shape of it, and it also pins that the decision itself is
NOT taken yet: the descriptor still takes only what `takeClear` returns, so nothing about which
action a pass asks for has changed. An observation that quietly became a behaviour is exactly what
this contract exists to catch.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'
BUILD = ROOT / '.github/workflows/build.yml'


class PassWritesEveryPixel(unittest.TestCase):
    def setUp(self):
        self.text = PASS.read_text(encoding='utf-8')

    def test_the_question_is_asked_of_the_spelling(self):
        # Asked of the spelling and not of the live branches, because this translator leaves every
        # #if standing: a branch the expander read as dead is one the compiler may read as live, and
        # the answer that keeps pixels is the safe one.
        self.assertIn('private static final String DISCARD = "discard";', self.text)
        self.assertIn('this.mayLeavePixelsUnwritten = fragment.contains(DISCARD);', self.text)

    def test_it_is_answered_from_the_fragment_stage_and_not_the_vertex_one(self):
        fragment = self.text.split('String fragment = loaded.program()', 1)[1]
        self.assertLess(fragment.index('fragment.contains(DISCARD)'), fragment.index('String stem'))

    def test_the_pass_says_which_of_the_two_it_is(self):
        self.assertIn('"a fragment stage that can leave a pixel unwritten"', self.text)
        self.assertIn('"a fragment stage that writes every pixel"', self.text)

    def test_the_load_decision_itself_is_not_taken_yet(self):
        # One call site, and it still passes only the owed clear. When the other half - whether this
        # pass reads a target it writes - is answered, this assertion is the one that changes, and
        # the diff that changes it is the one that needs a device run.
        self.assertIn('descriptor.withColorAttachment(view, targets.takeClear(view));', self.text)
        self.assertEqual(self.text.count('withColorAttachment('), 1)

    def test_the_contract_is_named_by_a_workflow(self):
        self.assertIn('tests/test_pack_pass_writes_every_pixel.py',
                      BUILD.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
