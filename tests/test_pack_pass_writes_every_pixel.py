"""A pass decides whether its targets are loaded or emptied before it writes them.

Three answers go into that decision, and this file pins all three: whether the fragment stage can
leave a pixel of its targets unwritten, whether any sampler of the program names a target this pass
also writes on the same half, and whether the draw covers the whole screen. A pass drawn over the
whole screen that writes every pixel of a target it does not also read has no use for what stood
there, and emptying a target is a tile fill where loading it is the target's bytes read back off the
device.

It also pins the shape of the decision itself, and that it is off unless the property asked for it. A
wrong load action is not a slower frame, it is a wrong picture that reads as a pack defect, so the
default stays what the engine did before any of this existed until a session with the property on has
been measured against one with it off.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'
TARGETS = ROOT / 'common/src/main/java/dev/vitrail/render/ColorTargets.java'
GEOMETRY = ROOT / 'common/src/main/java/dev/vitrail/render/GeometryProgram.java'
BUILD = ROOT / '.github/workflows/build.yml'


class PassWritesEveryPixel(unittest.TestCase):
    def setUp(self):
        self.text = PASS.read_text(encoding='utf-8')
        self.targets = TARGETS.read_text(encoding='utf-8')

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

    def test_the_second_answer_is_what_a_sampler_reads(self):
        # The half the pass's own text cannot answer. Read off the program's sampled names, so a name
        # the pack declares and never reads does not make its target's contents matter, and joined on
        # the half, because the other half of a ping-pong pair is a different image.
        self.assertIn('this.readsWhatItWrites = readsWhatItWrites();', self.text)
        self.assertIn('Set<String> sampled = this.loaded.program().sampled();', self.text)
        self.assertIn('binding.kind() == SamplerPlan.Kind.COLORTEX', self.text)
        self.assertIn('binding.index() == attachment.target()', self.text)
        self.assertIn('binding.side() == attachment.side()', self.text)
        self.assertIn('sampled.contains(this.samplers.get(at))', self.text)

    def test_the_third_answer_is_the_draw_being_the_whole_screen(self):
        # What keeps the other two honest: a pass drawn over part of a target writes every pixel of
        # that part and leaves every pixel outside it as it stood.
        self.assertIn('this.pass.size().width(screenWidth) == screenWidth', self.text)
        self.assertIn('this.pass.size().height(screenHeight) == screenHeight', self.text)
        self.assertIn('!this.mayLeavePixelsUnwritten\n\t\t\t\t&& !this.readsWhatItWrites', self.text)

    def test_it_is_off_unless_the_property_asks_for_it(self):
        self.assertIn('Boolean.getBoolean("vitrail.elideTargetLoads")', self.text)
        self.assertIn('boolean elide = ELIDE_TARGET_LOADS && writesEveryPixel', self.text)

    def test_a_session_with_it_on_says_so_in_its_own_log(self):
        # Otherwise a run with the property on cannot be told from one with it off except by its
        # numbers, and the numbers are the thing being measured.
        self.assertIn('elideTargetLoads is on', self.text)
        self.assertIn('ELIDE_TARGET_LOADS && !this.mayLeavePixelsUnwritten', self.text)

    def test_the_backend_is_told_the_fact_this_pass_owns_and_no_other(self):
        # One fact is handed over: this draw writes every pixel of the whole screen, so nothing it
        # is about to draw read what stood in its targets. "Nothing reads them afterwards" belongs
        # to the frame's schedule, so every slot is stated as still wanted - a wrong store answer is
        # a wrong image, which is the one mistake this shape cannot survive.
        self.assertIn('boolean told = elide && tellTheBackend(encoder);', self.text)
        self.assertIn('if (!(encoder instanceof AttachmentCommands commands)) {', self.text)
        self.assertIn('Arrays.fill(readAfterwards, true);', self.text)
        self.assertIn('commands.vitrail$setNextPassContents(readAfterwards, overwritten);', self.text)

    def test_a_backend_that_cannot_be_told_falls_back_to_a_clear(self):
        # The clear is the same traffic saved, paid for as a tile fill this engine asks for instead.
        self.assertIn('descriptor.withColorAttachment(view, !elide || told', self.text)
        self.assertIn(': targets.takeClearOrEmpty(view));', self.text)

    def test_a_clear_the_frame_owes_is_never_replaced(self):
        # A clear is owed for a reason this pass cannot see, and its colour is the pack's. The
        # emptying goes through ColorTargets rather than being built at the call site.
        self.assertIn('targets.takeClearOrEmpty(view)', self.text)
        self.assertIn('Optional<Vector4fc> takeClearOrEmpty(GpuTextureView view) {', self.targets)
        self.assertIn('Optional<Vector4fc> owed = takeClear(view);', self.targets)
        self.assertIn('return owed.isPresent() ? owed : Optional.of(EMPTY_CLEAR);', self.targets)

    def test_the_fallback_is_what_the_engine_did_before(self):
        # Two branches at one call site: the property off, or a draw that has not answered all three,
        # takes the owed clear and nothing else.
        self.assertIn('? targets.takeClear(view)', self.text)
        self.assertIn(': targets.takeClearOrEmpty(view));', self.text)
        self.assertEqual(self.text.count('withColorAttachment('), 1)

    def test_the_geometry_path_keeps_its_own_answer(self):
        # It asks the same question of its own attachments and makes its own decision from it; this
        # change is about the full screen family and does not reach into that one.
        geometry = GEOMETRY.read_text(encoding='utf-8')
        self.assertIn('descriptor.withColorAttachment(view, this.targets.takeClear(view));', geometry)
        self.assertNotIn('takeClearOrEmpty', geometry)

    def test_the_contract_is_named_by_a_workflow(self):
        self.assertIn('tests/test_pack_pass_writes_every_pixel.py',
                      BUILD.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
