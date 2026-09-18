package dev.vitrail.screen;

import dev.vitrail.ScreenText;
import dev.vitrail.render.PackChain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.util.Optional;

/**
 * The page the held world wears while a pack compiles, and the fade that hands the world back.
 * <p>
 * <b>The shape is the game's own terrain loading screen, carried over to the pixel.</b>
 * {@code LevelLoadingScreen} draws a centred sentence with a bar under it while it waits for
 * chunks, and the bar is where the numbers here come from: {@code drawProgressBar} fills a black
 * track and then a green bar over it, two hundred wide and two tall, its left edge a hundred from
 * the centre of the screen, and that class puts the bar's top at the sentence's top plus its
 * font's line height plus three, with the sentence drawn white. Every colour and every offset in
 * this file is one of that class's own literals or derived from the same font it uses rather than a
 * choice made here: {@code 0xFF000000} for the track, {@code 0xFF00FF00} for the fill,
 * {@code 0xFFFFFFFF} for the words, which are its {@code -16777216}, {@code -16711936} and
 * {@code -1}.
 * <p>
 * <b>One thing is deliberately not carried over, and it is not a shortcut.</b> That page blurs the
 * frame under it and then paints the menu's own background over the blur, and both of those need a
 * screen this is not. The world is skipped outright while a pack compiles, so there is no frame of
 * this world to blur, and the picture the buffer does hold is the one from before the pack was
 * replaced, which is the very thing a page is here to hide. The menu's dirt belongs to menus. The
 * page is flat black instead, which is not an invention either: it is the value the game's own
 * loading overlay fades in when its background is set dark,
 * {@code LoadingOverlay.LOGO_BACKGROUND_COLOR_DARK}, {@code ARGB.color(255, 0, 0, 0)}, and by the
 * accident of that it is the bar's own track colour too, so the empty half of the bar is drawn
 * into the page it already matches rather than onto it.
 * <p>
 * The page stands only for as long as the world is held, which is what {@link PackChain#warming}
 * answers, and it leaves when the world comes back. It is not the corner {@link CompileCard}'s
 * replacement: the corner still carries the words through the background compiles that follow the
 * world's return, where a page over a world being played would be in the way. This one covers the
 * wait that has nothing on screen at all.
 */
public final class LoadPage {

	/**
	 * The bar, and the sentence's distance above it. All three are {@code LevelLoadingScreen}'s
	 * own: it draws the sentence at a top, then puts the bar at that top plus its font's line
	 * height plus three. The three is what is carried here, and the line height is added from the
	 * font at the call site rather than folded into this number, because folding it in is how the
	 * gap silently becomes a whole line too tall when the font changes.
	 */
	private static final int BAR_WIDTH = 200;
	private static final int BAR_HEIGHT = 2;
	private static final int BAR_LABEL_GAP = 3;

	/** Air between the mark and the sentence, so the two read as a heading and its line. */
	private static final int MARK_GAP = 6;

	/**
	 * How far above the centre the sentence and its bar sit. Vanilla centres the pair on the chunk
	 * view it draws, and this page has no chunk view to leave room for; lifting them off the exact
	 * centre is what keeps the mark above them from looking like it is falling off the screen.
	 */
	private static final int STACK_LIFT = 6;

	/** Which load the rest of these describe, so a reload starts its page afresh. */
	private static int stateFor = Integer.MIN_VALUE;

	/** Whether a page has stood for this load at all: with none, there is nothing to lift. */
	private static boolean stood;

	/** When this load's page first stood, which the arrival into the black is timed from. */
	private static long stoodAt;

	/**
	 * The last frame the world was still held, which the departure out of it is timed from rather
	 * than the first frame that notices the world is back.
	 * <p>
	 * The difference matters because this page is not drawn at all under F3 or F1, and neither is
	 * it drawn on the frames where the corner has nothing left to say. A page that stamped the
	 * release on the first frame it was next asked would find that frame arbitrarily late, and
	 * would then paint itself opaque over a world already being played and fade from there. Timed
	 * from the last frame the world was seen held, a page that missed the release is simply
	 * already past its fade and draws nothing.
	 */
	private static long heldAt;

	/**
	 * The sentence the page last carried and the fraction it last carried, kept across the lift and
	 * reset with the rest of this state when the load changes. The compile can finish on the very
	 * frame the world comes back, and a page that emptied its bar and dropped its count on the way
	 * out would read as work undone at the one moment the page is being believed.
	 */
	private static Component lastWords = Component.translatable(ScreenText.COMPILING);
	private static float lastProgress = -1.0F;

	private LoadPage() {
	}

	/**
	 * Draws the page over the held world, and lets it go once the world is back.
	 * <p>
	 * Reached from {@link CompileCard#extract} after the corner has been drawn, so that the corner
	 * is already standing underneath when the page lifts and the words never stand twice on the
	 * screen at once.
	 *
	 * @param state what the chain is saying and how far along it is, read once by the caller
	 */
	static void extract(GuiGraphicsExtractor graphics, Optional<PackChain.CompilingState> state) {
		long now = Util.getMillis();
		int load = PackChain.loadNumber();
		if (stateFor != load) {
			stateFor = load;
			stoodAt = now;
			heldAt = now;
			stood = false;
			// Everything this page carries belongs to one load, the count included: a load that
			// inherited the last one's fraction would open under bare words with a bar already
			// full, which is the one thing the count is never allowed to say.
			lastWords = Component.translatable(ScreenText.COMPILING);
			lastProgress = -1.0F;
		}

		if (PackChain.warming()) {
			// The world is held, so this page is what the load is doing, and a pack read again
			// holds the world again after a lift had started.
			stood = true;
			heldAt = now;
			// The fraction is followed rather than pinned to its high-water mark. The total it is
			// measured against grows as each family's translation lands, so the count can step back
			// when a new family's programs arrive, and the sentence above the bar says so in the
			// same breath. Pinned, the bar would sit full while the count beside it read otherwise,
			// and it would carry a previous load's fill into a load whose count is not known yet.
			state.ifPresent(inFlight -> lastProgress = inFlight.progress());
			state.ifPresent(inFlight -> lastWords = inFlight.words());
		} else if (!stood) {
			// Nothing was ever held for this load, so there was no page and there is nothing to
			// lift. A refused pack and one compiled from a warm cache both come through here.
			return;
		}

		// Into the black over the compile card's own ramp and out of it over the same span, so the
		// page arrives at the speed the world leaves and the world at the speed the page does.
		float alpha = PackChain.warming()
				? CompileCard.ramp(now - stoodAt)
				: 1.0F - CompileCard.ramp(now - heldAt);
		if (alpha < CompileCard.FAINT) {
			return;
		}

		page(graphics, alpha, now);
	}

	private static void page(GuiGraphicsExtractor graphics, float alpha, long now) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int centreX = width / 2;
		int labelY = height / 2 - STACK_LIFT;

		// Flat, and opaque rather than a veil: what is under this page is the frame from before
		// the pack was replaced, and a page that let it show through would be showing the wrong
		// world rather than waiting for the right one.
		graphics.fill(RenderPipelines.GUI, 0, 0, width, height,
				ARGB.colorFromFloat(alpha, 0.0F, 0.0F, 0.0F));

		CompileCard.icon(graphics, centreX - CompileCard.ICON_EDGE / 2,
				labelY - MARK_GAP - CompileCard.ICON_EDGE, alpha * CompileCard.pulse(now));
		graphics.centeredText(font, lastWords, centreX, labelY,
				ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
		bar(graphics, centreX - BAR_WIDTH / 2, labelY + font.lineHeight + BAR_LABEL_GAP, alpha);
	}

	/**
	 * The track and the fill, in that order, which is {@code drawProgressBar}'s own and the one
	 * thing about it that has to stay: the fill is drawn over the track rather than beside it.
	 * <p>
	 * A count the chain has not given yet leaves the track alone and fills nothing, rather than
	 * drawing a bar at nought that reads as a compile which has not started. Vanilla does the same
	 * with no progress to show, and on this page the track is the page's own colour, so nothing is
	 * drawn and nothing is missing.
	 */
	private static void bar(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
		if (lastProgress < 0.0F) {
			return;
		}

		graphics.fill(RenderPipelines.GUI, x, y, x + BAR_WIDTH, y + BAR_HEIGHT,
				ARGB.colorFromFloat(alpha, 0.0F, 0.0F, 0.0F));
		graphics.fill(RenderPipelines.GUI, x, y, x + Math.round(lastProgress * BAR_WIDTH),
				y + BAR_HEIGHT, ARGB.colorFromFloat(alpha, 0.0F, 1.0F, 0.0F));
	}
}
