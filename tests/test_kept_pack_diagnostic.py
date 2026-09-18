"""The line that says why a kept pack opening could not serve has to name the input that moved.

The reuse decision compares four answers as one: the pack path, the settings the player chose, the
profile, and the engine's whole machine define table. That is right for the decision and useless for
whoever reads the line it produces, because the reader has to be sent to the right one of four
places, and the fourth is the one that moves on its own.

This pins the shape that makes the fourth actionable rather than merely reported: the names of the
defines that differ, with the value each held, in the direction from what was held to what is
wanted. A count would say that something moved; the name is what the next run can chase.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
KEPT = ROOT / 'common/src/main/java/dev/vitrail/pack/source/KeptPack.java'
BUILD = ROOT / '.github/workflows/build.yml'


class KeptPackDiagnostic(unittest.TestCase):
    def setUp(self):
        self.text = KEPT.read_text(encoding='utf-8')
        self.why = self.text.split('private static String why(', 1)[1].split('\n\t}', 1)[0]
        self.difference = self.text.split('String difference(Key other) {', 1)[1].split('\n\t\t}', 1)[0]

    def test_the_line_no_longer_names_three_places_at_once(self):
        # The old wording sent a reader to the pack, the settings and the defines together. Asked of
        # the method that returns the line and not of the file, because the replacement's own javadoc
        # quotes the wording it replaced.
        self.assertNotIn("the pack, its settings or the machine's defines have moved", self.why)
        self.assertIn('standing.key().difference(wanted)', self.why)

    def test_each_input_the_decision_uses_can_be_named(self):
        for said in (
            'the pack path has moved',
            'the settings the player chose have moved',
            'the profile has moved',
            "the machine's defines have moved",
        ):
            with self.subTest(said=said):
                self.assertIn(said, self.difference)

    def test_the_defines_are_named_with_their_change_and_bounded(self):
        self.assertIn('new TreeSet<>(defines.keySet())', self.difference)
        self.assertIn('names.addAll(other.defines.keySet())', self.difference)
        self.assertIn('name + " " + was + " -> " + now', self.difference)
        # Bounded, so a table that moved wholesale is still one line.
        self.assertIn('if (moved.size() < 3) {', self.difference)

    def test_the_other_reasons_for_reopening_are_untouched(self):
        for said in (
            'none was held',
            'this one is not fingerprinted, so it will not be held either',
            'the shadow map scale has moved',
            'its files have moved on the disk',
        ):
            with self.subTest(said=said):
                self.assertIn(said, self.text)

    def test_the_contract_is_named_by_a_workflow(self):
        # A contract no workflow runs asserts nothing, which is the rule this repository already
        # enforces over the whole directory; this is the same claim for this file alone.
        self.assertIn('tests/test_kept_pack_diagnostic.py', BUILD.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
