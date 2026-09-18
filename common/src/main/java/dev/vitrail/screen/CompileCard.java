package dev.vitrail.screen;

import dev.vitrail.render.PackChain;
import dev.vitrail.ScreenText;
import dev.vitrail.Vitrail;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.util.Optional;

/**
 * The corner of the HUD that says the pack is compiling, and then that it has, with the held
 * world's {@link LoadPage} over it while there is no world yet.
 * <p>
 * Interface and not rendering: it asks {@link PackChain} what the corner is to say, how long ago
 * the last family finished compiling and which load the chain came from, and draws off the
 * answers and its own clock. Nothing here decides anything about the chain, which is why it sits
 * with the screens rather than with the frame.
 * <p>
 * The mark, its pulse and the ramp every appearance eases in over are shared with the page rather
 * than repeated there, so that the one mark does not change rhythm or colour as it moves between
 * the middle of the screen and the corner.
 */
public final class CompileCard {

	/** The mod's own mark, pulsing in a corner while the pack compiles. */
	private static final Identifier COMPILE_ICON =
			Identifier.fromNamespaceAndPath(Vitrail.MOD_ID, "textures/gui/compiling.png");

	/** One pulse per second: full, down to half opacity, and back. */
	private static final long PULSE_MILLIS = 1000L;

	static final int ICON_EDGE = 16;
	private static final int ICON_TEXTURE = 32;
	private static final int ICON_MARGIN = 3;

	/** How long the closing sentence stands beside the mark, then the fade that erases both. */
	private static final long DONE_MILLIS = 2500L;
	private static final long FADE_MILLIS = 800L;

	/** Every appearance eases in over this, the way the whole corner eases out at the end. */
	private static final long RAMP_MILLIS = 300L;

	/**
	 * The floor under which nothing is drawn at all: the font renderer keeps the old convention
	 * of reading a near-nought alpha as fully opaque, so a fade has to end by not drawing
	 * rather than by popping back to full. The page fades on the same floor.
	 */
	static final float FAINT = 0.05F;

	/**
	 * When the mark first showed for the load it shows for, the fade-in's zero. Render thread
	 * only, and kept per load rather than per session, so a reload eases in afresh.
	 */
	private static long shownAt;
	private static int shownFor;

	private CompileCard() {
	}

	/**
	 * Draws the corner and then the held world's page over it.
	 * <p>
	 * Reached from the HUD's own extraction, after every vanilla layer, and quiet everywhere
	 * else: no chain, a chain that can never draw, the show over, the terrain loading screen
	 * (where vanilla extracts no HUD at all and the tail of the extraction still runs), and the
	 * F3 screen, whose first lines sit exactly where the mark does. Under F3 the compiling
	 * sentence rides {@link VitrailDebugEntry} instead, so neither the corner nor the page
	 * fights vanilla's debug block, and a held world under F3 is vanilla's own black with the
	 * debug block's word on it. F3 closed, both are unchanged.
	 */
	public static void extract(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.hud.isHidden() || minecraft.gui.screen() instanceof LevelLoadingScreen) {
			return;
		}

		// Under F3 the mark always bows out: the corner is vanilla's debug block, and the
		// compiling words live on the Vitrail F3 line. Keeping the overlay up while the
		// world is held back used to fight that block for the same pixels.
		if (minecraft.gui.hud.getDebugOverlay().showDebugScreen()) {
			return;
		}

		// In flight covers both moments of a load, the held world and the background compiles;
		// a chain that is neither in flight nor drawable was refused and shows nothing.
		// Both answers are empty for no chain, a stopped one and one that never runs, so the
		// corner stays quiet there without this class knowing why. The state is taken once and
		// says by itself whether the pack still compiles: asked twice, the worker could finish
		// between the two answers and the mark would pulse beside nothing.
		Optional<PackChain.CompilingState> state = PackChain.compilingState();
		long warmedFor = PackChain.warmedForMillis();

		// The two surfaces are gated apart even though they are drawn one over the other, because
		// they answer different questions. The corner goes quiet the moment the workers are done and
		// it has nothing left to say; the page stands for as long as the level is not being drawn,
		// and the workers finishing that translation does not decide when the world is handed back.
		// Gating the page behind the corner's answer leaves the held world covered by nothing on the
		// frames where the two disagree, which is the blank screen the page exists to remove.
		if (!(state.isEmpty() && warmedFor < 0L)) {
			corner(graphics, minecraft, state, warmedFor);
		}

		// Drawn second and so over the corner: while the world is held the page is the only thing
		// on the screen that should be read, and when the world comes back the corner is already
		// standing underneath the page as it lifts, which is what makes the handoff one mark
		// moving rather than two marks taking turns.
		LoadPage.extract(graphics, state);
	}

	/**
	 * The corner mark and its sentence: pulsing next to "Compiling shaders..." through the held
	 * world and the background compiles alike, the walked-out-of-total count riding along once the
	 * compile tasks have a plate, then standing still next to "Shaders compiled!" once the workers
	 * finish, and the whole corner fading away. The mark's first appearance eases in and the end
	 * eases out, a corner re-shown after F3 or F1 coming back plain; the closing show is timed from
	 * the last family finishing its compile, so a corner hidden long enough behind those misses the
	 * show rather than replaying it stale.
	 */
	private static void corner(GuiGraphicsExtractor graphics, Minecraft minecraft,
			Optional<PackChain.CompilingState> state, long warmedFor) {
		long now = Util.getMillis();
		int load = PackChain.loadNumber();
		if (shownFor != load) {
			shownFor = load;
			shownAt = now;
		}

		float ease = ramp(now - shownAt);
		Font font = minecraft.font;
		int textX = ICON_MARGIN + ICON_EDGE + 4;
		int textY = ICON_MARGIN + (ICON_EDGE - font.lineHeight) / 2 + 1;

		if (state.isPresent()) {
			icon(graphics, ICON_MARGIN, ICON_MARGIN, ease * pulse(now));
			word(graphics, font, state.get().words(), textX, textY, ease);

			return;
		}

		if (warmedFor >= DONE_MILLIS + FADE_MILLIS) {
			return;
		}

		// The workers just finished: the mark stands still and the sentence lands with it on
		// the spot, no transition, then the whole corner eases out together. The one place the
		// ramp deliberately does not apply: the switch of words IS the news.
		float out = warmedFor <= DONE_MILLIS ? 1.0F
				: 1.0F - (warmedFor - DONE_MILLIS) / (float) FADE_MILLIS;
		icon(graphics, ICON_MARGIN, ICON_MARGIN, ease * out);
		word(graphics, font, Component.translatable(ScreenText.COMPILED), textX, textY, out);
	}

	/** The mark's brightness this frame, which the corner and the page both pulse by. */
	static float pulse(long now) {
		float phase = (now % PULSE_MILLIS) / (float) PULSE_MILLIS;

		return 0.75F + 0.25F * (float) Math.cos(2.0 * Math.PI * phase);
	}

	/** How far into an appearance a thing is, nought to one over {@link #RAMP_MILLIS}. */
	static float ramp(long sinceMillis) {
		return Math.min(1.0F, sinceMillis / (float) RAMP_MILLIS);
	}

	/** The mark at a place, for whichever of the two surfaces is carrying it this frame. */
	static void icon(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
		if (alpha < FAINT) {
			return;
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, COMPILE_ICON, x, y,
				0.0F, 0.0F, ICON_EDGE, ICON_EDGE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE,
				ICON_TEXTURE, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
	}

	private static void word(GuiGraphicsExtractor graphics, Font font, Component words, int x,
			int y, float alpha) {
		if (alpha < FAINT) {
			return;
		}

		graphics.text(font, words, x, y, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F), true);
	}
}
