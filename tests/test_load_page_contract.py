"""Lock the load page's reference to the game's own terrain loading screen, without a live GPU.

The page exists because a pack compile holds the world back and nothing of the level is drawn
while it does, so the screen is the frame from before the pack was replaced. What it must stay is
the shape `LevelLoadingScreen` draws while it waits for chunks, and a count that is the same read
the corner's sentence came from. Both are properties of the source rather than of a picture, so
they are checked here rather than trusted to review.

Every assertion below is on the whole expression rather than on a lone constant, because a constant
correctly spelled and wrongly applied is exactly the mistake this file exists to catch: the first
version of the page carried the terrain loading screen's `12` across and then added the font's line
height to it as well, which drew the bar a whole line lower than the screen it claims to copy.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / 'common/src/main/java/dev/vitrail/screen/LoadPage.java'
CARD = ROOT / 'common/src/main/java/dev/vitrail/screen/CompileCard.java'
CHAIN = ROOT / 'common/src/main/java/dev/vitrail/render/PackChain.java'


def source(path):
    return path.read_text(encoding='utf-8')


def body_of(text, marker):
    """Everything after a method signature, for assertions about one method rather than the file."""
    return text.split(marker, 1)[1]


class LoadPageContract(unittest.TestCase):
    def test_the_bar_is_the_terrain_loading_screens_own_geometry(self):
        page = source(PAGE)
        body = body_of(page, 'private static void page(GuiGraphicsExtractor graphics, float alpha, long now)')

        self.assertIn('private static final int BAR_WIDTH = 200;', page)
        self.assertIn('private static final int BAR_HEIGHT = 2;', page)
        # LevelLoadingScreen draws its sentence at a top and then puts the bar at that top plus its
        # font's line height plus three. The three is the constant and the line height is added from
        # the font, so the pair cannot be folded into one number and silently become a line too tall.
        self.assertIn('private static final int BAR_LABEL_GAP = 3;', page)
        self.assertIn('bar(graphics, centreX - BAR_WIDTH / 2, labelY + font.lineHeight + BAR_LABEL_GAP, alpha);', body)
        self.assertIn('graphics.centeredText(font, lastWords, centreX, labelY,', body)
        self.assertIn('graphics.fill(RenderPipelines.GUI, x, y, x + BAR_WIDTH, y + BAR_HEIGHT,', page)

    def test_the_fill_is_drawn_over_the_track_and_sized_by_the_count(self):
        bar = body_of(source(PAGE), 'private static void bar(')

        # The track first and the fill over it, which is drawProgressBar's order and the one thing
        # about it that has to stay: the two are the same rectangle, not two halves.
        track = bar.index('ARGB.colorFromFloat(alpha, 0.0F, 0.0F, 0.0F)')
        fill = bar.index('ARGB.colorFromFloat(alpha, 0.0F, 1.0F, 0.0F)')
        self.assertLess(track, fill, "the fill must be drawn after the track it covers")
        self.assertIn('x + Math.round(lastProgress * BAR_WIDTH)', bar)

    def test_a_count_that_is_not_known_yet_draws_no_bar(self):
        bar = body_of(source(PAGE), 'private static void bar(')

        # A bare count is not a count of nought: a bar at nought reads as a compile that has not
        # started, which is the one thing the words deliberately avoid saying.
        self.assertIn('if (lastProgress < 0.0F) {', bar)
        self.assertLess(bar.index('if (lastProgress < 0.0F) {'), bar.index('graphics.fill('))

    def test_the_page_stands_only_while_the_world_is_held_back(self):
        page = source(PAGE)
        body = body_of(page, 'static void extract(GuiGraphicsExtractor graphics, Optional<PackChain.CompilingState> state)')

        # warming() is the only predicate that means the level is not being drawn: branching on
        # compiling() would put a page over a world that is being played, where the corner belongs.
        self.assertIn('if (PackChain.warming()) {', body)
        self.assertNotIn('PackChain.compiling()', page)
        self.assertIn('} else if (!stood) {', body)

    def test_everything_the_page_carries_is_reset_when_the_load_changes(self):
        page = source(PAGE)
        reset = body_of(page, 'if (stateFor != load) {').split('\n\t\t}\n', 1)[0]

        # The count belongs to one load. A load that inherited the last one's fraction would open
        # under bare words with a bar already part-filled by a different compile's arithmetic, which
        # is the one thing the count is never allowed to say.
        self.assertIn('stoodAt = now;', reset)
        self.assertIn('heldAt = now;', reset)
        self.assertIn('stood = false;', reset)
        self.assertIn('lastWords = Component.translatable(ScreenText.COMPILING);', reset)
        self.assertIn('lastProgress = -1.0F;', reset)

    def test_the_fraction_follows_the_count_rather_than_latching_it(self):
        page = source(PAGE)

        # The total the count is measured against grows as each family's translation lands, so the
        # fraction can step back when a new family's programs arrive. Pinned to a high-water mark it
        # would sit full while the sentence above it read otherwise, and it would carry a previous
        # load's fill into a load whose count is not known yet.
        self.assertIn('state.ifPresent(inFlight -> lastProgress = inFlight.progress());', page)
        self.assertNotIn('Math.max', page)

    def test_the_lift_is_timed_from_the_last_frame_the_world_was_held(self):
        page = source(PAGE)
        body = body_of(page, 'static void extract(GuiGraphicsExtractor graphics, Optional<PackChain.CompilingState> state)')

        # Not from the first frame that notices the world is back. This page is not drawn at all
        # under F3 or F1, nor where the corner has nothing left to say, so that frame can be
        # arbitrarily late, and a page that stamped it would paint itself opaque over a world
        # already being played and fade from there.
        self.assertIn('heldAt = now;', body)
        self.assertIn('1.0F - CompileCard.ramp(now - heldAt)', body)
        self.assertIn('CompileCard.ramp(now - stoodAt)', body)
        self.assertNotIn('liftedAt', page)

    def test_the_page_asks_the_chain_nothing_of_its_own(self):
        page = source(PAGE)

        # The words and the fraction are one reading taken by the caller. A page that asked again
        # could show a bar a step away from the sentence over it.
        self.assertIn('Optional<PackChain.CompilingState> state', page)
        self.assertNotIn('compilingState()', page)
        self.assertNotIn('compilingWords()', page)

    def test_the_page_is_opaque_and_full_screen(self):
        page = source(PAGE)
        body = body_of(page, 'private static void page(GuiGraphicsExtractor graphics, float alpha, long now)')

        # Opaque rather than a veil: what is under the page is the frame from before the pack was
        # replaced, and letting it show through would show the wrong world rather than wait.
        self.assertIn('graphics.fill(RenderPipelines.GUI, 0, 0, width, height,', body)
        self.assertIn('ARGB.colorFromFloat(alpha, 0.0F, 0.0F, 0.0F));', body)


class CompileCardContract(unittest.TestCase):
    def extract_body(self):
        return body_of(source(CARD), 'public static void extract(GuiGraphicsExtractor graphics) {')

    def test_the_corner_is_drawn_under_the_page(self):
        body = self.extract_body()

        # Drawn second so that the corner is already standing as the page lifts, which is what
        # makes the handoff one mark moving rather than two marks taking turns.
        self.assertLess(body.index('corner(graphics, minecraft, state, warmedFor);'),
                        body.index('LoadPage.extract(graphics, state);'))

    def test_the_page_is_not_gated_behind_the_corners_own_silence(self):
        body = self.extract_body()
        guard = 'if (!(state.isEmpty() && warmedFor < 0L)) {'

        # The two surfaces answer different questions. The corner goes quiet once the workers are
        # done and it has nothing left to say; the page stands while the level is not being drawn,
        # and the workers finishing their translation does not decide when the world is handed back.
        # Sharing the corner's answer leaves the held world covered by nothing on the frames where
        # the two disagree, which is the blank screen the page exists to remove.
        self.assertIn(guard, body)
        self.assertNotIn('if (state.isEmpty() && warmedFor < 0L) {', body)
        self.assertLess(body.index(guard), body.index('corner(graphics, minecraft, state, warmedFor);'))
        closing = body.index('\n\t\t}\n', body.index(guard))
        self.assertLess(closing, body.index('LoadPage.extract(graphics, state);'),
                        "the page call must sit outside the corner's guard, not inside it")

    def test_the_bows_that_silence_the_corner_silence_the_page(self):
        body = self.extract_body()

        # One extraction point for both surfaces, so F1, vanilla's terrain loading screen and F3
        # cannot silence one and leave the other over the debug block.
        self.assertLess(body.index('minecraft.gui.hud.isHidden()'), body.index('LoadPage.extract'))
        self.assertLess(body.index('LevelLoadingScreen'), body.index('LoadPage.extract'))
        self.assertLess(body.index('showDebugScreen()'), body.index('LoadPage.extract'))
        self.assertEqual(body.count('LoadPage.extract('), 1)

    def test_the_mark_and_its_clock_are_one_shared_thing(self):
        card = source(CARD)
        page = source(PAGE)

        # The page carries the same mark at the same rhythm at the same ramp, so it reads as one
        # thing crossing the screen instead of two indicators with their own clocks.
        self.assertIn('static float pulse(long now)', card)
        self.assertIn('static float ramp(long sinceMillis)', card)
        self.assertIn('static void icon(GuiGraphicsExtractor graphics, int x, int y, float alpha)', card)
        self.assertIn('static final int ICON_EDGE = 16;', card)
        self.assertIn('alpha * CompileCard.pulse(now)', page)
        self.assertIn('CompileCard.icon(graphics, centreX - CompileCard.ICON_EDGE / 2,', page)
        self.assertIn('CompileCard.ramp(now - stoodAt)', page)
        self.assertIn('CompileCard.ramp(now - heldAt)', page)
        self.assertNotIn('Math.cos', page, "the pulse is the card's, not a second copy of it")


class CompilingCountContract(unittest.TestCase):
    def state_body(self):
        chain = source(CHAIN)
        return body_of(chain, 'public static Optional<CompilingState> compilingState()'
                       ).split('public static Optional<Component> compilingWords()', 1)[0]

    def test_the_words_and_the_bar_are_one_reading_of_one_count(self):
        chain = source(CHAIN)
        state = self.state_body()

        # One place computes the count and one place formats it, so the sentence and the bar under
        # it cannot be a program apart on the frame where a worker finishes.
        self.assertIn('public record CompilingState(Component words, float progress) {', chain)
        self.assertEqual(state.count('warmup.total()'), 1)
        self.assertEqual(state.count('warmup.walked()'), 1)
        self.assertEqual(state.count('Math.min('), 1)
        self.assertNotIn('compilingLabel', chain)

    def test_the_f3_line_reads_the_same_sentence_the_corner_does(self):
        self.assertIn('return compilingState().map(CompilingState::words);', source(CHAIN))

    def test_the_fraction_is_not_known_before_the_tasks_have_a_plate(self):
        state = self.state_body()

        # Bare words and no bar, rather than a "0 of 0" that reads as stuck.
        self.assertIn('float progress = -1.0F;', state)
        self.assertIn('if (total > 0) {', state)


if __name__ == '__main__':
    unittest.main()
