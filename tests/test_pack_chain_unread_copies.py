"""A target nothing in the frame reads is not copied back for a reader that does not exist.

At the end of every frame this engine copies each target the pack keeps between frames back from the
half the chain left it on, because the next frame walks from an empty flipped set. The walk that
decides those copies already computes which of them has a reader at all, and it already prints that
answer - on one measured Photon session, ten targets are copied and three of them are read by nothing
in the frame. This file pins the switch that acts on that number, and the four things about it that
would make it a wrong picture rather than a slower frame if they changed:

  - it is off unless the property asked for it, because a copy is part of what the image is;
  - the question is asked of the plan's own read set, on the half the copy writes to, and not of a
    second opinion computed here;
  - a target the frame *does* read is still copied, so the filter keeps rather than drops;
  - the session says which of the two configurations it ran, so a log can be read without knowing
    which switches produced it.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACK_CHAIN = ROOT / 'common/src/main/java/dev/vitrail/render/PackChain.java'
CHAIN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ChainPlan.java'
BUILD = ROOT / '.github/workflows/build.yml'


class UnreadCopies(unittest.TestCase):
    def setUp(self):
        self.chain_source = PACK_CHAIN.read_text(encoding='utf-8')

    def test_the_switch_is_off_unless_it_was_asked_for(self):
        self.assertIn(
            'private static final boolean ELIDE_TARGET_COPIES = '
            'Boolean.getBoolean("vitrail.elideTargetCopies");',
            self.chain_source,
        )
        self.assertNotIn('ELIDE_TARGET_COPIES = true', self.chain_source)

    def test_it_answers_from_the_plans_read_set_on_the_half_the_copy_writes(self):
        helper = self.chain_source.split('private List<Integer> copiesBack()', 1)[1]
        helper = helper.split('private void run()', 1)[0]
        self.assertIn('Set<ChainPlan.Attachment> read = this.chain.chain().readInFrame();', helper)
        self.assertIn('new ChainPlan.Attachment(index, TargetSchedule.Side.MAIN)', helper)
        # The candidate list is still the plan's own swap-back set: this filter may only take copies
        # away from it, never add one.
        self.assertIn('List<Integer> back = this.chain.chain().swapBack();', helper)

    def test_a_reader_keeps_the_copy(self):
        helper = self.chain_source.split('private List<Integer> copiesBack()', 1)[1]
        helper = helper.split('private void run()', 1)[0]
        guarded = helper.split('if (read.contains(', 1)[1]
        self.assertIn('moving.add(index);', guarded.split('}', 1)[0])
        self.assertNotIn('else', guarded.split('return moving;', 1)[0])

    def test_the_copy_site_goes_through_the_filter(self):
        self.assertIn('this.targets.copyBack(device.createCommandEncoder(), copiesBack());',
                      self.chain_source)
        # And the plan's own list is no longer handed to the copy directly, which is what would make
        # the switch a comment rather than a decision.
        self.assertNotIn('copyBack(device.createCommandEncoder(), this.chain.chain().swapBack())',
                         self.chain_source)

    def test_the_switch_is_only_taken_when_the_walk_found_something_to_leave(self):
        helper = self.chain_source.split('private List<Integer> copiesBack()', 1)[1]
        helper = helper.split('private void run()', 1)[0]
        self.assertIn('if (!ELIDE_TARGET_COPIES || back.isEmpty()) {', helper)

    def test_the_session_says_which_configuration_it_ran(self):
        announcement = self.chain_source.split('targets are copied back from their far half', 1)[1]
        announcement = announcement.split('unfolded.history()', 1)[0]
        self.assertIn('elideTargetCopies is on', announcement)
        self.assertIn('elideTargetCopies is off', announcement)

    def test_the_plan_still_answers_the_question_it_is_asked(self):
        chain = CHAIN.read_text(encoding='utf-8')
        self.assertIn('public List<Integer> swapBack() {', chain)
        self.assertIn('public Set<Attachment> readInFrame() {', chain)

    def test_this_file_is_named_by_the_workflow(self):
        self.assertIn('tests/test_pack_chain_unread_copies.py', BUILD.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
